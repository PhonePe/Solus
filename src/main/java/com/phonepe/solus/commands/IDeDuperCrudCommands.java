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

package com.phonepe.solus.commands;

import com.phonepe.solus.DeDuper;
import com.phonepe.solus.config.DeDuperConfig;
import com.phonepe.solus.exception.ErrorCode;
import com.phonepe.solus.exception.SolusException;

import java.util.Map;

public interface IDeDuperCrudCommands {
    /**
     * Registers a DeDuper with the given name and configuration.
     *
     * @param deDuperName   The name to identify the DeDuper.
     * @param deDuperConfig The configuration for the DeDuper.
     */
    void register(String deDuperName, DeDuperConfig deDuperConfig);

    /**
     * Unregisters a DeDuper with the given name.
     *
     * @param deDuperName The name of the DeDuper to unregister.
     */
    void unregister(String deDuperName);

    /**
     * Retrieves a map of active DeDuper instances, indexed by their names.
     *
     * @return A map containing active DeDuper instances.
     */
    Map<String, DeDuper> getActiveDeDupers();

    /**
     * Retrieves the DeDuper instance by its name.
     *
     * @param deDuperName The name of the DeDuper to retrieve.
     * @return The DeDuper instance
     * @throws SolusException with {@link ErrorCode#DEDUPER_NOT_FOUND} if not found
     */
    DeDuper getDeDuper(String deDuperName);

    /**
     * Retrieves the DeDuper instance by its name from local cache
     *
     * @param deDuperName The name of the DeDuper to retrieve.
     * @return The DeDuper instance from local cache
     * @throws SolusException with {@link ErrorCode#DEDUPER_NOT_FOUND} if not found
     */
    DeDuper getCachedDeDuper(String deDuperName);
}
