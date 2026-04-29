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

import com.phonepe.solus.exception.ErrorCode;
import com.phonepe.solus.exception.SolusException;
import lombok.experimental.UtilityClass;

import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import java.util.Set;

@UtilityClass
public class CommonUtils {
    public static <T> void validate(final Validator validator, final T request) {
        final Set<ConstraintViolation<T>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw SolusException.builder()
                    .errorCode(ErrorCode.INVALID_CONFIG)
                    .message(violations.toString())
                    .build();
        }
    }
}
