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
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.Arrays;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import lombok.Getter;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellComparator;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.CompareOperator;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Append;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Durability;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Increment;
import org.apache.hadoop.hbase.client.Mutation;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Row;
import org.apache.hadoop.hbase.client.RowMutations;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.client.TableDescriptor;
import org.apache.hadoop.hbase.client.coprocessor.Batch;
import org.apache.hadoop.hbase.client.metrics.ScanMetrics;
import org.apache.hadoop.hbase.filter.CompareFilter;
import org.apache.hadoop.hbase.filter.Filter;
import org.apache.hadoop.hbase.ipc.CoprocessorRpcChannel;
import org.apache.hadoop.hbase.util.Bytes;

public class MockHBaseTable implements Table {
    private final String tableName;
    private final List<String> columnFamilies = new ArrayList<>();

    @Getter
    private final NavigableMap<byte[], NavigableMap<byte[], NavigableMap<byte[], NavigableMap<Long, byte[]>>>> data
            = new TreeMap<>(Bytes.BYTES_COMPARATOR);

    private static List<Cell> toCell(byte[] row, NavigableMap<byte[], NavigableMap<byte[], NavigableMap<Long, byte[]>>> rowdata, int maxVersions) {
        return toCell(row, rowdata, 0, Long.MAX_VALUE, maxVersions);
    }

    public MockHBaseTable(String tableName) {
        this.tableName = tableName;
    }

    @Override
    public TableName getName() {
        return TableName.valueOf(tableName);
    }

    @Override
    public Configuration getConfiguration() {
        throw new RuntimeException(this.getClass() + " does NOT implement this method.");
    }

    @Override
    public HTableDescriptor getTableDescriptor() {
        final HTableDescriptor table = new HTableDescriptor(getName());
        for (String columnFamily : columnFamilies) {
            table.addFamily(new HColumnDescriptor(columnFamily));
        }
        return table;
    }

    @Override
    public TableDescriptor getDescriptor() {
        return null;
    }

    @Override
    public void mutateRow(RowMutations rm) {
        // currently only support Put and Delete
        for (Mutation mutation : rm.getMutations()) {
            if (mutation instanceof Put) {
                put((Put) mutation);
            } else if (mutation instanceof Delete) {
                delete((Delete) mutation);
            }
        }
    }

    @Override
    public Result append(Append append) {
        throw new RuntimeException(this.getClass() + " does NOT implement this method.");
    }

    private static List<Cell> toCell(byte[] row, NavigableMap<byte[], NavigableMap<byte[], NavigableMap<Long, byte[]>>> rowdata, long timestampStart, long timestampEnd, int maxVersions) {
        final List<Cell> ret = new ArrayList<>();
        final byte putType = KeyValue.Type.Put.getCode();
        for (byte[] family : rowdata.keySet())
            for (byte[] qualifier : rowdata.get(family).keySet()) {
                int versionsAdded = 0;
                for (Map.Entry<Long, byte[]> tsToVal : rowdata.get(family).get(qualifier).descendingMap().entrySet()) {
                    if (versionsAdded++ == maxVersions)
                        break;
                    Long timestamp = tsToVal.getKey();
                    if (timestamp < timestampStart)
                        continue;
                    if (timestamp > timestampEnd)
                        continue;
                    byte[] value = tsToVal.getValue();
                    ret.add(CellUtil.createCell(row, family, qualifier, timestamp, putType, value));
                }
            }
        return ret;
    }

    @Override
    public boolean exists(Get get) throws IOException {
        final Result result = get(get);
        return Objects.nonNull(result) && !result.isEmpty();
    }

    @Override
    public boolean[] exists(List<Get> list) {
        return new boolean[0];
    }

    @Override
    public boolean[] existsAll(List<Get> gets) throws IOException {
        final boolean[] result = new boolean[gets.size()];
        for (int i = 0; i < gets.size(); i++) {
            result[i] = exists(gets.get(i));
        }
        return result;
    }

    @Override
    public void batch(List<? extends Row> list, Object[] objects) {
    }

