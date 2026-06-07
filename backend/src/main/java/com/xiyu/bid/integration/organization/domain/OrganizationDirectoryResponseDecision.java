package com.xiyu.bid.integration.organization.domain;

public record OrganizationDirectoryResponseDecision(
        OrganizationDirectoryResponseOutcome outcome,
        boolean retryable,
        String message
) {
}
