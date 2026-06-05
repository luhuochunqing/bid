package com.xiyu.bid.integration.domain;

import java.util.List;

/**
 * Immutable result of a WeCom message send operation.
 * Pure domain record — no Spring/JPA annotations.
 */
public record WeComSendResult(
        boolean success,
        int errcode,
        String errmsg,
        List<String> sentTo
) {
}
