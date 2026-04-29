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

package com.phonepe.solus.store;

import com.phonepe.solus.DeDuper;
import com.phonepe.solus.config.DeDuperConfig;

import java.util.Map;
import java.util.Optional;

public interface IDeDuperMetaStore {

    /**
     * Stores the configuration for a deduper.
     *
     * @param deDuperName   name of the deduper
     * @param deDuperConfig configuration to persist
     */
    void store(String deDuperName, DeDuperConfig deDuperConfig);

    /**
     * Updates the active/inactive status of a deduper.
     *
     * @param deDuperName name of the deduper
     * @param status      {@code true} to activate, {@code false} to deactivate
     */
    void updateStatus(String deDuperName, boolean status);

    /**
     * Retrieves all currently active dedupers.
     *
     * @return map of deduper name to its {@link DeDuper} instance
     */
    Map<String, DeDuper> getAllActive();

    /**
     * Retrieves a deduper by name.
     *
     * @param deDuperName name of the deduper
     * @return an {@link Optional} containing the deduper if found, or empty otherwise
     */
    Optional<DeDuper> get(String deDuperName);
}
