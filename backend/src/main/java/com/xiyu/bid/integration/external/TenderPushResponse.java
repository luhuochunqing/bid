package com.xiyu.bid.integration.external;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 外部系统推送标讯响应 DTO（接口规范 v2.0）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenderPushResponse {

    private Long tenderId;

    /** CREATED / UPDATED / DUPLICATE */
    private String status;

    private String message;
}