    @Override
    public <R> void batchCallback(List<? extends Row> actions, Object[] results, Batch.Callback<R> callback) {
        throw new RuntimeException(this.getClass() + " does NOT implement this method.");
    }

    @Override
    public Result get(Get get) throws IOException {
        if (!data.containsKey(get.getRow()))
            return new Result();
        byte[] row = get.getRow();
        List<Cell> cells = new ArrayList<>();
        if (!get.hasFamilies()) {
            cells = toCell(row, data.get(row), get.getMaxVersions());
        } else {
            for (byte[] family : get.getFamilyMap().keySet()) {
                if (data.get(row).get(family) == null)
                    continue;
                NavigableSet<byte[]> qualifiers = get.getFamilyMap().get(family);
                if (qualifiers == null || qualifiers.isEmpty())
                    qualifiers = data.get(row).get(family).navigableKeySet();
                for (byte[] qualifier : qualifiers) {
                    if (qualifier == null)
                        qualifier = "".getBytes();
                    if (!data.get(row).containsKey(family) ||
                            !data.get(row).get(family).containsKey(qualifier) ||
                            data.get(row).get(family).get(qualifier).isEmpty())
                        continue;
                    Map.Entry<Long, byte[]> timestampAndValue = data.get(row).get(family).get(qualifier).lastEntry();
                    cells.add(new KeyValue(row, family, qualifier, timestampAndValue.getKey(), timestampAndValue.getValue()));
                }
            }
        }
        Filter filter = get.getFilter();
        if (Objects.nonNull(filter)) {
            cells = filter(filter, cells);
        }
        cells.sort(new CellComparator() {
            @Override
            public int compare(Cell cell, Cell cell1) {
                return 0;
            }

            @Override
            public boolean equals(Object obj) {
                return false;
            }

            @Override
            public int compare(Cell cell, Cell cell1, boolean b) {
                return 0;
            }

            @Override
            public int compareRows(Cell cell, Cell cell1) {
                return 0;
            }

            @Override
            public int compareRows(Cell cell, byte[] bytes, int i, int i1) {
                return 0;
            }

            @Override
            public int compareWithoutRow(Cell cell, Cell cell1) {
                return 0;
            }

            @Override
            public int compareFamilies(Cell cell, Cell cell1) {
                return 0;
            }

            @Override
            public int compareQualifiers(Cell cell, Cell cell1) {
                return 0;
            }

            @Override
            public int compareTimestamps(Cell cell, Cell cell1) {
                return 0;
            }

            @Override
            public int compareTimestamps(long l, long l1) {
                return 0;
            }

            @Override
            public Comparator getSimpleComparator() {
                return null;
            }
        });
        return Result.create(cells);
    }

    @Override
    public Result[] get(List<Get> gets) throws IOException {
        final List<Result> results = new ArrayList<>();
        for (Get g : gets) {
            results.add(get(g));
        }
        return results.toArray(new Result[0]);
    }

