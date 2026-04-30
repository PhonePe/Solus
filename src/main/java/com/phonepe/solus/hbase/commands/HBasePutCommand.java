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

package com.phonepe.solus.hbase.commands;

import com.phonepe.solus.hbase.HBaseTableConnection;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Table;

import java.io.IOException;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class HBasePutCommand extends GenericHBaseCommand<Void> {

    private final Put put;

    @Builder
    public HBasePutCommand(final Put put,
                           final HBaseTableConnection hBaseConnection,
                           final String table) {
        super(hBaseConnection, table);
        this.put = put;
    }

    @Override
    public Void execute() throws IOException {
        try (Table htable = connection.getTable(table)) {
            htable.put(put);
        }
        return null;
    }
}
