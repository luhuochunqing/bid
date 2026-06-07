package com.xiyu.bid.notification.outbound.application;

import com.xiyu.bid.platform.async.domain.AsyncFailureKind;
import com.xiyu.bid.platform.async.domain.AsyncFailureClassifier;
import org.springframework.stereotype.Component;

@Component
public class NotificationFailureClassifier implements AsyncFailureClassifier {
    @Override
    public AsyncFailureKind classify(Throwable error) {
        String message = error == null || error.getMessage() == null ? "" : error.getMessage();
        if (message.contains("recipient") || message.contains("userId") || message.contains("mapping")) {
            return AsyncFailureKind.SIDE_EFFECT_OPTIONAL;
        }
        if (message.contains("429") || message.contains("5xx") || message.contains("timeout") || message.contains("超时")) {
            return AsyncFailureKind.TRANSIENT_DEPENDENCY;
        }
        if (message.contains("template") || message.contains("invalid")) {
            return AsyncFailureKind.CONTRACT_INVALID;
        }
        return AsyncFailureKind.BUG;
    }
}
