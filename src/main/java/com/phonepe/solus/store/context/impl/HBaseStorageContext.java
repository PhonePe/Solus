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

import com.phonepe.solus.filter.impl.hbase.HBaseBloomFilterUtils;
import com.phonepe.solus.hbase.HBaseTableConnection;
import com.phonepe.solus.store.context.StorageContext;
import com.phonepe.solus.store.context.StorageType;
import com.phonepe.solus.util.Constants;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class HBaseStorageContext extends StorageContext {
    @NotBlank
    private final String tableName;
    @NotNull
    @Valid
    private final HBaseTableConnection connection;

    @Builder
    public HBaseStorageContext(final HBaseTableConnection connection,
                               final String farm,
                               final String tableName) {
        super(StorageType.HBASE, farm);
        this.connection = connection;
        this.tableName = tableName;
        connection.ensureTableExists(tableName, Constants.HBASE_COLUMN_FAMILY_NAME,
                HBaseBloomFilterUtils.ONE_BYTE_HASHER.getAllPossiblePrefixes());
    }

    @Override
    public <T> T accept(Visitor<T> visitor) {
        return visitor.visit(this);
    }

}
