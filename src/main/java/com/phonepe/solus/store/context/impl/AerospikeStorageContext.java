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

package com.phonepe.solus.store.context.impl;

import com.aerospike.client.IAerospikeClient;
import com.phonepe.solus.store.context.StorageContext;
import com.phonepe.solus.store.context.StorageType;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AerospikeStorageContext extends StorageContext {
    @NotNull
    @Valid
    private final IAerospikeClient aerospikeClient;
    @NotBlank
    private final String namespace;
    @NotBlank
    private final String setName;

    @Builder
    public AerospikeStorageContext(final IAerospikeClient aerospikeClient,
                                   final String farm,
                                   final String namespace,
                                   final String setName) {
        super(StorageType.AEROSPIKE, farm);
        this.aerospikeClient = aerospikeClient;
        this.namespace = namespace;
        this.setName = setName;
    }

    @Override
    public <T> T accept(Visitor<T> visitor) {
        return visitor.visit(this);
    }
}
