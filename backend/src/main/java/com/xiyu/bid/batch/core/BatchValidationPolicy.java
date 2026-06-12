package com.xiyu.bid.batch.core;

import com.xiyu.bid.entity.User;

import java.util.List;

/**
 * 批处理基础校验规则 (Pure Core — no Spring dependencies)
 */
public final class BatchValidationPolicy {

    public static final int MAX_BATCH_SIZE = 100;

    public BatchValidationPolicy() {}

    public static void validateBatchInput(List<?> ids, String fieldName) {
        if (ids == null) {
            throw new IllegalArgumentException(fieldName + " cannot be null");
        }
        if (ids.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be empty");
        }
        if (ids.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("Batch size exceeds maximum allowed size of " + MAX_BATCH_SIZE);
        }
    }

    public static void validateUserId(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (userId <= 0) {
            throw new IllegalArgumentException("User ID must be positive");
        }
    }

    public static void validateUserRole(User.Role userRole) {
        if (userRole == null) {
            throw new IllegalArgumentException("User role cannot be null");
        }
    }

    public static void requireNonNull(Object request, String message) {
        if (request == null) {
            throw new IllegalArgumentException(message);
        }
    }

    /** 纯核心推荐：返回验证错误而非抛出异常 */
    public static java.util.Optional<String> validateBatchInputReturnError(List<?> ids, String fieldName) {
        if (ids == null) return java.util.Optional.of(fieldName + " cannot be null");
        if (ids.isEmpty()) return java.util.Optional.of(fieldName + " cannot be empty");
        if (ids.size() > MAX_BATCH_SIZE) return java.util.Optional.of("Batch size exceeds maximum allowed size of " + MAX_BATCH_SIZE);
        return java.util.Optional.empty();
    }

    public static java.util.Optional<String> validateUserIdReturnError(Long userId) {
        if (userId == null) return java.util.Optional.of("User ID cannot be null");
        if (userId <= 0) return java.util.Optional.of("User ID must be positive");
        return java.util.Optional.empty();
    }

    public static java.util.Optional<String> validateUserRoleReturnError(User.Role userRole) {
        if (userRole == null) return java.util.Optional.of("User role cannot be null");
        return java.util.Optional.empty();
    }
}
