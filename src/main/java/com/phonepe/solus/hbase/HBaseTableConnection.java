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

package com.phonepe.solus.hbase;

import java.io.IOException;

import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.client.TableDescriptor;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;
import org.apache.hadoop.hbase.io.compress.Compression;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.security.UserGroupInformation;

import com.phonepe.solus.exception.ErrorCode;
import com.phonepe.solus.exception.SolusException;

public class HBaseTableConnection {

    private final boolean secure;
    private final Connection connection;

    public HBaseTableConnection(final boolean secure,
                                final Connection connection) {
        this.secure = secure;
        this.connection = connection;
    }

    public Table getTable(final String tableName) throws IOException {
        if (secure && UserGroupInformation.isSecurityEnabled()) {
            UserGroupInformation.getCurrentUser()
                    .reloginFromKeytab();
        }
        return connection.getTable(TableName.valueOf(tableName));
    }

    public void ensureTableExists(final String tableName,
                                  final String columnFamily,
                                  final byte[][] splitKeys) {
        final TableDescriptor tableDescriptor = TableDescriptorBuilder.newBuilder(TableName.valueOf(tableName))
                .setColumnFamily(
                        ColumnFamilyDescriptorBuilder.newBuilder(Bytes.toBytes(columnFamily))
                                .setCompressionType(Compression.Algorithm.GZ)
                                .setMaxVersions(1)
                                .build())
                .build();
        try {
            if (!connection.getAdmin()
                    .tableExists(TableName.valueOf(Bytes.toBytes(tableName)))) {
                connection.getAdmin()
                        .createTable(tableDescriptor, splitKeys);
            }
        } catch (IOException e) {
            throw SolusException.builder()
                    .cause(e)
                    .errorCode(ErrorCode.TABLE_CREATION_ERROR)
                    .message(String.format("Could not create table: %s", tableName))
                    .build();
        }
    }

}
