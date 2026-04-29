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

import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import lombok.Getter;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.BufferedMutator;
import org.apache.hadoop.hbase.client.BufferedMutatorParams;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.RegionLocator;
import org.apache.hadoop.hbase.client.TableBuilder;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class MockHBaseConnection implements Connection, Serializable {

    @Getter
    private final Map<String, MockHBaseTable> tables = new HashMap<>();

    @Mock
    private Admin admin;

    public MockHBaseConnection() {
        super();
        MockitoAnnotations.openMocks(this);
        try {
            Mockito.doNothing().when(admin).createTable(ArgumentMatchers.any(), ArgumentMatchers.any());
        } catch (IOException e) {
            //Do Nothing
        }
    }

    @Override
    public Configuration getConfiguration() {
        return null;
    }

    @Override
    public MockHBaseTable getTable(TableName tableName) {
        String name = tableName.getNameAsString();
        return tables.computeIfAbsent(name, MockHBaseTable::new);
    }

    @Override
    public MockHBaseTable getTable(TableName tableName, ExecutorService pool) {
        return getTable(tableName);
    }

    public MockHBaseTable getTable(String tableName) {
        return tables.get(tableName);
    }

    @Override
    public BufferedMutator getBufferedMutator(TableName tableName) {
        throw new RuntimeException(this.getClass() + " does NOT implement this method.");
    }

    @Override
    public BufferedMutator getBufferedMutator(BufferedMutatorParams params) {
        throw new RuntimeException(this.getClass() + " does NOT implement this method.");
    }

    @Override
    public RegionLocator getRegionLocator(TableName tableName) {
        throw new RuntimeException(this.getClass() + " does NOT implement this method.");
    }

    @Override
    public Admin getAdmin() {
        return admin;
    }

    @Override
    public void close() {
    }

    @Override
    public boolean isClosed() {
        return false;
    }

    @Override
    public TableBuilder getTableBuilder(TableName tableName, ExecutorService executorService) {
        return null;
    }

    @Override
    public void abort(String why, Throwable e) {
    }

    @Override
    public boolean isAborted() {
        return false;
    }
}
