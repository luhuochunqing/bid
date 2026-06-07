package com.xiyu.bid.exception;

/**
 * Exception thrown when a requested tender (招标信息) is not found.
 */
public class TenderNotFoundException extends BusinessException {

    public TenderNotFoundException(Long tenderId) {
        super(404, "标讯不存在: ID=" + tenderId);
    }

    public TenderNotFoundException(String title) {
        super(404, "标讯不存在: " + title);
    }

    public TenderNotFoundException(String message, Throwable cause) {
        super(404, message, cause);
    }
}
