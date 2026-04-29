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

package com.phonepe.solus.hbase.commands;

import com.google.common.collect.Maps;
import com.phonepe.solus.hbase.HBaseTableConnection;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Singular;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Builder;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.filter.Filter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.stream.Collectors;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class HBaseGetCommand extends GenericHBaseCommand<Map<HBaseGetCommand.ColumnInfo, byte[]>> {
    private final byte[] rowkey;
    @Singular
    private final List<ColumnInfo> columnInfos;
    private final Filter filter;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ColumnInfo {
        private byte[] family;
        private byte[] qualifier;
    }

    @Builder
    public HBaseGetCommand(final byte[] rowkey,
                           final List<ColumnInfo> columnInfos,
                           final Filter filter,
                           final HBaseTableConnection hBaseConnection,
                           final String table) {
        super(hBaseConnection, table);
        this.rowkey = rowkey;
        this.columnInfos = columnInfos;
        this.filter = filter;
    }

    public Map<ColumnInfo, byte[]> execute() throws IOException {
        try (Table htable = connection.getTable(table)) {
            final Get get = new Get(rowkey);
            columnInfos.forEach(columnInfo -> {
                if (Objects.nonNull(columnInfo.getFamily())) {
                    if (Objects.nonNull(columnInfo.getQualifier())) {
                        get.addColumn(columnInfo.getFamily(), columnInfo.getQualifier());
                    } else {
                        get.addFamily(columnInfo.getFamily());
                    }
                }
            });
            if (Objects.nonNull(filter)) {
                get.setFilter(filter);
            }
            final Result result = htable.get(get);

            final Map<ColumnInfo, byte[]> map = Maps.newHashMap();
            if (Objects.nonNull(result) && !result.isEmpty()) {
                final NavigableMap<byte[], NavigableMap<byte[], byte[]>> familyMaps = result.getNoVersionMap();
                familyMaps.forEach((family, familyMap) ->
                        familyMap.forEach((qualifier, latestValue) ->
                                map.put(new ColumnInfo(family, qualifier), latestValue)
                        ));
            }
            return map.entrySet()
                    .stream()
                    .filter(entry -> entry.getValue().length > 0)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }
    }
}

