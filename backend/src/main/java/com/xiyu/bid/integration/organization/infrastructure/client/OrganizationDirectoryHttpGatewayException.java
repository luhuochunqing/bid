package com.xiyu.bid.integration.organization.infrastructure.client;

import com.xiyu.bid.platform.async.domain.AsyncFailureKind;

public class OrganizationDirectoryHttpGatewayException extends RuntimeException {
    private final boolean retryable;
    private final AsyncFailureKind failureKind;

    public OrganizationDirectoryHttpGatewayException(String message) {
        this(message, null, true, AsyncFailureKind.TRANSIENT_DEPENDENCY);
    }

    public OrganizationDirectoryHttpGatewayException(String message, Throwable cause) {
        this(message, cause, true, AsyncFailureKind.TRANSIENT_DEPENDENCY);
    }

    private OrganizationDirectoryHttpGatewayException(
            String message,
            Throwable cause,
            boolean retryable,
            AsyncFailureKind failureKind
    ) {
        super(message, cause);
        this.retryable = retryable;
        this.failureKind = failureKind;
    }

    public static OrganizationDirectoryHttpGatewayException retryable(String message, Throwable cause) {
        return new OrganizationDirectoryHttpGatewayException(message, cause, true, AsyncFailureKind.TRANSIENT_DEPENDENCY);
    }

    public static OrganizationDirectoryHttpGatewayException nonRetryable(String message, Throwable cause) {
        return new OrganizationDirectoryHttpGatewayException(message, cause, false, AsyncFailureKind.CONTRACT_INVALID);
    }

    public static OrganizationDirectoryHttpGatewayException persistent(String message, Throwable cause) {
        return new OrganizationDirectoryHttpGatewayException(message, cause, false, AsyncFailureKind.PERSISTENT_DEPENDENCY);
    }

    public static OrganizationDirectoryHttpGatewayException dataCorruption(String message, Throwable cause) {
        return new OrganizationDirectoryHttpGatewayException(message, cause, false, AsyncFailureKind.DATA_CORRUPTION);
    }

    public boolean retryable() {
        return retryable;
    }

    public AsyncFailureKind failureKind() {
        return failureKind;
    }
}
