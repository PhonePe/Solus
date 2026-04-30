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
import com.phonepe.solus.config.DeDuperConfig;
import com.phonepe.solus.config.DeDuperLevel;
import com.phonepe.solus.exception.ErrorCode;
import com.phonepe.solus.exception.SolusException;
import com.phonepe.solus.filter.impl.hbase.HBaseBloomFilterUtils;
import com.phonepe.solus.hbase.HBaseTableConnection;
import com.phonepe.solus.hbase.commands.HBaseGetCommand;
import com.phonepe.solus.hbase.commands.HBasePutCommand;
import com.phonepe.solus.hbase.commands.HBaseScanCommand;
import com.phonepe.solus.store.IDeDuperMetaStore;
import com.phonepe.solus.util.Constants;
import com.phonepe.solus.util.ErrorMessages;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.CompareOperator;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.filter.Filter;
import org.apache.hadoop.hbase.filter.SingleColumnValueFilter;
import org.apache.hadoop.hbase.util.Bytes;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class HBaseDeDuperMetaStore implements IDeDuperMetaStore {
    private static final String TABLE_NAME_FORMAT = "%s_dedupers";
    private static final String HBASE_REGISTRATION_COLUMN_FAMILY_NAME = "C";
    private static final String NAME_COL = "name";
    private static final String CREATED_TIME_COL = "ctime";
    private static final String UPDATED_TIME_COL = "utime";
    private static final String NO_OF_HASH_FUNCTIONS_COL = "hf";
    private static final String NO_OF_SHARDS_COL = "sh";
    private static final String BITS_PER_SHAR_COL = "bps";
    private static final String ACTIVE_COL = "a";
    private static final String LEVEL_COL = "level";
    private final String clientId;
    private final HBaseTableConnection connection;

    public HBaseDeDuperMetaStore(final String clientId, final HBaseTableConnection connection) {
        this.clientId = clientId;
        this.connection = connection;

        connection.ensureTableExists(getTableName(), HBASE_REGISTRATION_COLUMN_FAMILY_NAME,
                HBaseBloomFilterUtils.ONE_BYTE_HASHER.getAllPossiblePrefixes());
    }

    @Override
    public void store(final String deDuperName, final DeDuperConfig deDuperConfig) {
        final byte[] rowKey = HBaseBloomFilterUtils.rowKeyWithPrefix(Bytes.toBytes(
                HBaseBloomFilterUtils.getRegistrationRowKey(Constants.REGISTRATION_ROOT_KEY,
                        clientId, deDuperName)));
        final Put put = new Put(rowKey);
        put.addColumn(Bytes.toBytes(HBASE_REGISTRATION_COLUMN_FAMILY_NAME), Bytes.toBytes(NAME_COL),
                Bytes.toBytes(deDuperName));
        put.addColumn(Bytes.toBytes(HBASE_REGISTRATION_COLUMN_FAMILY_NAME), Bytes.toBytes(NO_OF_HASH_FUNCTIONS_COL),
                Bytes.toBytes(deDuperConfig.getNoOfHashFunctions()));
        put.addColumn(Bytes.toBytes(HBASE_REGISTRATION_COLUMN_FAMILY_NAME), Bytes.toBytes(NO_OF_SHARDS_COL),
                Bytes.toBytes(deDuperConfig.getNoOfShards()));
        put.addColumn(Bytes.toBytes(HBASE_REGISTRATION_COLUMN_FAMILY_NAME), Bytes.toBytes(BITS_PER_SHAR_COL),
                Bytes.toBytes(deDuperConfig.getBitsPerShard()));
        put.addColumn(Bytes.toBytes(HBASE_REGISTRATION_COLUMN_FAMILY_NAME), Bytes.toBytes(ACTIVE_COL),
                Bytes.toBytes(true));
        put.addColumn(Bytes.toBytes(HBASE_REGISTRATION_COLUMN_FAMILY_NAME), Bytes.toBytes(LEVEL_COL),
                Bytes.toBytes(deDuperConfig.getDeDuperLevel().getValue()));
        put.addColumn(Bytes.toBytes(HBASE_REGISTRATION_COLUMN_FAMILY_NAME), Bytes.toBytes(CREATED_TIME_COL),
                Bytes.toBytes(System.currentTimeMillis()));
        put.addColumn(Bytes.toBytes(HBASE_REGISTRATION_COLUMN_FAMILY_NAME), Bytes.toBytes(UPDATED_TIME_COL),
                Bytes.toBytes(System.currentTimeMillis()));
        try {
            HBasePutCommand.builder()
                    .hBaseConnection(connection)
                    .table(getTableName())
                    .put(put)
                    .build()
                    .execute();
        } catch (IOException e) {
            throw SolusException.propagate(
                    String.format(Constants.REGISTER_DEDUPER_ERROR, deDuperName), e, ErrorCode.HBASE_ERROR);
        }
    }

    @Override
    public void updateStatus(final String deDuperName, final boolean status) {
        final Put put = new Put(HBaseBloomFilterUtils.rowKeyWithPrefix(Bytes.toBytes(
                HBaseBloomFilterUtils.getRegistrationRowKey(Constants.REGISTRATION_ROOT_KEY, clientId, deDuperName))));
        put.addColumn(
                Bytes.toBytes(HBASE_REGISTRATION_COLUMN_FAMILY_NAME),
                Bytes.toBytes(ACTIVE_COL),
                Bytes.toBytes(status));
        put.addColumn(
                Bytes.toBytes(HBASE_REGISTRATION_COLUMN_FAMILY_NAME),
                Bytes.toBytes(UPDATED_TIME_COL),
                Bytes.toBytes(System.currentTimeMillis())
        );
        try {
            HBasePutCommand.builder()
                    .hBaseConnection(connection)
                    .table(getTableName())
                    .put(put)
                    .build()
                    .execute();
        } catch (IOException e) {
            throw SolusException.propagate(
                    String.format(Constants.DEDUPER_STATE_CHANGE_ERROR, deDuperName), e, ErrorCode.HBASE_ERROR);
        }
    }

    @Override
    public Map<String, DeDuper> getAllActive() {
        final Map<String, DeDuper> activeDeDupersMap = new ConcurrentHashMap<>();
        final Filter valueFilter = new SingleColumnValueFilter(
                Bytes.toBytes(HBASE_REGISTRATION_COLUMN_FAMILY_NAME),
                Bytes.toBytes(ACTIVE_COL),
                CompareOperator.EQUAL,
                Bytes.toBytes(true)
        );
        final Scan scan = new Scan()
                .setFilter(valueFilter)
                .addFamily(Bytes.toBytes(HBASE_REGISTRATION_COLUMN_FAMILY_NAME));
        try {
            final List<Result> results = HBaseScanCommand.builder()
                    .scan(scan)
                    .hBaseConnection(connection)
                    .table(getTableName())
                    .build()
                    .execute();
            results.forEach(result -> {
                final Map<HBaseGetCommand.ColumnInfo, byte[]> columnInfoMap =
                        Arrays.stream(result.rawCells()).map(cell ->
                                Pair.of(
                                        new HBaseGetCommand.ColumnInfo(CellUtil.cloneFamily(cell), CellUtil.cloneQualifier(cell)),
                                        CellUtil.cloneValue(cell)
                                )
                        ).collect(Collectors.toMap(Pair::getKey, Pair::getValue, (x1, x2) -> x1));
                buildDeDuper(columnInfoMap).ifPresent(deDuper -> activeDeDupersMap.put(deDuper.getName(), deDuper));
            });
        } catch (IOException e) {
            throw SolusException.propagate(
                    String.format(ErrorMessages.SCAN_ERROR, getTableName()), e, ErrorCode.HBASE_ERROR);
        }

        return activeDeDupersMap;
    }

    @Override
    public Optional<DeDuper> get(final String deDuperName) {
        final byte[] rowKey = HBaseBloomFilterUtils.rowKeyWithPrefix(Bytes.toBytes(
                HBaseBloomFilterUtils.getRegistrationRowKey(Constants.REGISTRATION_ROOT_KEY, clientId, deDuperName)));
        final List<HBaseGetCommand.ColumnInfo> columnInfos = Arrays.asList(
                new HBaseGetCommand.ColumnInfo(Bytes.toBytes(HBASE_REGISTRATION_COLUMN_FAMILY_NAME),
                        Bytes.toBytes(NAME_COL)),
                new HBaseGetCommand.ColumnInfo(Bytes.toBytes(HBASE_REGISTRATION_COLUMN_FAMILY_NAME),
                        Bytes.toBytes(NO_OF_HASH_FUNCTIONS_COL)),
                new HBaseGetCommand.ColumnInfo(Bytes.toBytes(HBASE_REGISTRATION_COLUMN_FAMILY_NAME),
                        Bytes.toBytes(NO_OF_SHARDS_COL)),
                new HBaseGetCommand.ColumnInfo(Bytes.toBytes(HBASE_REGISTRATION_COLUMN_FAMILY_NAME),
                        Bytes.toBytes(BITS_PER_SHAR_COL)),
                new HBaseGetCommand.ColumnInfo(Bytes.toBytes(HBASE_REGISTRATION_COLUMN_FAMILY_NAME),
                        Bytes.toBytes(ACTIVE_COL)),
                new HBaseGetCommand.ColumnInfo(Bytes.toBytes(HBASE_REGISTRATION_COLUMN_FAMILY_NAME),
                        Bytes.toBytes(LEVEL_COL)));
        try {
            final Map<HBaseGetCommand.ColumnInfo, byte[]> columnInfoMap = HBaseGetCommand.builder()
                    .hBaseConnection(connection)
                    .table(getTableName())
                    .rowkey(rowKey)
                    .columnInfos(columnInfos)
                    .build()
                    .execute();
            return buildDeDuper(columnInfoMap);
        } catch (IOException e) {
            throw SolusException.propagate(ErrorMessages.FETCH_ERROR, e, ErrorCode.HBASE_ERROR);
        }
    }

    private Optional<DeDuper> buildDeDuper(final Map<HBaseGetCommand.ColumnInfo, byte[]> columnInfoMap) {
        if (isDeDuperActive(columnInfoMap)) {
            final byte[] level = columnInfoMap.get(
                    new HBaseGetCommand.ColumnInfo(
                            Bytes.toBytes(HBASE_REGISTRATION_COLUMN_FAMILY_NAME),
                            Bytes.toBytes(LEVEL_COL))
            );
            return Optional.of(
                    DeDuper.builder()
                            .name(Bytes.toString(columnInfoMap.get(
                                    new HBaseGetCommand.ColumnInfo(
                                            Bytes.toBytes(HBASE_REGISTRATION_COLUMN_FAMILY_NAME),
                                            Bytes.toBytes(NAME_COL))
                            )))
                            .deDuperConfig(DeDuperConfig.builder()
                                    .noOfHashFunctions(Bytes.toInt(columnInfoMap.get(
                                            new HBaseGetCommand.ColumnInfo(Bytes.toBytes(
                                                    HBASE_REGISTRATION_COLUMN_FAMILY_NAME),
                                                    Bytes.toBytes(NO_OF_HASH_FUNCTIONS_COL))
                                    )))
                                    .noOfShards(Bytes.toLong(columnInfoMap.get(
                                            new HBaseGetCommand.ColumnInfo(
                                                    Bytes.toBytes(HBASE_REGISTRATION_COLUMN_FAMILY_NAME),
                                                    Bytes.toBytes(NO_OF_SHARDS_COL))
                                    )))
                                    .bitsPerShard(Bytes.toInt(columnInfoMap.get(
                                            new HBaseGetCommand.ColumnInfo(
                                                    Bytes.toBytes(HBASE_REGISTRATION_COLUMN_FAMILY_NAME),
                                                    Bytes.toBytes(BITS_PER_SHAR_COL))
                                    )))
                                    .deDuperLevel(Objects.nonNull(level)
                                            ? DeDuperLevel.valueOf(Bytes.toString(level))
                                            : DeDuperLevel.XDC
                                    )
                                    .build())
                            .clientId(clientId)
                            .build());
        }
        return Optional.empty();
    }

    private boolean isDeDuperActive(final Map<HBaseGetCommand.ColumnInfo, byte[]> columnInfoMap) {
        return !columnInfoMap.isEmpty()
                && Bytes.toBoolean(columnInfoMap.get(
                new HBaseGetCommand.ColumnInfo(
                        Bytes.toBytes(HBASE_REGISTRATION_COLUMN_FAMILY_NAME),
                        Bytes.toBytes(ACTIVE_COL)
                )));
    }

    private String getTableName() {
        return String.format(TABLE_NAME_FORMAT, clientId);
    }
}