    @Override
    public ResultScanner getScanner(Scan scan) throws IOException {
        final List<Result> ret = new ArrayList<>();
        byte[] st = scan.getStartRow();
        byte[] sp = scan.getStopRow();

        for (byte[] row : data.keySet()) {
            // if row is equal to startRow emit it. When startRow (inclusive) and
            // stopRow (exclusive) is the same, it should not be excluded which would
            // happen w/o this control.
            if (Objects.nonNull(st) && st.length > 0 &&
                    Bytes.BYTES_COMPARATOR.compare(st, row) != 0) {
                // if row is before startRow do not emit, pass to next row
                if (Bytes.BYTES_COMPARATOR.compare(st, row) > 0)
                    continue;
                // if row is equal to stopRow, or after it do not emit, stop iteration
                if (Objects.nonNull(sp) && sp.length > 0 &&
                        Bytes.BYTES_COMPARATOR.compare(sp, row) < 0)
                    break;
            }

            List<Cell> kvs;
            if (!scan.hasFamilies()) {
                kvs = toCell(row, data.get(row), scan.getTimeRange().getMin(), scan.getTimeRange().getMax(), scan.getMaxVersions());
            } else {
                kvs = new ArrayList<>();
                for (byte[] family : scan.getFamilyMap().keySet()) {
                    if (data.get(row).get(family) == null)
                        continue;
                    NavigableSet<byte[]> qualifiers = scan.getFamilyMap().get(family);
                    if (qualifiers == null || qualifiers.isEmpty())
                        qualifiers = data.get(row).get(family).navigableKeySet();
                    for (byte[] qualifier : qualifiers) {
                        if (data.get(row).get(family).get(qualifier) == null)
                            continue;
                        for (Long timestamp : data.get(row).get(family).get(qualifier).descendingKeySet()) {
                            if (timestamp < scan.getTimeRange().getMin())
                                continue;
                            if (timestamp > scan.getTimeRange().getMax())
                                continue;
                            byte[] value = data.get(row).get(family).get(qualifier).get(timestamp);
                            kvs.add(new KeyValue(row, family, qualifier, timestamp, value));
                            if (kvs.size() == scan.getMaxVersions()) {
                                break;
                            }
                        }
                    }
                }
            }

            if (!kvs.isEmpty()) {
                kvs.sort(new CellComparator() {
                    @Override
                    public int compare(Cell cell, Cell cell1) {
                        return 0;
                    }

                    @Override
                    public boolean equals(Object obj) {
                        return false;
                    }

                    @Override
                    public int compare(Cell cell, Cell cell1, boolean b) {
                        return 0;
                    }

                    @Override
                    public int compareRows(Cell cell, Cell cell1) {
                        return 0;
                    }

                    @Override
                    public int compareRows(Cell cell, byte[] bytes, int i, int i1) {
                        return 0;
                    }

                    @Override
                    public int compareWithoutRow(Cell cell, Cell cell1) {
                        return 0;
                    }

                    @Override
                    public int compareFamilies(Cell cell, Cell cell1) {
                        return 0;
                    }

                    @Override
                    public int compareQualifiers(Cell cell, Cell cell1) {
                        return 0;
                    }

                    @Override
                    public int compareTimestamps(Cell cell, Cell cell1) {
                        return 0;
                    }

                    @Override
                    public int compareTimestamps(long l, long l1) {
                        return 0;
                    }

                    @Override
                    public Comparator getSimpleComparator() {
                        return null;
                    }
                });
                ret.add(Result.create(kvs));
            }
        }

        return new ResultScanner() {
            private final Iterator<Result> iterator = ret.iterator();

            public Iterator<Result> iterator() {
                return iterator;
            }

            public Result[] next(int nbRows) {
                final ArrayList<Result> resultSets = new ArrayList<>(nbRows);
                for (int i = 0; i < nbRows; i++) {
                    Result next = next();
                    if (Objects.nonNull(next)) {
                        resultSets.add(next);
                    } else {
                        break;
                    }
                }
                return resultSets.toArray(new Result[0]);
            }

            public Result next() {
                try {
                    return iterator().next();
                } catch (NoSuchElementException e) {
                    return null;
                }
            }

            public void close() {
            }

            @Override
            public boolean renewLease() {
                return false;
            }

            @Override
            public ScanMetrics getScanMetrics() {
                return null;
            }
        };
    }

    private List<Cell> filter(Filter filter, List<Cell> cells) throws IOException {
        filter.reset();

        final List<Cell> tmp = new ArrayList<>(cells.size());
        tmp.addAll(cells);

        /*
         * Note. Filter flow for a single row. Adapted from
         * "HBase: The Definitive Guide" (p. 163) by Lars George, 2011.
         * See Figure 4-2 on p. 163.
         */
        boolean filteredOnRowKey = false;
        final List<Cell> nkvs = new ArrayList<>(tmp.size());
        for (Cell cell : tmp) {
            if (filter.filterRowKey(cell.getRowArray(), cell.getRowOffset(), cell.getRowLength())) {
                filteredOnRowKey = true;
                break;
            }
            final Filter.ReturnCode filterResult = filter.filterKeyValue(cell);
            if (filterResult == Filter.ReturnCode.INCLUDE) {
                nkvs.add(cell);
            } else if (filterResult == Filter.ReturnCode.NEXT_ROW) {
                break;
            }
            /*
             * Ignoring next key hint which is an optimization to reduce file
             * system IO
             */
        }
        if (filter.hasFilterRow() && !filteredOnRowKey) {
            filter.filterRowCells(nkvs);
        }
        if (filter.filterRow() || filteredOnRowKey) {
            nkvs.clear();
        }
        return nkvs;
    }

