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
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Builder;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;

import java.io.IOException;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class HBaseScanCommand extends GenericHBaseCommand<List<Result>> {
    private final Scan scan;

    @Builder
    public HBaseScanCommand(final Scan scan,
                            final HBaseTableConnection hBaseConnection,
                            final String table) {
        super(hBaseConnection, table);
        this.scan = scan;
    }

    @Override
    public List<Result> execute() throws IOException {
        try (Table htable = getConnection().getTable(getTable())) {
            final ResultScanner scanner = htable.getScanner(scan);
            return StreamSupport.stream(Spliterators.spliteratorUnknownSize(scanner.iterator(),
                            Spliterator.ORDERED), false)
                    .filter(Objects::nonNull)
                    .filter(Predicate.not(Result::isEmpty))
                    .collect(Collectors.toList());
        }
    }
}
