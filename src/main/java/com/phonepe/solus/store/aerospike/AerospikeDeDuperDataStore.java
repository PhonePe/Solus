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

package com.phonepe.solus.store.aerospike;

import com.aerospike.client.Bin;
import com.aerospike.client.IAerospikeClient;
import com.aerospike.client.Key;
import com.aerospike.client.Value;
import com.aerospike.client.Record;
import com.aerospike.client.BatchRecord;
import com.aerospike.client.BatchRead;
import com.aerospike.client.BatchWrite;
import com.aerospike.client.ResultCode;
import com.aerospike.client.Operation;
import com.aerospike.client.policy.RecordExistsAction;
import com.aerospike.client.policy.WritePolicy;
import com.github.rholder.retry.RetryException;
import com.phonepe.solus.DeDuper;
import com.phonepe.solus.config.DeDuperLevel;
import com.phonepe.solus.exception.ErrorCode;
import com.phonepe.solus.exception.SolusException;
import com.phonepe.solus.filter.impl.EntityWithBitPositions;
import com.phonepe.solus.store.IDeDuperDataStore;
import com.phonepe.solus.util.AerospikeUtils;
import com.phonepe.solus.util.ErrorMessages;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Date;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * The latest binName structure is shortened by eliminating few useless characters in the
 * previous version due to the limitation of binName max length = 14 chars
 * Version 2: {farmId}#b{bit_position}
 * Version 1: {farmId}##b_{bitPosition}_ttl
 * Version 0: b_{bitPosition}_ttl
 * <p>
 * CRUD operations flow:
 * Writes: In V2 format bins
 * Reads: In V2 format bins, if not present fallback to V1 followed by V0
 */
@AllArgsConstructor
public class AerospikeDeDuperDataStore<T> implements IDeDuperDataStore<T> {
    private static final int BIT_INFO_TTL = 1000000;
    private static final String BIT_TTL_BIN_NAME_FORMAT = "%s_%s_%s";
    private static final String BIT_TTL_BIN_NAME_FORMAT_V2 = "%s%s";
    private static final String BIT_TTL_BIN_NAME_PREFIX = "b";
    private static final String BIT_TTL_BIN_NAME_SUFFIX = "ttl";
    private static final String AEROSPIKE_KEY_FORMAT = "%d|%s|%s";
    private static final String DC_LEVEL_SET_FORMAT = "%s_%s";

    private final String clientId;
    private final String farm;
    private final IAerospikeClient aerospikeClient;
    private final String namespace;
    private final String setName;

    @Override
    public void update(final String deDuperName,
                       final long shardId,
                       final DeDuperLevel level,
                       final EntityWithBitPositions<T> entityWithBitPositions,
                       final long ttl) {
        try {
            final WritePolicy writePolicy = new WritePolicy(aerospikeClient.getWritePolicyDefault());
            writePolicy.recordExistsAction = RecordExistsAction.UPDATE;
            writePolicy.expiration = BIT_INFO_TTL;
            writePolicy.sendKey = true;
            // alter operations for all bits with new ttl
            final Long expiryTime = new Date().getTime() + ttl;
            AerospikeUtils.retryer.call(() -> {
                aerospikeClient.put(
                        writePolicy,
                        new Key(namespace, getSetName(level), buildDeDuperKey(deDuperName, shardId)),
                        fetchTTLBinsV2(Collections.singleton(farm), entityWithBitPositions.getBitPositions()).stream()
                                .map(binName -> new Bin(binName, Value.get(expiryTime)))
                                .toArray(Bin[]::new)
                );
                return null;
            });
        } catch (ExecutionException e) {
            throw SolusException.propagate(ErrorMessages.UPDATE_BIN_ERROR, e, ErrorCode.AEROSPIKE_ERROR);
        } catch (RetryException e) {
            throw SolusException.propagate(ErrorMessages.UPDATE_BIN_ERROR, e.getCause(), ErrorCode.AEROSPIKE_ERROR);
        }
    }