    @Override
    public ResultScanner getScanner(byte[] family) throws IOException {
        final Scan scan = new Scan();
        scan.addFamily(family);
        return getScanner(scan);
    }

    @Override
    public ResultScanner getScanner(byte[] family, byte[] qualifier) throws IOException {
        final Scan scan = new Scan();
        scan.addColumn(family, qualifier);
        return getScanner(scan);
    }

    private <K, V> V forceFind(NavigableMap<K, V> map, K key, V newObject) {
        return map.computeIfAbsent(key, k -> newObject);
    }

    @Override
    public void put(Put put) {
        final byte[] row = put.getRow();
        final NavigableMap<byte[], NavigableMap<byte[], NavigableMap<Long, byte[]>>> rowData = forceFind(data, row, new TreeMap<>(Bytes.BYTES_COMPARATOR));
        for (byte[] family : put.getFamilyCellMap().keySet()) {
            final NavigableMap<byte[], NavigableMap<Long, byte[]>> familyData = forceFind(rowData, family, new TreeMap<>(Bytes.BYTES_COMPARATOR));
            for (Cell cell : put.getFamilyCellMap().get(family)) {
                final byte[] qualifier = CellUtil.cloneQualifier(cell);
                final NavigableMap<Long, byte[]> qualifierData = forceFind(familyData, qualifier, new TreeMap<>());
                qualifierData.put(cell.getTimestamp(), CellUtil.cloneValue(cell));
            }
        }
    }

    @Override
    public void put(List<Put> puts) {
        for (Put put : puts) {
            put(put);
        }

    }

    private boolean check(byte[] row, byte[] family, byte[] qualifier, byte[] value) {
        if (value == null || value.length == 0)
            return !data.containsKey(row) ||
                    !data.get(row).containsKey(family) ||
                    !data.get(row).get(family).containsKey(qualifier);
        else
            return data.containsKey(row) &&
                    data.get(row).containsKey(family) &&
                    data.get(row).get(family).containsKey(qualifier) &&
                    !data.get(row).get(family).get(qualifier).isEmpty() &&
                    Arrays.equals(data.get(row).get(family).get(qualifier).lastEntry().getValue(), value);
    }

    @Override
    public boolean checkAndPut(byte[] row, byte[] family, byte[] qualifier, byte[] value, Put put) {
        if (check(row, family, qualifier, value)) {
            put(put);
            return true;
        }
        return false;
    }

    @Override
    public boolean checkAndPut(byte[] row, byte[] family, byte[] qualifier, CompareFilter.CompareOp compareOp, byte[] value, Put put) {
        return false;
    }

    @Override
    public boolean checkAndPut(byte[] bytes, byte[] bytes1, byte[] bytes2, CompareOperator compareOperator, byte[] bytes3, Put put) {
        return false;
    }

    @Override
    public void delete(Delete delete) {
        final byte[] row = delete.getRow();
        final NavigableMap<byte[], List<Cell>> familyCellMap = delete.getFamilyCellMap();
        if (data.get(row) == null)
            return;
        if (familyCellMap.isEmpty()) {
            data.remove(row);
            return;
        }
        for (byte[] family : familyCellMap.keySet()) {
            if (data.get(row).get(family) == null)
                continue;
            if (familyCellMap.get(family).isEmpty()) {
                data.get(row).remove(family);
                continue;
            }
            for (Cell cell : familyCellMap.get(family)) {
                data.get(row).get(CellUtil.cloneFamily(cell)).remove(CellUtil.cloneQualifier(cell));
            }
            if (data.get(row).get(family).isEmpty()) {
                data.get(row).remove(family);
            }
        }
        if (data.get(row).isEmpty()) {
            data.remove(row);
        }
    }

