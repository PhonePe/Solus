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

package com.phonepe.solus.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Constants {
    public static final String REGISTRATION_ROOT_KEY = "deDuperConfig";
    public static final String HBASE_COLUMN_FAMILY_NAME = "S";
    public static final String SHARD_ID_COL_NAME = "shid";
    public static final String HBASE_KEY_FORMAT = "%s|%s|%s";
    public static final String REGISTER_DEDUPER_ERROR = "Error occurred while registering deduper %s";
    public static final String DEDUPER_STATE_CHANGE_ERROR = "Error occurred while changing deduper state %s";
}
