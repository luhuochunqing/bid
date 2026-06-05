package com.xiyu.bid.integration.organization.infrastructure.client;

public class OrganizationDirectoryHttpGatewayException extends RuntimeException {
    private final boolean retryable;

    public OrganizationDirectoryHttpGatewayException(String message) {
        this(message, null, true);
    }

    public OrganizationDirectoryHttpGatewayException(String message, Throwable cause) {
        this(message, cause, true);
    }

    private OrganizationDirectoryHttpGatewayException(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.retryable = retryable;
    }

    public static OrganizationDirectoryHttpGatewayException retryable(String message, Throwable cause) {
        return new OrganizationDirectoryHttpGatewayException(message, cause, true);
    }

    public static OrganizationDirectoryHttpGatewayException nonRetryable(String message, Throwable cause) {
        return new OrganizationDirectoryHttpGatewayException(message, cause, false);
    }

    public boolean retryable() {
        return retryable;
    }
}
