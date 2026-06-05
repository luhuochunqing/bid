package com.xiyu.bid.batch.service;

import com.xiyu.bid.entity.User;

import java.util.List;

final class BatchValidationPolicy {

    private BatchValidationPolicy() {
    }

    static void validateBatchInput(List<?> ids, String fieldName, int maxBatchSize) {
        if (ids == null) {
            throw new IllegalArgumentException(fieldName + " cannot be null");
        }
        if (ids.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be empty");
        }
        if (ids.size() > maxBatchSize) {
            throw new IllegalArgumentException("Batch size exceeds maximum allowed size of " + maxBatchSize);
        }
    }

    static void validateUserId(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (userId <= 0) {
            throw new IllegalArgumentException("User ID must be positive");
        }
    }

    static void validateUserRole(User.Role userRole) {
        if (userRole == null) {
            throw new IllegalArgumentException("User role cannot be null");
        }
    }
}
