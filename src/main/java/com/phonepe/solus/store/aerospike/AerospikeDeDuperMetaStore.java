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

import com.aerospike.client.Record;
import com.aerospike.client.Key;
import com.aerospike.client.Bin;
import com.aerospike.client.AerospikeException;
import com.aerospike.client.IAerospikeClient;
import com.aerospike.client.policy.RecordExistsAction;
import com.aerospike.client.policy.WritePolicy;
import com.aerospike.client.query.IndexType;
import com.aerospike.client.query.KeyRecord;
import com.aerospike.client.query.RecordSet;
import com.aerospike.client.query.Statement;
import com.github.rholder.retry.RetryException;
import com.phonepe.solus.DeDuper;
import com.phonepe.solus.config.DeDuperConfig;
import com.phonepe.solus.config.DeDuperLevel;
import com.phonepe.solus.exception.ErrorCode;
import com.phonepe.solus.exception.SolusException;
import com.phonepe.solus.store.IDeDuperMetaStore;
import com.phonepe.solus.util.AerospikeUtils;
import com.phonepe.solus.util.Constants;
import com.phonepe.solus.util.ErrorMessages;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.Optional;
import java.util.Set;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Slf4j
public class AerospikeDeDuperMetaStore implements IDeDuperMetaStore {
    public static final String SET_FORMAT = "%s_solus_DEDUPER";
    private static final String AEROSPIKE_KEY_FORMAT = "%s|%s";
    private static final String NAME_BIN = "name";
    private static final String UPDATED_BIN = "updated";
    private static final String NO_OF_HASH_FUNCTIONS_BIN = "hashFns";
    private static final String NO_OF_SHARDS_BIN = "shards";
    private static final String BITS_PER_SHARD_BIN = "bps";
    private static final String ACTIVE_BIN = "active";
    private static final String LEVEL_BIN = "level";
    private static final int AS_FOREVER_EXPIRATION_TTL = -1;
    private static final String ACTIVE_BIN_INDEX_FORMAT = "%s_%s";
    private static final String FARM_BIN = "farm";
    private static final int NO_EXPIRATION_UPDATE_TTL = -2;

    private final IAerospikeClient aerospikeClient;
    private final String namespace;
    private final String farm;
    private final String clientId;

    public AerospikeDeDuperMetaStore(final IAerospikeClient aerospikeClient,
                                     final String namespace,
                                     final String farm,
                                     final String clientId) {
        this.aerospikeClient = aerospikeClient;
        this.namespace = namespace;
        this.farm = farm;
        this.clientId = clientId;

        createdIndexes();
    }

    @Override
    public void store(final String deDuperName, final DeDuperConfig deDuperConfig) {
        try {
            final WritePolicy writePolicy = new WritePolicy(aerospikeClient.getWritePolicyDefault());
            writePolicy.expiration = AS_FOREVER_EXPIRATION_TTL;
            writePolicy.sendKey = true;

            writeToAerospike(deDuperName, true, writePolicy, deDuperConfig);

        } catch (RetryException e) {
            throw SolusException.propagate(String.format(
                    Constants.REGISTER_DEDUPER_ERROR, deDuperName), e.getCause(), ErrorCode.AEROSPIKE_ERROR);
        } catch (ExecutionException e) {
            throw SolusException.propagate(String.format(
                    Constants.REGISTER_DEDUPER_ERROR, deDuperName), e, ErrorCode.AEROSPIKE_ERROR);
        }
    }

    @Override
    public void updateStatus(final String deDuperName, final boolean status) {
        try {
            final Optional<DeDuper> storedDeDuper = get(deDuperName);
            if (storedDeDuper.isPresent()) {
                final WritePolicy writePolicy = new WritePolicy(aerospikeClient.getWritePolicyDefault());
                writePolicy.recordExistsAction = RecordExistsAction.UPDATE_ONLY;
                writeToAerospike(deDuperName, status, writePolicy, storedDeDuper.get().getDeDuperConfig());
                return;
            }

            log.error("It should never have come here, some bug. [DeDuper: {}]", deDuperName);
            throw SolusException.builder()
                    .errorCode(ErrorCode.INTERNAL_ERROR)
                    .build();
        } catch (RetryException e) {
            throw SolusException.propagate(String.format(Constants.DEDUPER_STATE_CHANGE_ERROR, deDuperName),
                    e.getCause(), ErrorCode.AEROSPIKE_ERROR);
        } catch (ExecutionException e) {
            throw SolusException.propagate(String.format(Constants.DEDUPER_STATE_CHANGE_ERROR, deDuperName),
                    e, ErrorCode.AEROSPIKE_ERROR);
        }
    }

