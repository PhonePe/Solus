package com.phonepe.solus;

import com.aerospike.client.AerospikeClient;
import com.aerospike.client.Host;
import com.aerospike.client.Key;
import com.aerospike.client.Record;
import com.aerospike.client.policy.ClientPolicy;
import com.phonepe.solus.config.DeDuperConfig;
import com.phonepe.solus.shard.ShardCalculator;
import com.phonepe.solus.store.context.impl.AerospikeStorageContext;
import com.phonepe.solus.util.AerospikeUtils;
import io.dropwizard.validation.BaseValidator;
import java.util.Set;

public class SolusTest {

    private static final String AEROSPIKE_NAMESPACE = "test";
    private static final String AEROSPIKE_HOST = "localhost";
    private static final int AEROSPIKE_PORT = 3000;
    private static final String SET_NAME = "events";
    private static final String DE_DUPER_NAME = "lead";
    private static final String CLIENT_ID = "vidar";

    public static void main(String[] args) {
        try (AerospikeClient client = new AerospikeClient(new ClientPolicy(), new Host(AEROSPIKE_HOST, AEROSPIKE_PORT))) {

            client.truncate(client.getInfoPolicyDefault(), AEROSPIKE_NAMESPACE, null, null);

            final AerospikeStorageContext storageContext = AerospikeStorageContext.builder()
                    .aerospikeClient(client)
                    .setName(SET_NAME)
                    .namespace(AEROSPIKE_NAMESPACE)
                    .farm("NB6")
                    .build();

            try  {
                SolusEngine engine = new SolusEngine(BaseValidator.newValidator(), CLIENT_ID, storageContext);
                engine.register(DE_DUPER_NAME, DeDuperConfig.builder()
                        .expiryInSeconds(6)
                    .build());

                final String singleEntity = "single-entity";
                final String batchEntity1 = "batch-entity3";
                final String batchEntity2 = "batch-entity4";
                final String singleEntity2 = "single-entity";
                final long ttlInSeconds = 60L;

                engine.add(DE_DUPER_NAME, Set.of(batchEntity1, batchEntity2),8);

                Thread.sleep(20L);

                engine.add(DE_DUPER_NAME, singleEntity2, 100);

                final DeDuper deDuper = engine.getDeDuper(DE_DUPER_NAME);
                final ShardCalculator<String> shardCalculator = new ShardCalculator<>();

                final long singleShardId = shardCalculator.getShardId(singleEntity, deDuper.getDeDuperConfig().getNoOfShards());
                final long batchShardId = shardCalculator.getShardId(batchEntity2, deDuper.getDeDuperConfig().getNoOfShards());

                final Record singleRecord = client.get(null, new Key(AEROSPIKE_NAMESPACE, SET_NAME,
                        String.format("%d|%s|%s", singleShardId, CLIENT_ID, DE_DUPER_NAME)));
                final Record batchRecord = client.get(null, new Key(AEROSPIKE_NAMESPACE, SET_NAME,
                        String.format("%d|%s|%s", batchShardId, CLIENT_ID, DE_DUPER_NAME)));

                System.out.println("Single record TTL: " + singleRecord.getTimeToLive() + "s (expected ~" + ttlInSeconds + "s)");
                System.out.println("Batch record TTL:  " + batchRecord.getTimeToLive() + "s (expected ~" + ttlInSeconds + "s)");

                if (Math.abs(singleRecord.getTimeToLive() - ttlInSeconds) > 1) {
                    throw new AssertionError("Single add TTL mismatch: " + singleRecord.getTimeToLive());
                }
                if (Math.abs(batchRecord.getTimeToLive() - ttlInSeconds) > 1) {
                    throw new AssertionError("Batch add TTL mismatch: " + batchRecord.getTimeToLive());
                }

                System.out.println("Both single and batch writes applied the requested TTL correctly.");
            }catch (Exception e){

            }
        }
    }
}
