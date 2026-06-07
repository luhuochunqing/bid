package com.xiyu.bid.businessqualification.domain.service;

public record QualificationValidationResult(boolean valid, String message) {

    public static QualificationValidationResult success() {
        return new QualificationValidationResult(true, null);
    }

    public static QualificationValidationResult invalid(String message) {
        return new QualificationValidationResult(false, message);
    }
}
