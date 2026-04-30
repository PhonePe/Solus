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

package com.phonepe.solus.exception;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class SolusException extends RuntimeException {
    private final ErrorCode errorCode;

    @Builder
    public SolusException(final ErrorCode errorCode,
                          final String message,
                          final Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public static SolusException propagate(final ErrorCode errorCode, final Throwable throwable) {
        return propagate(throwable.getMessage(), throwable, errorCode);
    }

    public static SolusException propagate(final String message, final Throwable throwable, final ErrorCode errorCode) {
        if (throwable instanceof SolusException) {
            return (SolusException) throwable;
        } else if (throwable.getCause() instanceof SolusException) {
            return (SolusException) throwable.getCause();
        }
        return SolusException.builder()
                .errorCode(errorCode)
                .message(message)
                .cause(throwable)
                .build();
    }

}
