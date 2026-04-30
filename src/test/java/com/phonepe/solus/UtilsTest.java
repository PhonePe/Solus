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

import com.phonepe.solus.shard.ShardCalculator;
import com.phonepe.solus.util.SizeUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;

@Slf4j
public class UtilsTest {

    @Test
    public void testNumShardCalculation() {
        Assert.assertEquals(10, SizeUtils.calculateNumberOfShards(10000));
        Assert.assertEquals(11, SizeUtils.calculateNumberOfShards(10001));
        Assert.assertEquals(91, SizeUtils.calculateNumberOfShards(90001));
    }

    @Test
    public void testShardCalculator() {
        int numShards = 10;
        ShardCalculator<Long> shardCalculator = new ShardCalculator<>();
        long shardId = shardCalculator.getShardId(10000L, numShards);
        Assert.assertTrue(shardId < numShards);
        Assert.assertEquals(shardId, shardCalculator.getShardId(10000L, numShards));
    }

}
