package com.xiyu.bid.integration.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for the WeCom send-test endpoint.
 */
public record WeComSendTestResponse(
        boolean success,
        int errcode,
        String errmsg,
        List<String> sentTo,
        LocalDateTime sentAt
) {
}
