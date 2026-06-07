package com.xiyu.bid.integration.domain;

import java.util.List;

/**
 * Generic validation result for WeCom integration domain.
 * Mirrors the pattern from com.xiyu.bid.bidmatch.domain.ValidationResult.
 */
public record ValidationResult(boolean valid, List<String> errors) {

    public ValidationResult {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public static ValidationResult ok() {
        return new ValidationResult(true, List.of());
    }

    public static ValidationResult failed(List<String> errors) {
        return new ValidationResult(false, errors);
    }
}
