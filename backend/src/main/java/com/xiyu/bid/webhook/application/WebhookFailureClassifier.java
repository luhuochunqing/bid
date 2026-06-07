package com.xiyu.bid.webhook.application;

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
