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

package com.phonepe.solus.store;

import com.phonepe.solus.DeDuper;
import com.phonepe.solus.config.DeDuperLevel;
import com.phonepe.solus.filter.impl.EntityWithBitPositions;

import java.util.List;
import java.util.Map;

public interface IDeDuperDataStore<T> {

    /**
     * Updates bit positions for a single entity in the underlying store.
     *
     * @param deDuperName            name of the deduper
     * @param shardId                shard identifier computed for the entity
     * @param level                  configured deduper level
     * @param entityWithBitPositions entity and resolved bit positions
     * @param ttl                    ttl for persisted data
     */
    void update(String deDuperName, long shardId, DeDuperLevel level, EntityWithBitPositions<T> entityWithBitPositions, long ttl);

    /**
     * Updates bit positions for a batch of entities grouped by shard.
     *
     * @param deDuperName          name of the deduper
     * @param level                configured deduper level
     * @param shardGroupedEntities entities grouped by shard id
     * @param ttl                  ttl for persisted data
     */
    void batchUpdate(String deDuperName, DeDuperLevel level, Map<Long, List<EntityWithBitPositions<T>>> shardGroupedEntities, long ttl);

    /**
     * Returns the number of already-set bits for an entity.
     *
     * @param deDuper                deduper configuration object
     * @param shardId                shard identifier computed for the entity
     * @param entityWithBitPositions entity and resolved bit positions
     * @return count of set bits found for the entity
     */
    int getSetBitsCount(DeDuper deDuper, long shardId, EntityWithBitPositions<T> entityWithBitPositions);

    /**
     * Returns dedupe presence result for a batch of entities grouped by shard.
     *
     * @param deDuper              deduper configuration object
     * @param shardGroupedEntities entities grouped by shard id
     * @return map of entity to dedupe presence flag
     */
    Map<T, Boolean> batchGetEntitiesSetBitsCounts(DeDuper deDuper, Map<Long, List<EntityWithBitPositions<T>>> shardGroupedEntities);
}
