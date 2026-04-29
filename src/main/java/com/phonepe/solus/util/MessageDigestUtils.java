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

package com.phonepe.solus.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Slf4j
@UtilityClass
public class MessageDigestUtils {

    private final String MESSAGE_DIGEST_ALGORITHM_NAME = "MD5";//SHA1,SHA256
    private final Charset CHARSET = StandardCharsets.UTF_8; // encoding used for storing hash values as strings

    private final MessageDigest messageDigest;

    static {
        MessageDigest tempMessageDigest = null;
        try {
            tempMessageDigest = MessageDigest.getInstance(MESSAGE_DIGEST_ALGORITHM_NAME);
        } catch (NoSuchAlgorithmException e) {
            log.error("Exception occurred while getting instance of message digest", e);
        }
        messageDigest = tempMessageDigest;
    }

    /**
     * Generates a digest based on the contents of a String.
     *
     * @param val     specifies the input data.
     * @param charset specifies the encoding of the input data.
     * @return digest as long.
     */
    public int createHash(final String val, final Charset charset) {
        return createHash(val.getBytes(charset));
    }

    /**
     * Generates a digest based on the contents of a String.
     *
     * @param val specifies the input data. The encoding is expected to be UTF-8.
     * @return digest as long.
     */
    public int createHash(final String val) {
        return createHash(val, CHARSET);
    }

    /**
     * Generates a digest based on the contents of an array of bytes.
     *
     * @param data specifies input data.
     * @return digest as long.
     */
    public int createHash(final byte[] data) {
        return createHashes(data, 1)[0];
    }

    /**
     * Generates digests based on the contents of an array of bytes and splits the result into 4-byte int's and store them in an array. The
     * digest function is called until the required number of int's are produced. For each call to digest a salt
     * is prepended to the data. The salt is increased by 1 for each call.
     *
     * @param data   specifies input data.
     * @param hashes number of bitPositions/int's to produce.
     * @return array of int-sized bitPositions
     */
    public int[] createHashes(final byte[] data, final int hashes) {
        int[] result = new int[hashes];

        int k = 0;
        byte salt = 0;
        while (k < hashes) {
            byte[] digest;
            synchronized (messageDigest) {
                messageDigest.update(salt);
                salt++;
                digest = messageDigest.digest(data);
            }

            for (int i = 0; i < digest.length / 4 && k < hashes; i++, k++) {
                int h = 0;
                for (int j = (i * 4); j < (i * 4) + 4; j++) {
                    h <<= 8;
                    h |= (digest[j]) & 0xFF;
                }
                result[k] = h;
            }
        }
        return result;
    }
}

