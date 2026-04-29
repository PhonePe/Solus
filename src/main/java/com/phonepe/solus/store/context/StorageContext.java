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

package com.phonepe.solus.store.context;

import com.phonepe.solus.store.context.impl.AerospikeStorageContext;
import com.phonepe.solus.store.context.impl.HBaseStorageContext;
import lombok.AllArgsConstructor;
import lombok.Getter;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Getter
@AllArgsConstructor
public abstract class StorageContext {
    @NotNull
    private final StorageType storageType;
    @NotBlank
    private final String farm;

    public abstract <T> T accept(Visitor<T> visitor);

    public interface Visitor<T> {
        T visit(AerospikeStorageContext storageContext);

        T visit(HBaseStorageContext storageContext);
    }
}