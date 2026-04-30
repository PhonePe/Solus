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

package com.phonepe.solus.util;

import com.aerospike.client.AerospikeException;
import com.aerospike.client.Record;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.BlockStrategies;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@UtilityClass
public class AerospikeUtils {
    public static final String BIN_FARM_SEPARATOR = "##";
    public static final String BIN_FARM_SEPARATOR_V2 = "#";
    private static final String BIN_FORMAT = "%s" + BIN_FARM_SEPARATOR + "%s";
    private static final String BIN_FORMAT_V2 = "%s" + BIN_FARM_SEPARATOR_V2 + "%s";
    private static final int DEFAULT_SLEEP_TIME = 80;
    private static final int DEFAULT_RETRY_ATTEMPTS = 5;

    public static final Retryer<Object> retryer = RetryerBuilder.newBuilder()
            .retryIfExceptionOfType(AerospikeException.class)
            .withStopStrategy(StopStrategies.stopAfterAttempt(DEFAULT_RETRY_ATTEMPTS))
            .withWaitStrategy(WaitStrategies.fixedWait(DEFAULT_SLEEP_TIME, TimeUnit.MILLISECONDS))
            .withBlockStrategy(BlockStrategies.threadSleepStrategy())
            .build();

    public Object getFarmSpecificBinValue(final Record asRecord, final String binSuffix, final String farm) {
        return asRecord.bins.getOrDefault(BIN_FORMAT.formatted(farm, binSuffix), asRecord.bins.get(binSuffix));
    }

    public Stream<String> filterFarmSpecificBins(final Record asRecord, final String binSuffix) {
        return asRecord.bins.keySet().stream()
                .filter(storedBin -> storedBin.contains(BIN_FARM_SEPARATOR)
                        ? storedBin.contains(BIN_FARM_SEPARATOR.concat(binSuffix))
                        : storedBin.equals(binSuffix));
    }

    public String getBin(final String binSuffix, final String farm) {
        return BIN_FORMAT.formatted(farm, binSuffix);
    }

    public String getBinV2(final String binSuffix, final String farm) {
        return BIN_FORMAT_V2.formatted(farm, binSuffix);
    }

    public String getLatestUpdatedFarm(final Record asRecord, final String binSuffix) {
        if (Objects.isNull(asRecord)) {
            return null;
        }

        final Pair<String, Long> farmWithUpdateTS = filterFarmSpecificBins(asRecord, binSuffix)
                .map(storedBin -> Pair.of(storedBin, asRecord.getLong(storedBin)))
                .max(Map.Entry.comparingByValue())
                .orElse(null);
        return Objects.nonNull(farmWithUpdateTS)
                ? getFarmFromUpdateTS(farmWithUpdateTS)
                : null;
    }

    private String getFarmFromUpdateTS(final Pair<String, Long> farmWithUpdateTS) {
        return farmWithUpdateTS.getKey().contains(BIN_FARM_SEPARATOR)
                ? farmWithUpdateTS.getKey().substring(0, farmWithUpdateTS.getKey().indexOf(BIN_FARM_SEPARATOR))
                : "";
    }
}
