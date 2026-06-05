package com.xiyu.bid.integration.domain;

/**
 * WeCom API error codes.
 * Pure domain enum — no Spring annotations.
 */
public enum WeComApiErrCode {

    OK(0),
    INVALID_CREDENTIAL(40001),
    INVALID_TOKEN(40014),
    API_RATE_LIMIT(45009),
    UNKNOWN(-1);

    private final int code;

    WeComApiErrCode(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static WeComApiErrCode fromCode(int code) {
        for (WeComApiErrCode v : values()) {
            if (v.code == code) {
                return v;
            }
        }
        return UNKNOWN;
    }
}