    @Override
    public void batchUpdate(final String deDuperName,
                            final DeDuperLevel level,
                            final Map<Long, List<EntityWithBitPositions<T>>> shardGroupedEntities,
                            final long ttl) {
        try {
            // alter operations for all bits with new ttl
            final Long expiryTime = new Date().getTime() + ttl;
            final List<BatchRecord> batchWrites = buildBatchWrites(deDuperName, level, shardGroupedEntities, expiryTime);
            AerospikeUtils.retryer.call(() ->
                    aerospikeClient.operate(aerospikeClient.getBatchPolicyDefault(), batchWrites));
        } catch (ExecutionException e) {
            throw SolusException.propagate(ErrorMessages.UPDATE_BIN_ERROR, e, ErrorCode.AEROSPIKE_ERROR);
        } catch (RetryException e) {
            throw SolusException.propagate(ErrorMessages.UPDATE_BIN_ERROR, e.getCause(), ErrorCode.AEROSPIKE_ERROR);
        }
    }

    @Override
    public int getSetBitsCount(final DeDuper deDuper,
                               final long shardId,
                               final EntityWithBitPositions<T> entityWithBitPositions) {
        try {
            final Set<String> ttlBins = fetchTTLBinsV2(deDuper.getFarms(), entityWithBitPositions.getBitPositions());
            // For backward compatibility
            ttlBins.addAll(fetchTTLBins(deDuper.getFarms(), entityWithBitPositions.getBitPositions()));
            ttlBins.addAll(entityWithBitPositions.getBitPositions().stream()
                    .map(this::getTTLBinSuffix)
                    .collect(Collectors.toSet()));
            final Record asRecord = (Record) AerospikeUtils.retryer.call(() ->
                    aerospikeClient.get(
                            aerospikeClient.getReadPolicyDefault(),
                            new Key(namespace,
                                    getSetName(deDuper.getDeDuperConfig().getDeDuperLevel()),
                                    buildDeDuperKey(deDuper.getName(), shardId)),
                            ttlBins.toArray(new String[0])
                    )
            );
            if (Objects.isNull(asRecord) || Objects.isNull(asRecord.bins)) {
                return 0;
            }
            final long currentTime = new Date().getTime();
            return (int) entityWithBitPositions.getBitPositions().stream()
                    .map(bitPosition -> deDuper.getFarms().stream()
                            .anyMatch(requestedFarm -> isBitSet(asRecord, bitPosition, requestedFarm, currentTime)))
                    .filter(Boolean::booleanValue)
                    .count();
        } catch (ExecutionException e) {
            throw SolusException.propagate(ErrorMessages.FETCH_RECORD_WITH_BINS_ERROR, e, ErrorCode.AEROSPIKE_ERROR);
        } catch (RetryException e) {
            throw SolusException.propagate(ErrorMessages.FETCH_RECORD_WITH_BINS_ERROR, e.getCause(), ErrorCode.AEROSPIKE_ERROR);
        }
    }