    @Override
    public Map<String, DeDuper> getAllActive() {
        final Map<String, DeDuper> activeDeDupersMap = new ConcurrentHashMap<>();
        final Statement statement = new Statement();
        statement.setNamespace(namespace);
        statement.setSetName(getSetName());

        try {
            final RecordSet rs = (RecordSet) AerospikeUtils.retryer.call(() ->
                    aerospikeClient.query(aerospikeClient.getQueryPolicyDefault(), statement));
            rs.iterator().forEachRemaining(keyRecord ->
                    buildDeDuper(keyRecord.key.userKey.toString(), keyRecord.record).ifPresent(deDuper -> {
                        if (isDeDuperActiveInAllFarms(keyRecord)) {
                            activeDeDupersMap.put(deDuper.getName(), deDuper);
                        }
                    })
            );
            return activeDeDupersMap;
        } catch (Exception e) {
            throw SolusException.propagate(ErrorMessages.GET_DEDUPERS_ERROR, e.getCause(), ErrorCode.AEROSPIKE_ERROR);
        }
    }

    @Override
    public Optional<DeDuper> get(final String deDuperName) {
        try {
            final Key key = new Key(namespace, getSetName(), buildMetaKey(deDuperName));
            final Record asRecord = (Record) AerospikeUtils.retryer.call(() ->
                    aerospikeClient.get(aerospikeClient.getReadPolicyDefault(), key)
            );
            return buildDeDuper(key.userKey.toString(), asRecord);
        } catch (ExecutionException e) {
            throw SolusException.propagate(ErrorMessages.REGISTRATION_RECORD_FETCH_ERROR, e, ErrorCode.AEROSPIKE_ERROR);
        } catch (RetryException e) {
            throw SolusException.propagate(ErrorMessages.REGISTRATION_RECORD_FETCH_ERROR, e.getCause(), ErrorCode.AEROSPIKE_ERROR);
        }
    }

    private void addFarmInAggregator(final String key) {
        try {
            final WritePolicy writePolicy = new WritePolicy(aerospikeClient.getWritePolicyDefault());
            writePolicy.expiration = NO_EXPIRATION_UPDATE_TTL;

            AerospikeUtils.retryer.call(() -> {
                aerospikeClient.put(writePolicy,
                        new Key(namespace, getSetName(), key),
                        new Bin(AerospikeUtils.getBin(FARM_BIN, farm), true)
                );
                return null;
            });
        } catch (Exception e) {
            throw SolusException.propagate(String.format(Constants.REGISTER_DEDUPER_ERROR, key), e, ErrorCode.AEROSPIKE_ERROR);
        }
    }

    private boolean isDeDuperActiveInAllFarms(final KeyRecord keyRecord) {
        return AerospikeUtils.filterFarmSpecificBins(keyRecord.record, ACTIVE_BIN)
                .map(bin -> (Long) keyRecord.record.bins.get(bin) != 0)
                .reduce(true, (accumulator, status) -> accumulator && status);
    }

