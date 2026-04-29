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
package com.phonepe.solus.filter.impl.hbase;

import com.phonepe.solus.filter.impl.EntityWithBitPositions;
import com.phonepe.solus.util.Constants;
import com.sematext.hbase.ds.AbstractRowKeyDistributor;
import com.sematext.hbase.ds.RowKeyDistributorByHashPrefix;
import com.sematext.hbase.ds.RowKeyDistributorByHashPrefix.Hasher;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import lombok.experimental.UtilityClass;
import lombok.val;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.util.Bytes;

@UtilityClass
public class HBaseBloomFilterUtils {
    public final Hasher ONE_BYTE_HASHER = new RowKeyDistributorByHashPrefix.OneByteSimpleHash(256);
    private final AbstractRowKeyDistributor rowKeyDistributor = new RowKeyDistributorByHashPrefix(
            ONE_BYTE_HASHER);

    public <E> List<Get> createBatchGets(Map<Long, List<EntityWithBitPositions<E>>> map,
                                         String columnFamilyName,
                                         String clientId,
                                         String deDuperName) {
        List<Get> gets = new LinkedList<>();
        map.entrySet()
                .forEach(entry -> {
                    final Get get = new Get(
                            rowKeyWithPrefix(Bytes.toBytes(getRowKey(entry.getKey(), clientId, deDuperName))));
                    get.addColumn(Bytes.toBytes(columnFamilyName), Bytes.toBytes(Constants.SHARD_ID_COL_NAME));
                    entry.getValue()
                            .forEach(entityWithBitPositions -> {
                                for (int bitPosition : entityWithBitPositions.getBitPositions()) {
                                    get.addColumn(Bytes.toBytes(columnFamilyName), Bytes.toBytes(bitPosition));
                                }
                            });
                    gets.add(get);
                });
        return gets;
    }

    public Map<Long, List<Integer>> getResultMap(Result[] results) {
        Map<Long, List<Integer>> resultMap = new HashMap<>();
        Arrays.stream(results)
                .filter(result -> Objects.nonNull(result) && !result.isEmpty())
                .forEach(result -> {
                    List<Integer> list = new LinkedList<>();
                    val familyMaps = result.getNoVersionMap();
                    familyMaps.forEach((family, familyMap) -> familyMap.forEach((qualifier, latestValue) -> {
                        if (Constants.SHARD_ID_COL_NAME.equals(Bytes.toString(qualifier))
                                && !resultMap.containsKey(Bytes.toLong(latestValue))) {
                            resultMap.put(Bytes.toLong(latestValue), list);
                        } else {
                            if (Bytes.toBoolean(latestValue)) {
                                list.add(Bytes.toInt(qualifier));
                            }
                        }
                    }));
                });
        return resultMap;
    }

    public <E> List<Put> createBatchPuts(Map<Long, List<EntityWithBitPositions<E>>> map,
                                         long ttl,
                                         String columnFamilyName,
                                         String clientId,
                                         String deDuperName) {
        List<Put> puts = new LinkedList<>();
        map.forEach((key, value) -> {
            Put put = new Put(rowKeyWithPrefix(Bytes.toBytes(getRowKey(key, clientId, deDuperName))));
            put.addColumn(Bytes.toBytes(columnFamilyName), Bytes.toBytes(Constants.SHARD_ID_COL_NAME),
                    Bytes.toBytes(key));
            value.forEach(entityWithBitPositions -> {
                for (int bitPosition : entityWithBitPositions.getBitPositions()) {
                    put.addColumn(Bytes.toBytes(columnFamilyName), Bytes.toBytes(bitPosition), Bytes.toBytes(true));
                }
            });
            put.setTTL(ttl);
            puts.add(put);
        });
        return puts;
    }

    public byte[] rowKeyWithPrefix(byte[] key) {
        return rowKeyDistributor.getDistributedKey(key);
    }

    public String getRowKey(Long shardId, String clientId, String deDuperName) {
        return String.format(Constants.HBASE_KEY_FORMAT, shardId, clientId, deDuperName);
    }

    public String getRegistrationRowKey(final String rootKey, final String clientId, final String deDuperName) {
        return String.format(Constants.HBASE_KEY_FORMAT, rootKey, clientId, deDuperName);
    }
}
