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
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;

@Slf4j
public class SizeCalculatorTest {

    @Test
    public void sizeTest() {
        double falsePositiveProbability = 0.01;
        long dataSize = 1000000000L;
        long numberOfHashFunctions = (long) Math.ceil(-(Math.log(falsePositiveProbability) / Math.log(2)));
        Assert.assertEquals(7, numberOfHashFunctions);
        long numberOfBits = (long) ((dataSize * Math.abs(Math.log(falsePositiveProbability))) / (Math.pow(Math.log(2),
                2)));
        Assert.assertEquals(9585058377L, numberOfBits);
        numberOfBits = (numberOfBits % DeDuperConfig.MIN_BITS_PER_SHARD != 0)
                ? (numberOfBits / DeDuperConfig.MIN_BITS_PER_SHARD * DeDuperConfig.MIN_BITS_PER_SHARD + DeDuperConfig.MIN_BITS_PER_SHARD)
                : (numberOfBits / DeDuperConfig.MIN_BITS_PER_SHARD) * DeDuperConfig.MIN_BITS_PER_SHARD;
        Assert.assertEquals(9585059000L, numberOfBits);
        Assert.assertEquals(0, numberOfBits % DeDuperConfig.MIN_BITS_PER_SHARD);
    }

    @Test
    public void sizeTest2() {
        double falsePositiveProbability = 0.01;
        long dataSize = 1000L;
        long numberOfHashFunctions = (long) Math.ceil(-(Math.log(falsePositiveProbability) / Math.log(2)));
        Assert.assertEquals(7, numberOfHashFunctions);
        long numberOfBits = (long) ((dataSize * Math.abs(Math.log(falsePositiveProbability))) / (Math.pow(Math.log(2),
                2)));
        Assert.assertEquals(9585L, numberOfBits);
        numberOfBits = (numberOfBits % DeDuperConfig.MIN_BITS_PER_SHARD != 0)
                ? (numberOfBits / DeDuperConfig.MIN_BITS_PER_SHARD) * DeDuperConfig.MIN_BITS_PER_SHARD + DeDuperConfig.MIN_BITS_PER_SHARD
                : (numberOfBits / DeDuperConfig.MIN_BITS_PER_SHARD) * DeDuperConfig.MIN_BITS_PER_SHARD;
        Assert.assertEquals(10000L, numberOfBits);
        Assert.assertEquals(0, numberOfBits % DeDuperConfig.MIN_BITS_PER_SHARD);
    }


    @Test
    public void sizeTest3() {
        double falsePositiveProbability = 0.01;
        long numberOfHashFunctions = (long) Math.ceil(-(Math.log(falsePositiveProbability) / Math.log(2)));
        Assert.assertEquals(7, numberOfHashFunctions);
    }

}
