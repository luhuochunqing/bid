package com.xiyu.bid.tendersource.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 标讯源测试响应 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenderSourceTestResponse {

    /** 是否成功 */
    private boolean success;

    /** 结果消息 */
    private String message;

    /** 测试时间 */
    private Instant testedAt;

    public static TenderSourceTestResponse success() {
        return TenderSourceTestResponse.builder()
                .success(true)
                .message("连接测试成功")
                .testedAt(Instant.now())
                .build();
    }

    public static TenderSourceTestResponse failure(String errorMessage) {
        return TenderSourceTestResponse.builder()
                .success(false)
                .message(errorMessage != null && !errorMessage.isBlank()
                        ? errorMessage
                        : "连接失败，请检查API端点和密钥")
                .testedAt(Instant.now())
                .build();
    }
}
