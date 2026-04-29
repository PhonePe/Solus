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

package com.phonepe.solus.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

@Data
@Builder
@AllArgsConstructor
public class DeDuperConfig {
    /**
     * The below min and max values are chosen on the basis of calculation to achieve minimum false positive probability.
     * The max key space of DeDuper can be MAX_NUMBER_OF_SHARDS * MAX_BITS_PER_SHARD = 4.5 trillion
     */
    public static final int MIN_NUMBER_OF_HASH_FUNCTION = 7;
    public static final int MAX_NUMBER_OF_HASH_FUNCTION = 13;
    public static final long MIN_NUMBER_OF_SHARDS = 10000000;
    public static final long MAX_NUMBER_OF_SHARDS = 150000000;
    public static final int MIN_BITS_PER_SHARD = 1000;
    public static final int MAX_BITS_PER_SHARD = 30000;

    @Min(MIN_NUMBER_OF_HASH_FUNCTION)
    @Max(MAX_NUMBER_OF_HASH_FUNCTION)
    @Builder.Default
    private int noOfHashFunctions = MIN_NUMBER_OF_HASH_FUNCTION;
    @Min(MIN_NUMBER_OF_SHARDS)
    @Max(MAX_NUMBER_OF_SHARDS)
    @Builder.Default
    private long noOfShards = MIN_NUMBER_OF_SHARDS;
    @Min(MIN_BITS_PER_SHARD)
    @Max(MAX_BITS_PER_SHARD)
    @Builder.Default
    private int bitsPerShard = MIN_BITS_PER_SHARD;
    @Builder.Default
    private DeDuperLevel deDuperLevel = DeDuperLevel.XDC; // For Backward compatibility

    @JsonIgnore
    public boolean isEqual(final DeDuperConfig deDuperConfig) {
        return this.getNoOfHashFunctions() == deDuperConfig.getNoOfHashFunctions()
                && this.getNoOfShards() == deDuperConfig.getNoOfShards()
                && this.getBitsPerShard() == deDuperConfig.getBitsPerShard();
    }
}