    private Optional<DeDuper> buildDeDuper(final String key, final Record asRecord) {
        final String latestUpdateFarmForThisRecord = AerospikeUtils.getLatestUpdatedFarm(asRecord, UPDATED_BIN);
        if (isActiveDeDuper(asRecord, latestUpdateFarmForThisRecord)) {
            final Set<String> storedFarms = AerospikeUtils.filterFarmSpecificBins(asRecord, FARM_BIN)
                    .filter(bin -> bin.contains(AerospikeUtils.BIN_FARM_SEPARATOR))
                    .map(bin -> bin.substring(0, bin.indexOf(AerospikeUtils.BIN_FARM_SEPARATOR)))
                    .collect(Collectors.toSet());
            // If current requesting doesn't have the farm updated in dedupers, then add the farm in deduper
            // This will help in figuring out the farms to fetch for de dupe record.
            if (!storedFarms.contains(farm)) {
                addFarmInAggregator(key);
                storedFarms.add(farm);
            }

            final String level = (String) AerospikeUtils.getFarmSpecificBinValue(asRecord, LEVEL_BIN, latestUpdateFarmForThisRecord);
            return Optional.of(DeDuper.builder()
                    .name((String) AerospikeUtils.getFarmSpecificBinValue(asRecord, NAME_BIN, latestUpdateFarmForThisRecord))
                    .deDuperConfig(DeDuperConfig.builder()
                            .noOfHashFunctions(((Long) AerospikeUtils.getFarmSpecificBinValue(
                                    asRecord, NO_OF_HASH_FUNCTIONS_BIN, latestUpdateFarmForThisRecord)).intValue())
                            .noOfShards((long) AerospikeUtils.getFarmSpecificBinValue(
                                    asRecord, NO_OF_SHARDS_BIN, latestUpdateFarmForThisRecord))
                            .bitsPerShard(((Long) AerospikeUtils.getFarmSpecificBinValue(
                                    asRecord, BITS_PER_SHARD_BIN, latestUpdateFarmForThisRecord)).intValue())
                            .deDuperLevel(Objects.nonNull(level)
                                    ? DeDuperLevel.valueOf(level)
                                    : DeDuperLevel.XDC)
                            .build())
                    .clientId(clientId)
                    .farms(storedFarms)
                    .updatedAt(new Date((Long) AerospikeUtils.getFarmSpecificBinValue(
                            asRecord, UPDATED_BIN, latestUpdateFarmForThisRecord)))
                    .build());
        }
        return Optional.empty();
    }

    private boolean isActiveDeDuper(final Record asRecord, final String latestUpdatedFarmForThisRecord) {
        return Objects.nonNull(asRecord)
                && ((Long) AerospikeUtils.getFarmSpecificBinValue(asRecord, ACTIVE_BIN, latestUpdatedFarmForThisRecord) != 0);
    }

    private String getSetName() {
        return SET_FORMAT.formatted(clientId);
    }

    private String buildMetaKey(final String deDuperName) {
        return String.format(AEROSPIKE_KEY_FORMAT, clientId, deDuperName);
    }

    private void writeToAerospike(final String deDuperName,
                                  final boolean status,
                                  final WritePolicy writePolicy,
                                  final DeDuperConfig deDuperConfig) throws ExecutionException, RetryException {
        AerospikeUtils.retryer.call(() -> {
            aerospikeClient.put(writePolicy,
                    new Key(namespace, getSetName(), buildMetaKey(deDuperName)),
                    new Bin(AerospikeUtils.getBin(NAME_BIN, farm), deDuperName),
                    new Bin(AerospikeUtils.getBin(NO_OF_HASH_FUNCTIONS_BIN, farm), deDuperConfig.getNoOfHashFunctions()),
                    new Bin(AerospikeUtils.getBin(NO_OF_SHARDS_BIN, farm), deDuperConfig.getNoOfShards()),
                    new Bin(AerospikeUtils.getBin(BITS_PER_SHARD_BIN, farm), deDuperConfig.getBitsPerShard()),
                    new Bin(AerospikeUtils.getBin(ACTIVE_BIN, farm), status),
                    new Bin(AerospikeUtils.getBin(LEVEL_BIN, farm), deDuperConfig.getDeDuperLevel()),
                    new Bin(AerospikeUtils.getBin(UPDATED_BIN, farm), System.currentTimeMillis()),
                    new Bin(AerospikeUtils.getBin(FARM_BIN, farm), true)
            );
            return null;
        });
    }

    private void createdIndexes() {
        final String binWithFarm = AerospikeUtils.getBin(ACTIVE_BIN, farm);
        createIndex(
                String.format(ACTIVE_BIN_INDEX_FORMAT, getSetName(), binWithFarm),
                binWithFarm,
                IndexType.STRING
        );
        // For backward compatibility
        createIndex(
                String.format(ACTIVE_BIN_INDEX_FORMAT, getSetName(), ACTIVE_BIN),
                ACTIVE_BIN,
                IndexType.STRING
        );
    }

    private void createIndex(final String indexName,
                             final String bin,
                             final IndexType indexType) {
        try {
            aerospikeClient.createIndex(null, namespace, getSetName(), indexName, bin, indexType).waitTillComplete();
        } catch (AerospikeException e) {
            if (e.getResultCode() == 200) {
                log.info("Ignoring index creation. Index seems to be already present");
                return;
            }
            throw e;
        }
    }
}
