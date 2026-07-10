package com.xiyu.bid.crm.application;

/**
 * 用户 token 不可用异常（CO-152 补齐）。
 * <p>
 * 用于 webhook 回调场景：用户 OSS token 未找到 / 已过期 / 用户已登出。
 * <p>
 * 在 {@link com.xiyu.bid.webhook.application.WebhookFailureClassifier} 中映射为
 * {@link com.xiyu.bid.platform.async.domain.AsyncFailureKind#TRANSIENT_DEPENDENCY}，
 * 允许按 1/5/15min 重试，而不是立即死信（避免 Redis 抖动等临时故障误杀）。
 * <p>
 * 重试耗尽后仍不可用 → 死信（用户已确认接受）。
 */
public class TokenUnavailableException extends RuntimeException {
    public TokenUnavailableException(String message) {
        super(message);
    }

    public TokenUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