    @Override
    public void delete(List<Delete> deletes) {
        for (Delete delete : deletes) {
            delete(delete);
        }
    }

    @Override
    public boolean checkAndDelete(byte[] row, byte[] family, byte[] qualifier, byte[] value, Delete delete) {
        if (check(row, family, qualifier, value)) {
            delete(delete);
            return true;
        }
        return false;
    }

    @Override
    public boolean checkAndDelete(byte[] row, byte[] family, byte[] qualifier, CompareFilter.CompareOp compareOp, byte[] value, Delete delete) {
        throw new RuntimeException(this.getClass() + " does NOT implement this method.");
    }

    @Override
    public boolean checkAndDelete(byte[] bytes, byte[] bytes1, byte[] bytes2, CompareOperator compareOperator, byte[] bytes3, Delete delete) {
        return false;
    }

    @Override
    public CheckAndMutateBuilder checkAndMutate(byte[] bytes, byte[] bytes1) {
        return null;
    }


    @Override
    public Result increment(Increment increment) {
        final List<Cell> cells = new ArrayList<>();
        final Map<byte[], NavigableMap<byte[], Long>> famToVal = increment.getFamilyMapOfLongs();
        final NavigableMap<byte[], NavigableMap<byte[], NavigableMap<Long, byte[]>>> rowData = forceFind(data, increment.getRow(), new TreeMap<>(Bytes.BYTES_COMPARATOR));
        for (Map.Entry<byte[], NavigableMap<byte[], Long>> ef : famToVal.entrySet()) {
            byte[] family = ef.getKey();
            final NavigableMap<byte[], Long> qToVal = ef.getValue();
            final NavigableMap<byte[], NavigableMap<Long, byte[]>> familyData = forceFind(rowData, family, new TreeMap<>(Bytes.BYTES_COMPARATOR));
            for (Map.Entry<byte[], Long> eq : qToVal.entrySet()) {
                final long newValue = incrementColumnValue(increment.getRow(), family, eq.getKey(), eq.getValue());
                final Cell cell = CellUtil.createCell(increment.getRow(), family, eq.getKey(), System.currentTimeMillis(), KeyValue.Type.Put.getCode(), Bytes.toBytes(newValue));
                cells.add(cell);
                final byte[] qualifier = CellUtil.cloneQualifier(cell);
                final NavigableMap<Long, byte[]> qualifierData = forceFind(familyData, qualifier, new TreeMap<>());
                qualifierData.put(cell.getTimestamp(), CellUtil.cloneValue(cell));
            }
        }
        return Result.create(cells);
    }

    @Override
    public long incrementColumnValue(byte[] row, byte[] family, byte[] qualifier, long amount) {
        return incrementColumnValue(row, family, qualifier, amount, Durability.USE_DEFAULT);
    }

    @Override
    public long incrementColumnValue(byte[] row, byte[] family, byte[] qualifier, long amount, Durability durability) {
        try {
            final Get get = new Get(row);
            get(get);
            if (get(get).getValue(family, qualifier) == null) {
                return amount;
            } else {
                return Bytes.toLong(get(get).getValue(family, qualifier)) + amount;
            }
        } catch (Exception e) {
            // consume silently
            return amount;
        }

    }

    @Override
    public void close() {

    }

    @Override
    public CoprocessorRpcChannel coprocessorService(byte[] row) {
        throw new RuntimeException(this.getClass() + " does NOT implement this method.");
    }

    @Override
    public <T extends org.apache.hadoop.hbase.shaded.com.google.protobuf.Service, R> Map<byte[], R> coprocessorService(Class<T> aClass, byte[] bytes, byte[] bytes1, Batch.Call<T, R> call) {
        return null;
    }

    @Override
    public <T extends org.apache.hadoop.hbase.shaded.com.google.protobuf.Service, R> void coprocessorService(Class<T> aClass, byte[] bytes, byte[] bytes1, Batch.Call<T, R> call, Batch.Callback<R> callback) {

    }

