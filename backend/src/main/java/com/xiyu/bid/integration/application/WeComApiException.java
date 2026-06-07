package com.xiyu.bid.integration.application;

import com.xiyu.bid.integration.domain.WeComApiErrCode;

/**
 * Exception thrown when the WeCom API returns a non-OK errcode or an HTTP error occurs.
 */
public class WeComApiException extends RuntimeException {

    private final int errcode;

    public WeComApiException(int errcode, String message) {
        super(message);
        this.errcode = errcode;
    }

    public WeComApiException(int errcode, String message, Throwable cause) {
        super(message, cause);
        this.errcode = errcode;
    }

    public int errcode() {
        return errcode;
    }

    public WeComApiErrCode errcodeEnum() {
        return WeComApiErrCode.fromCode(errcode);
    }
}
