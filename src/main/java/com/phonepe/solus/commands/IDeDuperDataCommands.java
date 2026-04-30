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

package com.phonepe.solus.commands;

import com.phonepe.solus.DeDuper;

import java.util.Map;
import java.util.Set;

public interface IDeDuperDataCommands<T> {
    /**
     * Checks the absence of a single entity within the DeDuper.
     *
     * @param deDuper The DeDuper instance to perform the check on.
     * @param entity  The entity to check for absence.
     * @return True if the entity is absent, false otherwise.
     */
    boolean checkAbsence(DeDuper deDuper, T entity);

    /**
     * Checks the absence of multiple entities within the DeDuper.
     *
     * @param deDuper  The DeDuper instance to perform the checks on.
     * @param entities A set of entities to check for absence.
     * @return A map of entities to their absence status (true if absent, false if present).
     */
    Map<T, Boolean> checkAbsence(DeDuper deDuper, Set<T> entities);

    /**
     * Adds an entity to the DeDuper with a specified time-to-live (TTL).
     *
     * @param deDuper The DeDuper instance to add the entity to.
     * @param entity  The entity to add.
     * @param ttl     The time-to-live (TTL) for the entity in milliseconds.
     */
    void add(DeDuper deDuper, T entity, long ttl);

    /**
     * Adds multiple entities to the DeDuper with a specified time-to-live (TTL).
     *
     * @param deDuper  The DeDuper instance to add the entities to.
     * @param entities A set of entities to add.
     * @param ttl      The time-to-live (TTL) for the entities in milliseconds.
     */
    void add(DeDuper deDuper, Set<T> entities, long ttl);

    /**
     * Adds an entity to the DeDuper if it is absent, with a specified time-to-live (TTL).
     *
     * @param deDuper The DeDuper instance to add the entity to.
     * @param entity  The entity to add if absent.
     * @param ttl     The time-to-live (TTL) for the entity in milliseconds.
     * @return True if the entity was absent, false otherwise.
     */
    boolean addIfAbsent(DeDuper deDuper, T entity, long ttl);

    /**
     * Adds multiple entities to the DeDuper if they are absent, with a specified time-to-live (TTL).
     *
     * @param deDuper  The DeDuper instance to add the entities to.
     * @param entities A set of entities to add if absent.
     * @param ttl      The time-to-live (TTL) for the entities in milliseconds.
     * @return A map of entities to their addition status (true if added, false if already present).
     */
    Map<T, Boolean> addIfAbsent(DeDuper deDuper, Set<T> entities, long ttl);
}