    @Override
    public <R extends org.apache.hadoop.hbase.shaded.com.google.protobuf.Message> Map<byte[], R> batchCoprocessorService(org.apache.hadoop.hbase.shaded.com.google.protobuf.Descriptors.MethodDescriptor methodDescriptor, org.apache.hadoop.hbase.shaded.com.google.protobuf.Message message, byte[] bytes, byte[] bytes1, R r) {
        return null;
    }

    @Override
    public <R extends org.apache.hadoop.hbase.shaded.com.google.protobuf.Message> void batchCoprocessorService(org.apache.hadoop.hbase.shaded.com.google.protobuf.Descriptors.MethodDescriptor methodDescriptor, org.apache.hadoop.hbase.shaded.com.google.protobuf.Message message, byte[] bytes, byte[] bytes1, R r, Batch.Callback<R> callback) {

    }

    @Override
    public boolean checkAndMutate(byte[] row, byte[] family, byte[] qualifier, CompareFilter.CompareOp compareOp, byte[] value, RowMutations mutation) {
        throw new RuntimeException(this.getClass() + " does NOT implement this method.");
    }

    @Override
    public boolean checkAndMutate(byte[] bytes, byte[] bytes1, byte[] bytes2, CompareOperator compareOperator, byte[] bytes3, RowMutations rowMutations) {
        return false;
    }

    @Override
    public long getRpcTimeout(TimeUnit timeUnit) {
        return 0;
    }

    @Override
    public void setOperationTimeout(int operationTimeout) {
        throw new RuntimeException(this.getClass() + " does NOT implement this method.");
    }

    @Override
    public int getOperationTimeout() {
        throw new RuntimeException(this.getClass() + " does NOT implement this method.");
    }

    @Override
    public void setRpcTimeout(int rpcTimeout) {
        throw new RuntimeException(this.getClass() + " does NOT implement this method.");
    }

    @Override
    public long getReadRpcTimeout(TimeUnit timeUnit) {
        return 0;
    }

    @Override
    public int getReadRpcTimeout() {
        return 0;
    }

    @Override
    public void setReadRpcTimeout(int i) {

    }

    @Override
    public long getWriteRpcTimeout(TimeUnit timeUnit) {
        return 0;
    }

    @Override
    public int getWriteRpcTimeout() {
        return 0;
    }

    @Override
    public void setWriteRpcTimeout(int i) {

    }

    @Override
    public long getOperationTimeout(TimeUnit timeUnit) {
        return 0;
    }

    @Override
    public int getRpcTimeout() {
        throw new RuntimeException(this.getClass() + " does NOT implement this method.");
    }

    private static void indent(int level, StringBuilder sb) {
        final String indent = new String(new char[level]).replace("\0", "  ");
        sb.append(indent);
    }

    @Override
    public String toString() {
        final String nl = System.lineSeparator();
        int i = 1;
        final StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append(nl);
        for (Map.Entry<byte[], NavigableMap<byte[], NavigableMap<byte[], NavigableMap<Long, byte[]>>>> row : getData().entrySet()) {
            indent(i, sb);
            sb.append(Bytes.toString(row.getKey()));
            sb.append(":");
            sb.append(nl);
            i++;
            for (Map.Entry<byte[], NavigableMap<byte[], NavigableMap<Long, byte[]>>> family : row.getValue().entrySet()) {
                indent(i, sb);
                sb.append(Bytes.toString(family.getKey()));
                sb.append(":");
                sb.append(nl);
                i++;
                for (Map.Entry<byte[], NavigableMap<Long, byte[]>> column : family.getValue().entrySet()) {
                    indent(i, sb);
                    sb.append(Bytes.toString(column.getKey()));
                    sb.append(": ");
                    sb.append(Bytes.toString(column.getValue().lastEntry().getValue()));
                    sb.append(nl);
                }
                i--;
            }
            i--;
        }
        sb.append(nl);
        sb.append("}");
        return sb.toString();
    }

}
