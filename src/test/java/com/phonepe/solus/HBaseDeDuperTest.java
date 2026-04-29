/**
 * Copyright (c) 2025 Original Author(s), PhonePe India Pvt. Ltd.
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

import com.phonepe.solus.config.DeDuperConfig;
import com.phonepe.solus.config.DeDuperLevel;
import com.phonepe.solus.exception.ErrorCode;
import com.phonepe.solus.exception.SolusException;
import com.phonepe.solus.hbase.HBaseTableConnection;
import com.phonepe.solus.store.context.impl.HBaseStorageContext;
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
import java.util.List;
import java.util.HashMap;

@RunWith(MockitoJUnitRunner.class)
public class HBaseDeDuperTest {
    private SolusEngine solusEngine;
    private static final String CLIENT_ID = "offers";
    private static final String DE_DUPER_NAME = "coupons";

    @Before
    public void setUp() {
        MockHBaseConnection connection = new MockHBaseConnection();
        HBaseTableConnection tab = new HBaseTableConnection(false, connection);
        final HBaseStorageContext hBaseStorageContext = HBaseStorageContext.builder()
                .connection(tab)
                .tableName("coupons_bloom_filter")
                .farm("NB6")
                .build();
        solusEngine = new SolusEngine(BaseValidator.newValidator(), CLIENT_ID, hBaseStorageContext);
        solusEngine.register(DE_DUPER_NAME);
    }

    @Test
    public void registerDeDuper() {
        // register DeDuper with invalid config
        try {
            solusEngine.register(DE_DUPER_NAME, DeDuperConfig.builder()
                    .noOfHashFunctions(10)
                    .bitsPerShard(2000)
                    .noOfShards(10)
                    .build());
        } catch (SolusException e) {
            Assert.assertEquals(ErrorCode.INVALID_CONFIG, e.getErrorCode());
        }

        // register same DeDuper with different config
        try {
            solusEngine.register(DE_DUPER_NAME, DeDuperConfig.builder()
                    .noOfHashFunctions(10)
                    .bitsPerShard(2000)
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
        solusEngine.add("couponsV2", "Test123", 6000);
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
    }

    @Test
    public void testBatchCheck() {
        Set<String> set = new HashSet<>(
                Arrays.asList("TSTKHBC7F7KLM6ST", "TSTFKC7QHZRSHPGJ", "TSTNKFGAWQXDXJD7",
                        "TSTHRM4DA49AKYP7"));
        Map<Long, List<Integer>> resultMap = new HashMap<>();
        resultMap.put(77L,
                Arrays.asList(5847, 311, 6711, 6949, 8338, 8686, 5568, 4825, 1997, 758, 962, 5506, 7825));
        resultMap.put(78L, Arrays.asList(2095, 2323, 3190, 4186, 7264, 7281, 9637));
        Map<String, Boolean> batchCheck = solusEngine.checkAbsence(DE_DUPER_NAME, set);
        Assert.assertTrue(batchCheck.get("TSTKHBC7F7KLM6ST"));
        Assert.assertTrue(batchCheck.get("TSTFKC7QHZRSHPGJ"));
        Assert.assertTrue(batchCheck.get("TSTNKFGAWQXDXJD7"));
        Assert.assertTrue(batchCheck.get("TSTHRM4DA49AKYP7"));
    }

    @Test
    public void testcheckAbsence() {
        boolean check = solusEngine.checkAbsence(DE_DUPER_NAME, "TSTKHBC7F7KLM6ST");
        Assert.assertTrue(check);
    }

    @Test
    public void testAdd() {
        Map<Long, List<Integer>> resultMap = new HashMap<>();
        resultMap.put(8694893L, Arrays.asList(847, 311, 711, 949, 338, 686, 568));
        solusEngine.add(DE_DUPER_NAME, "TSTKHBC7F7KLM6ST", 6000);
        Map<String, Boolean> batchCheck = solusEngine.checkAbsence(DE_DUPER_NAME,
                Collections.singleton("TSTKHBC7F7KLM6ST"));
        Assert.assertFalse(batchCheck.get("TSTKHBC7F7KLM6ST"));
    }

    @Test
    public void testBatchAdd() throws Exception {
        Map<Long, List<Integer>> resultMap = new HashMap<>();
        resultMap.put(8694893L, Arrays.asList(847, 311, 711, 949, 338, 686, 568));
        solusEngine.add(DE_DUPER_NAME, Collections.singleton("TSTKHBC7F7KLM6ST"), 6000);
        Map<String, Boolean> batchCheck = solusEngine.checkAbsence(DE_DUPER_NAME,
                Collections.singleton("TSTKHBC7F7KLM6ST"));
        Assert.assertFalse(batchCheck.get("TSTKHBC7F7KLM6ST"));
    }

    @Test
    public void testAddIfAbsent() throws Exception {
        Map<Long, List<Integer>> resultMap = new HashMap<>();
        resultMap.put(8694893L, Arrays.asList(847, 311, 711, 949, 338, 686, 568));
        solusEngine.addIfAbsent(DE_DUPER_NAME, "TSTKHBC7F7KLM6ST", 6000);
        Map<String, Boolean> batchCheck = solusEngine.checkAbsence(DE_DUPER_NAME,
                Collections.singleton("TSTKHBC7F7KLM6ST"));
        Assert.assertFalse(batchCheck.get("TSTKHBC7F7KLM6ST"));
    }

    @Test
    public void testBatchAddIfAbsent() throws Exception {
        Map<Long, List<Integer>> resultMap = new HashMap<>();
        resultMap.put(8694893L, Arrays.asList(847, 311, 711, 949, 338, 686, 568));
        solusEngine.addIfAbsent(DE_DUPER_NAME, Collections.singleton("TSTKHBC7F7KLM6ST"), 6000);
        Map<String, Boolean> batchCheck = solusEngine.checkAbsence(DE_DUPER_NAME,
                Collections.singleton("TSTKHBC7F7KLM6ST"));
        Assert.assertFalse(batchCheck.get("TSTKHBC7F7KLM6ST"));
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
        solusEngine.unregister(DE_DUPER_NAME);
    }
}
