package com.xiyu.bid.tendersource.domain;

import lombok.Builder;
import lombok.Data;

/**
 * 标讯源连接测试结果（值对象）。
 */
@Data
@Builder
public final class TenderSourceConnectionResult {

    /**
     * 是否成功
     */
    private final boolean success;

    /**
     * 结果消息
     */
    private final String message;

    public static TenderSourceConnectionResult success() {
        return TenderSourceConnectionResult.builder()
                .success(true)
                .message("连接测试成功")
                .build();
    }

    public static TenderSourceConnectionResult failure(String errorMessage) {
        return TenderSourceConnectionResult.builder()
                .success(false)
                .message(errorMessage != null && !errorMessage.isBlank()
                        ? errorMessage
                        : "连接失败，请检查API端点和密钥")
                .build();
    }
}
