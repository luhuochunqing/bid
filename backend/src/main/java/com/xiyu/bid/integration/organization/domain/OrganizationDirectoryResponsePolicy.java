package com.xiyu.bid.integration.organization.domain;

import java.util.Locale;
import java.util.Set;

public final class OrganizationDirectoryResponsePolicy {
    private static final Set<String> SUCCESS_CODES = Set.of("0", "200", "OK", "SUCCESS");
    private static final Set<String> NOT_FOUND_CODES = Set.of("404", "NOT_FOUND", "DATA_NOT_FOUND", "NO_DATA");
    private static final Set<String> RETRYABLE_CODES = Set.of("408", "429", "500", "502", "503", "504", "TIMEOUT");
    private static final Set<String> NON_RETRYABLE_CODES = Set.of("400", "401", "403", "PARAM_ERROR", "UNAUTHORIZED", "FORBIDDEN");

    private OrganizationDirectoryResponsePolicy() {
    }

    public static OrganizationDirectoryResponseDecision classify(String code, boolean hasData) {
        String normalized = normalize(code);
        if (SUCCESS_CODES.contains(normalized)) {
            return hasData ? success() : notFound("success response without data");
        }
        if (NOT_FOUND_CODES.contains(normalized)) {
            return notFound("remote data not found");
        }
        if (RETRYABLE_CODES.contains(normalized) || normalized.startsWith("5")) {
            return retryable("remote organization directory failure");
        }
        if (NON_RETRYABLE_CODES.contains(normalized) || normalized.startsWith("4")) {
            return nonRetryable("organization directory contract or auth failure");
        }
        return nonRetryable("unrecognized organization directory response code");
    }

    private static OrganizationDirectoryResponseDecision success() {
        return new OrganizationDirectoryResponseDecision(OrganizationDirectoryResponseOutcome.SUCCESS, false, "success");
    }

    private static OrganizationDirectoryResponseDecision notFound(String message) {
        return new OrganizationDirectoryResponseDecision(OrganizationDirectoryResponseOutcome.NOT_FOUND, false, message);
    }

    private static OrganizationDirectoryResponseDecision retryable(String message) {
        return new OrganizationDirectoryResponseDecision(OrganizationDirectoryResponseOutcome.RETRYABLE_FAILURE, true, message);
    }

    private static OrganizationDirectoryResponseDecision nonRetryable(String message) {
        return new OrganizationDirectoryResponseDecision(OrganizationDirectoryResponseOutcome.NON_RETRYABLE_FAILURE, false, message);
    }

    private static String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }
}
