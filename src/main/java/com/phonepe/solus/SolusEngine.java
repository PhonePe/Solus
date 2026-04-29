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


import com.phonepe.solus.commands.IDeDuperCrudCommands;
import com.phonepe.solus.commands.IDeDuperDataCommands;
import com.phonepe.solus.commands.impl.DeDuperCrudCommands;
import com.phonepe.solus.commands.impl.DeDuperDataCommands;
import com.phonepe.solus.config.DeDuperConfig;
import com.phonepe.solus.exception.ErrorCode;
import com.phonepe.solus.exception.SolusException;
import com.phonepe.solus.store.context.StorageContext;
import com.phonepe.solus.util.CommonUtils;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import javax.validation.Validator;
import java.util.Map;
import java.util.Set;

@Slf4j
public class SolusEngine<T> {
    private final Validator validator;
    private final IDeDuperCrudCommands deDuperCrudCommands;
    private final IDeDuperDataCommands<T> deDuperDataCommands;

    @Builder
    public SolusEngine(final Validator validator,
                       final String clientId,
                       final StorageContext storageContext) {
        this.validator = validator;
        this.deDuperCrudCommands = new DeDuperCrudCommands(storageContext, clientId);
        this.deDuperDataCommands = new DeDuperDataCommands<>(storageContext, clientId);
    }

    /**
     * Registers a DeDuper with the provided name using default configuration.
     * If no configuration is provided, default values are used.
     *
     * @param name The name to identify the DeDuper.
     */
    public void register(final String name) {
        log.info("No DeDuper config provided. Registering with default config. noOfHashFunctions: {}, noOfShards {}, bitsPerShard: {}",
                DeDuperConfig.MIN_NUMBER_OF_HASH_FUNCTION, DeDuperConfig.MIN_NUMBER_OF_SHARDS, DeDuperConfig.MIN_BITS_PER_SHARD);
        register(name, DeDuperConfig.builder().build());
    }

    /**
     * Registers a DeDuper with the provided name and configuration.
     *
     * @param deDuperName   The name to identify the DeDuper.
     * @param deDuperConfig The configuration for the DeDuper.
     */
    public void register(final String deDuperName, final DeDuperConfig deDuperConfig) {
        CommonUtils.validate(validator, deDuperConfig);
        deDuperCrudCommands.register(deDuperName, deDuperConfig);
    }

    /**
     * Unregisters a DeDuper with the given name.
     *
     * @param deDuperName The name of the DeDuper to unregister.
     */
    public void unregister(final String deDuperName) {
        getDeDuper(deDuperName); // To validate if deduper is present or not
        deDuperCrudCommands.unregister(deDuperName);
    }

    /**
     * Retrieves a DeDuper instance by its name.
     *
     * @param deDuperName The name of the DeDuper to retrieve.
     * @return The DeDuper instance.
     * @throws SolusException with {@link ErrorCode#DEDUPER_NOT_FOUND} if not found
     */
    public DeDuper getDeDuper(final String deDuperName) {
        return deDuperCrudCommands.getDeDuper(deDuperName);
    }

    /**
     * Retrieves a cached DeDuper instance by its name.
     *
     * @param deDuperName The name of the DeDuper to retrieve.
     * @return The cached DeDuper instance, or null if not found.
     * @throws SolusException with {@link ErrorCode#DEDUPER_NOT_FOUND} if not found
     */
    public DeDuper getCachedDeDuper(final String deDuperName) {
        return deDuperCrudCommands.getCachedDeDuper(deDuperName);
    }

    /**
     * Retrieves a map of active DeDuper instances, indexed by their names.
     *
     * @return A map containing active DeDuper instances.
     */
    public Map<String, DeDuper> getActiveDeDupers() {
        return deDuperCrudCommands.getActiveDeDupers();
    }

    /**
     * Checks the absence of a single entity within the DeDuper.
     *
     * @param deDuperName The name of the DeDuper to use.
     * @param entity      The entity to check for absence.
     * @return True if the entity is absent, false otherwise.
     */
    public boolean checkAbsence(final String deDuperName, final T entity) {
        final DeDuper deDuper = getCachedDeDuper(deDuperName);
        return deDuperDataCommands.checkAbsence(deDuper, entity);
    }

    /**
     * Checks the absence of multiple entities within the DeDuper.
     *
     * @param deDuperName The name of the DeDuper to use.
     * @param entities    A set of entities to check for absence.
     * @return A map of entities to their absence status (true if absent, false if present).
     */
    public Map<T, Boolean> checkAbsence(final String deDuperName, final Set<T> entities) {
        final DeDuper deDuper = getCachedDeDuper(deDuperName);
        return deDuperDataCommands.checkAbsence(deDuper, entities);
    }

    /**
     * Adds an entity to the DeDuper with a specified time-to-live (TTL).
     *
     * @param deDuperName The name of the DeDuper to use.
     * @param entity      The entity to add.
     * @param ttlInMs     The time-to-live (TTL) for the entity in milliseconds.
     */
    public void add(final String deDuperName, final T entity, final long ttlInMs) {
        final DeDuper deDuper = getCachedDeDuper(deDuperName);
        deDuperDataCommands.add(deDuper, entity, ttlInMs);
    }

    /**
     * Adds multiple entities to the DeDuper with a specified time-to-live (TTL).
     *
     * @param deDuperName The name of the DeDuper to use.
     * @param entities    A set of entities to add.
     * @param ttlInMs     The time-to-live (TTL) for the entities in milliseconds.
     */
    public void add(final String deDuperName, final Set<T> entities, final long ttlInMs) {
        final DeDuper deDuper = getCachedDeDuper(deDuperName);
        deDuperDataCommands.add(deDuper, entities, ttlInMs);
    }

    /**
     * Adds an entity to the DeDuper if it is absent, with a specified time-to-live (TTL).
     *
     * @param deDuperName The name of the DeDuper to use.
     * @param entity      The entity to add if absent.
     * @param ttlInMs     The time-to-live (TTL) for the entity in milliseconds.
     * @return True if the entity was added, false if it was already present.
     */
    public boolean addIfAbsent(final String deDuperName, final T entity, final long ttlInMs) {
        final DeDuper deDuper = getCachedDeDuper(deDuperName);
        return deDuperDataCommands.addIfAbsent(deDuper, entity, ttlInMs);
    }

    /**
     * Adds multiple entities to the DeDuper if they are absent, with a specified time-to-live (TTL).
     *
     * @param deDuperName The name of the DeDuper to use.
     * @param entities    A set of entities to add if absent.
     * @param ttlInMs     The time-to-live (TTL) for the entities in milliseconds.
     * @return A map of entities to their addition status (true if added, false if already present).
     */
    public Map<T, Boolean> addIfAbsent(final String deDuperName, final Set<T> entities, final long ttlInMs) {
        final DeDuper deDuper = getCachedDeDuper(deDuperName);
        return deDuperDataCommands.addIfAbsent(deDuper, entities, ttlInMs);
    }
}
