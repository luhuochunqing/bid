package com.xiyu.bid.performance.application.exception;

/**
 * 业绩 ZIP 导出异常（如附件总数超上限）。
 *
 * <p>继承 IllegalArgumentException，由 GlobalExceptionHandler 统一映射为 HTTP 400 + 友好消息。
 */
public class PerformanceExportException extends IllegalArgumentException {

    public PerformanceExportException(String message) {
        super(message);
    }
}
