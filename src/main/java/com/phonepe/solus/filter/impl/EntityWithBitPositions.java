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

package com.phonepe.solus.filter.impl;

import com.phonepe.solus.util.MessageDigestUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.hadoop.hbase.util.Bytes;

import java.util.LinkedList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntityWithBitPositions<E> {

    private E entity;
    private List<Integer> bitPositions;

    public EntityWithBitPositions(final E entity,
                                  final int numberOfHashes,
                                  final int totalNumberOfPositions) {
        this.entity = entity;
        final int[] hashes = MessageDigestUtils.createHashes(Bytes.toBytes(entity.toString()), numberOfHashes);
        this.bitPositions = new LinkedList<>();
        for (int hash : hashes) {
            bitPositions.add(Math.abs(hash) % totalNumberOfPositions);
        }
    }

}