    @Override
    public Map<T, Boolean> batchGetEntitiesSetBitsCounts(final DeDuper deDuper,
                                                         final Map<Long, List<EntityWithBitPositions<T>>> shardGroupedEntities) {
        final long currentTime = new Date().getTime();
        try {
            final List<BatchRecord> batchReads = buildBatchReads(deDuper, shardGroupedEntities);
            AerospikeUtils.retryer.call(() ->
                    aerospikeClient.operate(aerospikeClient.getBatchPolicyDefault(), batchReads));
            checkIfBatchOpIsSuccessful(batchReads);

            final Map<String, Record> asRecordsMap = batchReads.stream()
                    .filter(request -> Objects.nonNull(request.record))
                    .collect(Collectors.toMap(
                            request -> request.key.userKey.toString(),
                            request -> request.record,
                            (x1, x2) -> x1)
                    );
            return shardGroupedEntities.entrySet().stream()
                    .flatMap(shardEntitiesEntry -> {
                        final String rootKey = buildDeDuperKey(deDuper.getName(), shardEntitiesEntry.getKey());
                        final Record asRecord = asRecordsMap.get(rootKey);
                        if (Objects.nonNull(asRecord)) {
                            // If shard exists in aerospike, then check for all the hash bits, set the result as true
                            // if all the hash bits are not set for the entity
                            return shardEntitiesEntry.getValue()
                                    .stream()
                                    .map(entityWithBitPositions ->
                                            Pair.of(
                                                    entityWithBitPositions.getEntity(),
                                                    !areAllBitsSet(asRecord, entityWithBitPositions.getBitPositions(),
                                                            deDuper.getFarms(), currentTime)
                                            )
                                    );
                        }

                        // If shard does not exist then set the result as true for all the entities belonging to that shard
                        return shardEntitiesEntry.getValue()
                                .stream()
                                .map(entityWithBitPositions -> Pair.of(entityWithBitPositions.getEntity(), true));
                    })
                    .collect(Collectors.toMap(Pair::getKey, Pair::getValue, (x1, x2) -> x1));
        } catch (ExecutionException e) {
            throw SolusException.propagate(ErrorMessages.FETCH_RECORD_ERROR, e, ErrorCode.AEROSPIKE_ERROR);
        } catch (RetryException e) {
            throw SolusException.propagate(ErrorMessages.FETCH_RECORD_ERROR, e.getCause(), ErrorCode.AEROSPIKE_ERROR);
        }
    }

    private void checkIfBatchOpIsSuccessful(final List<BatchRecord> batchReads) {
        if (batchReads.stream().anyMatch(request ->
                request.resultCode != ResultCode.OK && request.resultCode != ResultCode.KEY_NOT_FOUND_ERROR)) {
            throw SolusException.builder()
                    .errorCode(ErrorCode.AEROSPIKE_ERROR)
                    .build();
        }
    }

    private List<BatchRecord> buildBatchReads(final DeDuper deDuper,
                                              final Map<Long, List<EntityWithBitPositions<T>>> shardGroupedEntities) {
        return shardGroupedEntities.entrySet()
                .stream()
                .map(shardEntitiesEntry -> {
                    final String[] allBins = shardEntitiesEntry.getValue().stream()
                            .flatMap(entityWithBitPositions -> {
                                        final Set<String> ttlBins = fetchTTLBinsV2(
                                                deDuper.getFarms(),
                                                entityWithBitPositions.getBitPositions());
                                        // For backward compatibility
                                        ttlBins.addAll(fetchTTLBins(
                                                deDuper.getFarms(),
                                                entityWithBitPositions.getBitPositions()));
                                        ttlBins.addAll(entityWithBitPositions.getBitPositions().stream()
                                                .map(this::getTTLBinSuffix)
                                                .collect(Collectors.toSet()));
                                        return ttlBins.stream();
                                    }
                            )
                            .toArray(String[]::new);
                    return Pair.of(
                            new Key(namespace, getSetName(deDuper.getDeDuperConfig().getDeDuperLevel()),
                                    buildDeDuperKey(deDuper.getName(), shardEntitiesEntry.getKey())),
                            allBins);
                })
                .map(keyBinsPair -> (BatchRecord) new BatchRead(keyBinsPair.getKey(), keyBinsPair.getValue()))
                .toList();
    }

