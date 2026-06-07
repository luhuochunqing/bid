package com.xiyu.bid.fees.service;

import com.xiyu.bid.fees.dto.FeeCreateRequest;

import java.math.BigDecimal;

final class FeeRequestValidator {

    private FeeRequestValidator() {
    }

    static void validateCreateRequest(FeeCreateRequest request) {
        if (request.getProjectId() == null) {
            throw new IllegalArgumentException("Project ID is required");
        }

        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }

        if (request.getFeeType() == null) {
            throw new IllegalArgumentException("Fee type is required");
        }

        if (request.getFeeDate() == null) {
            throw new IllegalArgumentException("Fee date is required");
        }
    }
}
