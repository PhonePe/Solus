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

@UtilityClass
public class ErrorMessages {
    public static final String GET_DEDUPERS_ERROR = "Error getting dedupers from AS";
    public static final String REGISTRATION_RECORD_FETCH_ERROR = "Error occurred while fetching registration record";
    public static final String FETCH_RECORD_ERROR = "Error occurred while fetching record(s)";
    public static final String FETCH_RECORD_WITH_BINS_ERROR = "Error occurred while fetching record with bins";
    public static final String UPDATE_BIN_ERROR = "Error occurred while updating bin bit info";
    public static final String CHECK_ENTITY_ABSENCE_ERROR = "Error occurred while checking the existence of entity(s) %s in the bloom filter";
    public static final String ADD_ENTITY_ERROR = "Error occurred while adding entity(s) %s to the bloom filter";
    public static final String FETCH_ERROR = "Error occurred while fetching registration row";
    public static final String SCAN_ERROR = "Error occurred while scanning the table %s";
}
