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

package com.phonepe.solus.commands.impl;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.phonepe.solus.DeDuper;
import com.phonepe.solus.commands.IDeDuperCrudCommands;
import com.phonepe.solus.config.DeDuperConfig;
import com.phonepe.solus.exception.ErrorCode;
import com.phonepe.solus.exception.SolusException;
import com.phonepe.solus.store.IDeDuperMetaStore;
import com.phonepe.solus.store.aerospike.AerospikeDeDuperMetaStore;
import com.phonepe.solus.store.context.StorageContext;
import com.phonepe.solus.store.context.impl.AerospikeStorageContext;
import com.phonepe.solus.store.context.impl.HBaseStorageContext;
import com.phonepe.solus.store.hbase.HBaseDeDuperMetaStore;
import com.phonepe.solus.util.CacheUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Optional;

@Slf4j
public class DeDuperCrudCommands implements IDeDuperCrudCommands {
    private final IDeDuperMetaStore deDuperMetaStore;
    private final AsyncLoadingCache<String, Optional<DeDuper>> deDupersCache;

    public DeDuperCrudCommands(final StorageContext storageContext,
                               final String clientId) {
        this.deDuperMetaStore = buildDeDuperMetaStore(storageContext, clientId);
        this.deDupersCache = buildCache();
    }

    @Override
    public void register(final String deDuperName, final DeDuperConfig deDuperConfig) {
        final Optional<DeDuper> deDuper = deDuperMetaStore.get(deDuperName);
        if (deDuper.isPresent()) {
            if (!deDuper.get().getDeDuperConfig().isEqual(deDuperConfig)) {
                throw SolusException.builder()
                        .message("DeDuper is already registered with a different config.")
                        .errorCode(ErrorCode.DEDUPER_CONFIG_MISMATCH)
                        .build();
            }
            log.info("DeDuper is already registered. Gracefully ignoring.");
            return;
        }
        deDuperMetaStore.store(deDuperName, deDuperConfig);
    }

    @Override
    public void unregister(final String deDuperName) {
        deDuperMetaStore.updateStatus(deDuperName, false);
    }

    @Override
    public Map<String, DeDuper> getActiveDeDupers() {
        return deDuperMetaStore.getAllActive();
    }

    @Override
    public DeDuper getDeDuper(final String deDuperName) {
        return deDuperMetaStore.get(deDuperName)
                .orElseThrow(() -> SolusException.builder()
                        .errorCode(ErrorCode.DEDUPER_NOT_FOUND)
                        .build()
                );
    }

    @Override
    public DeDuper getCachedDeDuper(final String deDuperName) {
        try {
            return deDupersCache.get(deDuperName).get()
                    .orElseThrow(() -> SolusException.builder()
                            .errorCode(ErrorCode.DEDUPER_NOT_FOUND)
                            .build()
                    );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw SolusException.propagate(ErrorCode.CACHE_ERROR, e);
        } catch (Exception e) {
            throw SolusException.propagate(ErrorCode.CACHE_ERROR, e);
        }
    }

    private IDeDuperMetaStore buildDeDuperMetaStore(final StorageContext storageContext, final String clientId) {
        return storageContext.accept(new StorageContext.Visitor<>() {
            @Override
            public IDeDuperMetaStore visit(AerospikeStorageContext storageContext) {
                return new AerospikeDeDuperMetaStore(
                        storageContext.getAerospikeClient(),
                        storageContext.getNamespace(),
                        storageContext.getFarm(),
                        clientId
                );
            }

            @Override
            public IDeDuperMetaStore visit(HBaseStorageContext storageContext) {
                return new HBaseDeDuperMetaStore(clientId, storageContext.getConnection());
            }
        });
    }

    private AsyncLoadingCache<String, Optional<DeDuper>> buildCache() {
        return CacheUtils.buildCache(key -> {
            log.info("Loading deDuper cache for key {}", key);
            return deDuperMetaStore.get(key);
        });
    }
}
