package com.xiyu.bid.resources.service;

public class CaBusinessException extends RuntimeException {
    private final String errorCode;

    public CaBusinessException(String message) {
        super(message);
        this.errorCode = "CA_ERROR";
    }

    public CaBusinessException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() { return errorCode; }
}
