/**
 * Copyright (c) 2026 Original Author(s), PhonePe India Pvt. Ltd.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.phonepe.solus.store.hbase;

import com.phonepe.solus.DeDuper;
import com.phonepe.solus.config.DeDuperLevel;
import com.phonepe.solus.exception.ErrorCode;
import com.phonepe.solus.exception.SolusException;
import com.phonepe.solus.filter.impl.EntityWithBitPositions;
import com.phonepe.solus.filter.impl.hbase.HBaseBloomFilterUtils;
import com.phonepe.solus.hbase.HBaseTableConnection;
import com.phonepe.solus.hbase.commands.HBaseBatchGetCommand;
import com.phonepe.solus.hbase.commands.HBaseBatchPutCommand;
import com.phonepe.solus.hbase.commands.HBaseGetCommand;
import com.phonepe.solus.hbase.commands.HBasePutCommand;
import com.phonepe.solus.store.IDeDuperDataStore;
import com.phonepe.solus.util.Constants;
import com.phonepe.solus.util.ErrorMessages;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.util.Bytes;

import java.io.IOException;
import java.util.stream.Collectors;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Objects;

@AllArgsConstructor
public class HBaseDeDuperDataStore<T> implements IDeDuperDataStore<T> {
    private static final String DC_LEVEL_TABLE_FORMAT = "%s_%s";
    private final String clientId;
    private final HBaseTableConnection connection;
    private final String tableName;
    private final String farm;

    @Override
    public void update(final String deDuperName,
                       final long shardId,
                       final DeDuperLevel level,
                       final EntityWithBitPositions<T> entityWithBitPositions,
                       final long ttl) {
        final Put put = new Put(HBaseBloomFilterUtils.rowKeyWithPrefix(
                Bytes.toBytes(HBaseBloomFilterUtils.getRowKey(shardId, clientId, deDuperName))));
        put.addColumn(Bytes.toBytes(Constants.HBASE_COLUMN_FAMILY_NAME), Bytes.toBytes(Constants.SHARD_ID_COL_NAME),
                Bytes.toBytes(shardId));
        entityWithBitPositions.getBitPositions()
                .forEach(bitPosition -> put.addColumn(
                        Bytes.toBytes(Constants.HBASE_COLUMN_FAMILY_NAME),
                        Bytes.toBytes(bitPosition),
                        Bytes.toBytes(true))
                );
        put.setTTL(ttl);
        try {
            HBasePutCommand.builder()
                    .hBaseConnection(connection)
                    .table(getTableName(level))
                    .put(put)
                    .build()
                    .execute();
        } catch (IOException e) {
            throw SolusException.propagate(
                    String.format(ErrorMessages.ADD_ENTITY_ERROR, entityWithBitPositions.getEntity()), e, ErrorCode.HBASE_ERROR);
        }
    }

    @Override
    public void batchUpdate(final String deDuperName,
                            final DeDuperLevel level,
                            final Map<Long, List<EntityWithBitPositions<T>>> shardGroupedEntities,
                            final long ttl) {
        try {
            HBaseBatchPutCommand.builder()
                    .connection(connection)
                    .table(getTableName(level))
                    .puts(HBaseBloomFilterUtils.createBatchPuts(shardGroupedEntities, ttl,
                            Constants.HBASE_COLUMN_FAMILY_NAME, clientId, deDuperName))
                    .build()
                    .execute();
        } catch (IOException e) {
            throw SolusException.propagate(
                    String.format(ErrorMessages.ADD_ENTITY_ERROR, getEntities(shardGroupedEntities)),
                    e, ErrorCode.HBASE_ERROR);
        }
    }

    @Override
    public int getSetBitsCount(final DeDuper deDuper,
                               final long shardId,
                               final EntityWithBitPositions<T> entityWithBitPositions) {
        final List<HBaseGetCommand.ColumnInfo> columns = entityWithBitPositions.getBitPositions()
                .stream()
                .map(bitPosition -> new HBaseGetCommand.ColumnInfo(Bytes.toBytes(Constants.HBASE_COLUMN_FAMILY_NAME),
                        Bytes.toBytes(bitPosition)))
                .collect(Collectors.toList());
        try {
            final Map<HBaseGetCommand.ColumnInfo, byte[]> columnInfoMap = HBaseGetCommand.builder()
                    .hBaseConnection(connection)
                    .table(getTableName(deDuper.getDeDuperConfig().getDeDuperLevel()))
                    .rowkey(HBaseBloomFilterUtils.rowKeyWithPrefix(
                            Bytes.toBytes(HBaseBloomFilterUtils.getRowKey(shardId, clientId, deDuper.getName()))))
                    .columnInfos(columns)
                    .build()
                    .execute();
            return (int) columnInfoMap.entrySet()
                    .stream()
                    .filter(columnInfoEntry ->
                            !Objects.isNull(columnInfoEntry.getValue())
                                    && Bytes.toBoolean(columnInfoEntry.getValue())
                    )
                    .count();
        } catch (IOException e) {
            throw SolusException.propagate(
                    String.format(ErrorMessages.CHECK_ENTITY_ABSENCE_ERROR, entityWithBitPositions.getEntity()),
                    e, ErrorCode.HBASE_ERROR
            );
        }
    }

    @Override
    public Map<T, Boolean> batchGetEntitiesSetBitsCounts(final DeDuper deDuper,
                                                         final Map<Long, List<EntityWithBitPositions<T>>> shardGroupedEntities) {
        try {
            final HBaseBatchGetCommand getCommand = new HBaseBatchGetCommand(HBaseBloomFilterUtils.createBatchGets(
                    shardGroupedEntities, Constants.HBASE_COLUMN_FAMILY_NAME, clientId, deDuper.getName()),
                    connection, getTableName(deDuper.getDeDuperConfig().getDeDuperLevel())
            );
            final Map<Long, List<Integer>> hBaseResultMap = HBaseBloomFilterUtils.getResultMap(getCommand.execute());

            return shardGroupedEntities.entrySet().stream()
                    .flatMap(shardEntitiesEntry -> {
                        final List<Integer> resultList = hBaseResultMap.get(shardEntitiesEntry.getKey());
                        if (Objects.nonNull(resultList)) {
                            // If shard exists in hbase, then check for all the hash bits, set the result as true
                            // if all the hash bits are not set for the entity
                            return shardEntitiesEntry.getValue()
                                    .stream()
                                    .map(entityWithBitPositions ->
                                            Pair.of(
                                                    entityWithBitPositions.getEntity(),
                                                    !new HashSet<>(resultList).containsAll(entityWithBitPositions.getBitPositions())
                                            )
                                    );
                        }

                        // If shard does not exist then set the result as false for all the entities belonging to that shard
                        return shardEntitiesEntry.getValue()
                                .stream()
                                .map(entityWithBitPositions -> Pair.of(entityWithBitPositions.getEntity(), true));
                    })
                    .collect(Collectors.toMap(Pair::getKey, Pair::getValue, (x1, x2) -> x1));
        } catch (IOException e) {
            throw SolusException.propagate(
                    String.format(ErrorMessages.CHECK_ENTITY_ABSENCE_ERROR, getEntities(shardGroupedEntities)),
                    e, ErrorCode.HBASE_ERROR);
        }
    }

    private String getTableName(final DeDuperLevel level) {
        return level.accept(new DeDuperLevel.Visitor<>() {
            @Override
            public String visitDC() {
                return String.format(DC_LEVEL_TABLE_FORMAT, farm, tableName);
            }

            @Override
            public String visitXDC() {
                return tableName;
            }
        });
    }

    private static <T> Set<T> getEntities(final Map<Long, List<EntityWithBitPositions<T>>> shardGroupedEntities) {
        return shardGroupedEntities.values().stream()
                .flatMap(entityWithBitPositions -> entityWithBitPositions.stream().map(EntityWithBitPositions::getEntity))
                .collect(Collectors.toSet());
    }
}
