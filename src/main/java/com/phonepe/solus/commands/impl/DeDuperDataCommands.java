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

package com.phonepe.solus.commands.impl;

import com.phonepe.solus.DeDuper;
import com.phonepe.solus.commands.IDeDuperDataCommands;
import com.phonepe.solus.filter.impl.EntityWithBitPositions;
import com.phonepe.solus.shard.ShardCalculator;
import com.phonepe.solus.store.IDeDuperDataStore;
import com.phonepe.solus.store.aerospike.AerospikeDeDuperDataStore;
import com.phonepe.solus.store.context.StorageContext;
import com.phonepe.solus.store.context.impl.AerospikeStorageContext;
import com.phonepe.solus.store.context.impl.HBaseStorageContext;
import com.phonepe.solus.store.hbase.HBaseDeDuperDataStore;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class DeDuperDataCommands<T> implements IDeDuperDataCommands<T> {
    private final IDeDuperDataStore<T> deDuperDataStore;
    private final ShardCalculator<T> shardCalculator;

    public DeDuperDataCommands(final StorageContext storageContext,
                               final String clientId) {
        this.shardCalculator = new ShardCalculator<>();
        this.deDuperDataStore = buildDeDuperDataStore(storageContext, clientId);
    }

    @Override
    public boolean checkAbsence(final DeDuper deDuper, final T entity) {
        final long shardId = shardCalculator.getShardId(entity, deDuper.getDeDuperConfig().getNoOfShards());
        final EntityWithBitPositions<T> entityWithBitPositions = new EntityWithBitPositions<>(
                entity, deDuper.getDeDuperConfig().getNoOfHashFunctions(), deDuper.getDeDuperConfig().getBitsPerShard());
        final int count = deDuperDataStore.getSetBitsCount(deDuper, shardId, entityWithBitPositions);
        return count < deDuper.getDeDuperConfig().getNoOfHashFunctions();
    }

    @Override
    public Map<T, Boolean> checkAbsence(final DeDuper deDuper, final Set<T> entities) {
        final Map<Long, List<EntityWithBitPositions<T>>> shardGroupedEntitiesMap = groupEntitiesOnShard(deDuper, entities);
        return deDuperDataStore.batchGetEntitiesSetBitsCounts(deDuper, shardGroupedEntitiesMap);
    }

    @Override
    public void add(final DeDuper deDuper, final T entity, final long ttl) {
        final long shardId = shardCalculator.getShardId(entity, deDuper.getDeDuperConfig().getNoOfShards());
        final EntityWithBitPositions<T> entityWithBitPositions = new EntityWithBitPositions<>(entity,
                deDuper.getDeDuperConfig().getNoOfHashFunctions(), deDuper.getDeDuperConfig().getBitsPerShard());
        deDuperDataStore.update(
                deDuper.getName(), shardId, deDuper.getDeDuperConfig().getDeDuperLevel(), entityWithBitPositions, ttl);
    }

    @Override
    public void add(final DeDuper deDuper, final Set<T> entities, final long ttl) {
        final Map<Long, List<EntityWithBitPositions<T>>> shardGroupedEntities = groupEntitiesOnShard(deDuper, entities);
        deDuperDataStore.batchUpdate(
                deDuper.getName(), deDuper.getDeDuperConfig().getDeDuperLevel(), shardGroupedEntities, ttl);
    }

    @Override
    public boolean addIfAbsent(final DeDuper deDuper, final T entity, final long ttl) {
        if (checkAbsence(deDuper, entity)) {
            add(deDuper, entity, ttl);
            return true;
        }
        return false;
    }

    @Override
    public Map<T, Boolean> addIfAbsent(final DeDuper deDuper,
                                       final Set<T> entities,
                                       final long ttl) {
        final Map<T, Boolean> absenseMap = checkAbsence(deDuper, entities);
        final Set<T> absentEntities = absenseMap.entrySet()
                .stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        add(deDuper, absentEntities, ttl);
        return absenseMap;
    }

    private Map<Long, List<EntityWithBitPositions<T>>> groupEntitiesOnShard(final DeDuper deDuper, final Set<T> entities) {
        return entities.stream()
                .map(entity ->
                        new EntityWithBitPositions<>(
                                entity, deDuper.getDeDuperConfig().getNoOfHashFunctions(),
                                deDuper.getDeDuperConfig().getBitsPerShard()
                        )
                ).collect(Collectors.groupingBy(
                        entityWithBitPositions -> shardCalculator.getShardId(
                                entityWithBitPositions.getEntity(), deDuper.getDeDuperConfig().getNoOfShards())
                ));
    }

    private IDeDuperDataStore<T> buildDeDuperDataStore(final StorageContext storageContext, final String clientId) {
        return storageContext.accept(new StorageContext.Visitor<>() {
            @Override
            public IDeDuperDataStore<T> visit(AerospikeStorageContext storageContext) {
                return new AerospikeDeDuperDataStore<>(
                        clientId,
                        storageContext.getFarm(),
                        storageContext.getAerospikeClient(),
                        storageContext.getNamespace(),
                        storageContext.getSetName()
                );
            }

            @Override
            public IDeDuperDataStore<T> visit(HBaseStorageContext storageContext) {
                return new HBaseDeDuperDataStore<>(
                        clientId,
                        storageContext.getConnection(),
                        storageContext.getTableName(),
                        storageContext.getFarm()
                );
            }
        });
    }
}
