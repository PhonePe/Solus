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

package com.phonepe.solus;

import com.aerospike.client.AerospikeClient;
import com.aerospike.client.Host;
import com.aerospike.client.Key;
import com.aerospike.client.Record;
import com.aerospike.client.policy.ClientPolicy;
import com.phonepe.solus.config.DeDuperConfig;
import com.phonepe.solus.config.DeDuperLevel;
import com.phonepe.solus.exception.ErrorCode;
import com.phonepe.solus.exception.SolusException;
import com.phonepe.solus.store.aerospike.AerospikeDeDuperMetaStore;
import com.phonepe.solus.store.context.impl.AerospikeStorageContext;
import com.phonepe.solus.util.AerospikeUtils;
import io.appform.testcontainers.aerospike.AerospikeContainerConfiguration;
import io.appform.testcontainers.aerospike.container.AerospikeContainer;
import io.dropwizard.validation.BaseValidator;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.HashSet;
import java.util.Collections;
import java.util.Arrays;
import java.util.Set;
import java.util.Map;
import java.util.stream.Collectors;

@RunWith(MockitoJUnitRunner.class)
public class AerospikeDeDuperTest {
    private static final String AEROSPIKE_DOCKER_IMAGE = "aerospike/aerospike-server:6.1.0.7";
    private static final AerospikeContainer AEROSPIKE_DOCKER_CONTAINER;
    private static final String AEROSPIKE_NAMESPACE = "solus";
    private static final String AEROSPIKE_HOST = "localhost";
    private static final int AEROSPIKE_PORT = 3000;
    private static final String SET_NAME = "coupons";
    private static final String CLIENT_ID = "offers";
    private static final String DE_DUPER_NAME = "coupons";

    private SolusEngine solusEngine;
    public AerospikeClient aerospikeClient;

    static {
        var aerospikeContainerConfig = new AerospikeContainerConfiguration(
                true,
                AEROSPIKE_DOCKER_IMAGE,
                AEROSPIKE_NAMESPACE,
                AEROSPIKE_HOST,
                AEROSPIKE_PORT);
        aerospikeContainerConfig.setWaitTimeoutInSeconds(300L);
        AEROSPIKE_DOCKER_CONTAINER = new AerospikeContainer(aerospikeContainerConfig);
        AEROSPIKE_DOCKER_CONTAINER.start();
    }

    @Before
    public void setUp() {
        initialiseAerospike("NB6", false);
        solusEngine.register(DE_DUPER_NAME);
    }

    @Test
    public void registerDeDuperInMultiDCEnv() {
        initialiseAerospike("MH6", false);
        solusEngine.register(DE_DUPER_NAME);

        final Record asRecord = aerospikeClient.get(
                null, new Key(
                        AEROSPIKE_NAMESPACE,
                        AerospikeDeDuperMetaStore.SET_FORMAT.formatted(CLIENT_ID),
                        CLIENT_ID + "|" + DE_DUPER_NAME
                ));
        Assert.assertEquals(2, AerospikeUtils.filterFarmSpecificBins(asRecord, "farm").collect(Collectors.toSet()).size());
    }

    @Test
    public void registerDeDuperExceptionsTest() {
        // register DeDuper with invalid config
        try {
            solusEngine.register(DE_DUPER_NAME, DeDuperConfig.builder()
                    .noOfHashFunctions(7)
                    .bitsPerShard(10000)
                    .noOfShards(10)
                    .build());
        } catch (SolusException e) {
            Assert.assertEquals(ErrorCode.INVALID_CONFIG, e.getErrorCode());
        }

        // register same DeDuper with different config
        try {
            solusEngine.register(DE_DUPER_NAME, DeDuperConfig.builder()
                    .noOfHashFunctions(7)
                    .bitsPerShard(10000)
                    .noOfShards(10000000)
                    .build());
        } catch (SolusException e) {
            Assert.assertEquals(ErrorCode.DEDUPER_CONFIG_MISMATCH, e.getErrorCode());
        }
    }

    @Test
    public void testAddEntityInDCLevelDeDuper() {
        solusEngine.register("couponsV2", DeDuperConfig.builder()
                .noOfHashFunctions(10)
                .bitsPerShard(1000)
                .noOfShards(10000000)
                .deDuperLevel(DeDuperLevel.DC)
                .build());
        solusEngine.add("couponsV2", "Test123", 60000);
        Assert.assertFalse(solusEngine.checkAbsence("couponsV2", "Test123"));
        Assert.assertTrue(solusEngine.checkAbsence("couponsV2", "Test1234"));
    }

    @Test
    public void getDeDuper() {
        Assert.assertNotNull(solusEngine.getDeDuper(DE_DUPER_NAME));
        try {
            solusEngine.getDeDuper("random");
        } catch (SolusException e) {
            Assert.assertEquals(ErrorCode.DEDUPER_NOT_FOUND, e.getErrorCode());
        }
    }

    @Test
    public void getActiveDeDupersInMultiDCEnvTest() {
        Assert.assertEquals(1, solusEngine.getActiveDeDupers().size());

        initialiseAerospike("MH6", false);
        Assert.assertEquals(1, solusEngine.getActiveDeDupers().size());
        solusEngine.unregister(DE_DUPER_NAME);
        Assert.assertEquals(0, solusEngine.getActiveDeDupers().size());

        initialiseAerospike("NB6", false);
        Assert.assertEquals(0, solusEngine.getActiveDeDupers().size());
    }