    private List<BatchRecord> buildBatchWrites(final String deDuperName,
                                               final DeDuperLevel level,
                                               final Map<Long, List<EntityWithBitPositions<T>>> shardGroupedEntities,
                                               final Long expiryTime) {
        return shardGroupedEntities.entrySet()
                .stream()
                .map(shardEntitiesEntry -> {
                    final Set<String> allBins = shardEntitiesEntry.getValue().stream()
                            .flatMap(entityWithBitPositions -> fetchTTLBinsV2(
                                            Collections.singleton(farm),
                                            entityWithBitPositions.getBitPositions()
                                    ).stream()
                            )
                            .collect(Collectors.toSet());
                    final Operation[] operations = allBins.stream()
                            .map(binName -> Operation.add(new Bin(binName, Value.get(expiryTime))))
                            .toArray(Operation[]::new);
                    return Pair.of(
                            new Key(namespace, getSetName(level), buildDeDuperKey(deDuperName, shardEntitiesEntry.getKey())),
                            operations
                    );
                })
                .map(keyBinsPair -> (BatchRecord) new BatchWrite(keyBinsPair.getKey(), keyBinsPair.getValue()))
                .toList();
    }

    private Set<String> fetchTTLBins(final Set<String> allFarms, final List<Integer> bitPositions) {
        return allFarms.stream()
                .flatMap(requestedFarm -> bitPositions.stream().map(bitPosition -> getTTLBin(bitPosition, requestedFarm)))
                .collect(Collectors.toSet());
    }

    private boolean areAllBitsSet(final Record asRecord,
                                  final List<Integer> bitPositions,
                                  final Set<String> allFarms,
                                  final long referenceTime) {
        return bitPositions.stream()
                .allMatch(bitPosition -> allFarms.stream()
                        .anyMatch(requestedFarm -> isBitSet(asRecord, bitPosition, requestedFarm, referenceTime)));
    }

    private boolean isBitSet(final Record asRecord,
                             final Integer bitPosition,
                             final String requestedFarm,
                             final long currentTime) {
        Object ttl = asRecord.bins.get(getTTLBinV2(bitPosition, requestedFarm));
        // For backward compatibility
        if (Objects.isNull(ttl)) {
            ttl = asRecord.bins.getOrDefault(getTTLBin(bitPosition, requestedFarm),
                    asRecord.bins.get(getTTLBinSuffix(bitPosition)));
        }
        return Objects.nonNull(ttl) && (Long) ttl >= currentTime;
    }

    private String buildDeDuperKey(final String deDuperName, final long shardId) {
        return String.format(AEROSPIKE_KEY_FORMAT, shardId, clientId, deDuperName);
    }

    private String getTTLBin(final Integer bitPosition, final String requestedFarm) {
        return AerospikeUtils.getBin(getTTLBinSuffix(bitPosition), requestedFarm);
    }

    private String getTTLBinSuffix(final Integer bitPosition) {
        return String.format(BIT_TTL_BIN_NAME_FORMAT,
                BIT_TTL_BIN_NAME_PREFIX, bitPosition.toString(), BIT_TTL_BIN_NAME_SUFFIX);
    }

    private String getSetName(final DeDuperLevel level) {
        return level.accept(new DeDuperLevel.Visitor<>() {
            @Override
            public String visitDC() {
                return String.format(DC_LEVEL_SET_FORMAT, farm, setName);
            }

            @Override
            public String visitXDC() {
                return setName;
            }
        });
    }

    private Set<String> fetchTTLBinsV2(final Set<String> allFarms, final List<Integer> bitPositions) {
        return allFarms.stream()
                .flatMap(requestedFarm -> bitPositions.stream().map(bitPosition -> getTTLBinV2(bitPosition, requestedFarm)))
                .collect(Collectors.toSet());
    }

    private String getTTLBinV2(final Integer bitPosition, final String requestedFarm) {
        return AerospikeUtils.getBinV2(getTTLBinSuffixV2(bitPosition), requestedFarm);
    }

    private String getTTLBinSuffixV2(final Integer bitPosition) {
        return String.format(BIT_TTL_BIN_NAME_FORMAT_V2, BIT_TTL_BIN_NAME_PREFIX, bitPosition.toString());
    }
}
