package com.xiyu.bid.webhook.application;

import com.xiyu.bid.crm.application.TokenUnavailableException;
import com.xiyu.bid.platform.async.domain.AsyncFailureKind;
import com.xiyu.bid.platform.async.domain.AsyncFailureClassifier;
import org.springframework.stereotype.Component;

import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;

@Component
public class WebhookFailureClassifier implements AsyncFailureClassifier {
    @Override
    public AsyncFailureKind classify(Throwable error) {
        if (error instanceof HttpTimeoutException || error instanceof HttpConnectTimeoutException || error instanceof ConnectException) {
            return AsyncFailureKind.TRANSIENT_DEPENDENCY;
        }
        // CO-152 补齐：用户 token 不可用（登出/过期/Redis 抖动）按临时故障重试 1/5/15min，不立即死信
        if (error instanceof TokenUnavailableException) {
            return AsyncFailureKind.TRANSIENT_DEPENDENCY;
        }
        return AsyncFailureKind.BUG;
    }

    public AsyncFailureKind classifyStatusCode(int statusCode) {
        if (statusCode == 429 || statusCode >= 500) {
            return AsyncFailureKind.TRANSIENT_DEPENDENCY;
        }
        if (statusCode >= 400) {
            return AsyncFailureKind.CONTRACT_INVALID;
        }
        return AsyncFailureKind.BUSINESS_REJECT;
    }
}
