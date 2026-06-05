package com.xiyu.bid.batch.core;

import com.xiyu.bid.entity.User;

import java.util.List;

/**
 * 批处理基础校验规则
 */
public class BatchValidationPolicy {

    public static final int MAX_BATCH_SIZE = 100;

    public void validateBatchInput(List<?> ids, String fieldName) {
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

    public void validateUserId(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (userId <= 0) {
            throw new IllegalArgumentException("User ID must be positive");
        }
    }

    public void validateUserRole(User.Role userRole) {
        if (userRole == null) {
            throw new IllegalArgumentException("User role cannot be null");
        }
    }

    public void requireNonNull(Object request, String message) {
        if (request == null) {
            throw new IllegalArgumentException(message);
        }
    }
}
