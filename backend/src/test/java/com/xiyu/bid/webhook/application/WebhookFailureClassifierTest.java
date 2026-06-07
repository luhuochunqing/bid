package com.xiyu.bid.webhook.application;

import com.xiyu.bid.platform.async.domain.AsyncFailureKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebhookFailureClassifierTest {
    private final WebhookFailureClassifier classifier = new WebhookFailureClassifier();

    @Test
    void shouldTreat429AsTransientDependency() {
        assertEquals(AsyncFailureKind.TRANSIENT_DEPENDENCY, classifier.classifyStatusCode(429));
    }

    @Test
    void shouldTreat500AsTransientDependency() {
        assertEquals(AsyncFailureKind.TRANSIENT_DEPENDENCY, classifier.classifyStatusCode(500));
    }

    @Test
    void shouldTreat400AsContractInvalid() {
        assertEquals(AsyncFailureKind.CONTRACT_INVALID, classifier.classifyStatusCode(400));
    }
}
