package com.xiyu.bid.platform.async;

import com.xiyu.bid.notification.outbound.application.NotificationDeliveryJobService;
import com.xiyu.bid.platform.async.domain.AsyncDecisionResolver;
import com.xiyu.bid.platform.async.infrastructure.AsyncObservabilityRecorder;
import com.xiyu.bid.webhook.application.WebhookDeliveryJobService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncGovernanceArchitectureTest {
    @Test
    void notificationAndWebhookAsyncWorkersShouldDependOnUnifiedAsyncComponents() {
        assertHasFieldOfType(NotificationDeliveryJobService.class, AsyncDecisionResolver.class);
        assertHasFieldOfType(NotificationDeliveryJobService.class, AsyncObservabilityRecorder.class);
        assertHasFieldOfType(WebhookDeliveryJobService.class, AsyncDecisionResolver.class);
        assertHasFieldOfType(WebhookDeliveryJobService.class, AsyncObservabilityRecorder.class);
    }

    private void assertHasFieldOfType(Class<?> targetClass, Class<?> fieldType) {
        boolean found = Arrays.stream(targetClass.getDeclaredFields())
                .map(Field::getType)
                .anyMatch(type -> type.equals(fieldType));
        assertThat(found)
                .as("%s should contain field type %s", targetClass.getSimpleName(), fieldType.getSimpleName())
                .isTrue();
    }
}