    @Test
    public void testBatchCheck() {
        Set<String> set = new HashSet<>(
                Arrays.asList("TSTKHBC7F7KLM6ST1", "TSTFKC7QHZRSHPGJ2", "TSTNKFGAWQXDXJD73", "TSTHRM4DA49AKYP74"));
        Map<String, Boolean> batchCheck = solusEngine.checkAbsence(DE_DUPER_NAME, set);
        Assert.assertTrue(batchCheck.get("TSTKHBC7F7KLM6ST1"));
        Assert.assertTrue(batchCheck.get("TSTFKC7QHZRSHPGJ2"));
        Assert.assertTrue(batchCheck.get("TSTNKFGAWQXDXJD73"));
        Assert.assertTrue(batchCheck.get("TSTHRM4DA49AKYP74"));
    }

    @Test
    public void testcheckAbsence() {
        boolean check = solusEngine.checkAbsence(DE_DUPER_NAME, "TSTKHBC7F7KLM6ST1");
        Assert.assertTrue(check);
    }


    @Test
    public void testAdd() {
        solusEngine.add(DE_DUPER_NAME, "TSTKHBC7F7KLM6ST", 60000);
        Map<String, Boolean> batchCheck = solusEngine.checkAbsence(DE_DUPER_NAME,
                Collections.singleton("TSTKHBC7F7KLM6ST"));
        Assert.assertFalse(batchCheck.get("TSTKHBC7F7KLM6ST"));
    }

    @Test
    public void testBatchAdd() {
        solusEngine.add(DE_DUPER_NAME, Collections.singleton("TSTKHBC7F7KLM6ST"), 60000);
        Map<String, Boolean> batchCheck = solusEngine.checkAbsence(DE_DUPER_NAME,
                Collections.singleton("TSTKHBC7F7KLM6ST"));
        Assert.assertFalse(batchCheck.get("TSTKHBC7F7KLM6ST"));
    }

    @Test
    public void testAddIfAbsent() {
        solusEngine.addIfAbsent(DE_DUPER_NAME, "TSTKHBC7F7KLM6ST", 60000);
        Map<String, Boolean> batchCheck = solusEngine.checkAbsence(DE_DUPER_NAME,
                Collections.singleton("TSTKHBC7F7KLM6ST"));
        Assert.assertFalse(batchCheck.get("TSTKHBC7F7KLM6ST"));
    }

    @Test
    public void testBatchAddIfAbsent() {
        solusEngine.addIfAbsent(DE_DUPER_NAME, Collections.singleton("TSTKHBC7F7KLM6ST"), 60000);
        Map<String, Boolean> batchCheck = solusEngine.checkAbsence(DE_DUPER_NAME,
                Collections.singleton("TSTKHBC7F7KLM6ST"));
        Assert.assertFalse(batchCheck.get("TSTKHBC7F7KLM6ST"));
    }

    @Test
    public void testSolusInMultiDCEnv() {
        solusEngine.add(DE_DUPER_NAME, "Test1", 60000);
        Assert.assertFalse(solusEngine.checkAbsence(DE_DUPER_NAME, "Test1"));
        Assert.assertTrue(solusEngine.checkAbsence(DE_DUPER_NAME, "Test2"));

        initialiseAerospike("MH6", false);
        Assert.assertFalse(solusEngine.checkAbsence(DE_DUPER_NAME, "Test1"));
        solusEngine.add(DE_DUPER_NAME, "Test2", 60000);
        Assert.assertFalse(solusEngine.checkAbsence(DE_DUPER_NAME, "Test2"));

        initialiseAerospike("NB6", false);
        Assert.assertFalse(solusEngine.checkAbsence(DE_DUPER_NAME, "Test1"));
        Assert.assertFalse(solusEngine.checkAbsence(DE_DUPER_NAME, "Test2"));
    }

    @Test
    public void unregisterDeDuper() {
        try {
            solusEngine.unregister("random");
        } catch (SolusException e) {
            Assert.assertEquals(ErrorCode.DEDUPER_NOT_FOUND, e.getErrorCode());
        }
    }

    @After
    public void tearDown() {
        aerospikeClient.truncate(aerospikeClient.getInfoPolicyDefault(), AEROSPIKE_NAMESPACE, null, null);
    }

    private void initialiseAerospike(final String farm, final boolean clear) {
        aerospikeClient = new AerospikeClient(new ClientPolicy(),
                new Host(AEROSPIKE_DOCKER_CONTAINER.getContainerIpAddress(), AEROSPIKE_DOCKER_CONTAINER.getConnectionPort()));
        if (clear) {
            aerospikeClient.truncate(aerospikeClient.getInfoPolicyDefault(), AEROSPIKE_NAMESPACE, null, null);
        }
        final AerospikeStorageContext storageContext = AerospikeStorageContext.builder()
                .aerospikeClient(aerospikeClient)
                .setName(SET_NAME)
                .namespace(AEROSPIKE_NAMESPACE)
                .farm(farm)
                .build();
        solusEngine = new SolusEngine(BaseValidator.newValidator(), CLIENT_ID, storageContext);
    }
}
