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

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.experimental.UtilityClass;

import java.util.concurrent.TimeUnit;

@UtilityClass
public class CacheUtils {
    private static final int DEFAULT_MAXIMUM_SIZE = 4096;
    private static final int DEFAULT_REFRESH_TIME_IN_SECS = 300;
    private static final int DEFAULT_EXPIRY_TIME_IN_HOURS = 3;

    public static <K, R> AsyncLoadingCache<K, R> buildCache(final CacheLoader<K, R> cacheLoader) {
        return Caffeine.newBuilder()
                .maximumSize(DEFAULT_MAXIMUM_SIZE)
                .refreshAfterWrite(DEFAULT_REFRESH_TIME_IN_SECS, TimeUnit.SECONDS)
                .expireAfterWrite(DEFAULT_EXPIRY_TIME_IN_HOURS, TimeUnit.HOURS)
                .buildAsync(cacheLoader);
    }
}
