package com.xiyu.bid.notification.outbound.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.notification.outbound.event.NotificationCreatedEvent;
import com.xiyu.bid.notification.outbound.infrastructure.NotificationDeliveryTask;
import com.xiyu.bid.notification.outbound.infrastructure.NotificationDeliveryTaskRepository;
import com.xiyu.bid.notification.outbound.infrastructure.NotificationDeliveryTaskStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class NotificationDeliveryTaskListener {
    private final NotificationDeliveryTaskRepository taskRepository;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onNotificationCreated(NotificationCreatedEvent event) {
        if (event.notificationId() == null) {
            return;
        }
        for (Long recipientUserId : event.recipientUserIds()) {
            NotificationDeliveryCommand command = NotificationDeliveryCommand.fromEvent(event, recipientUserId);
            taskRepository.save(NotificationDeliveryTask.builder()
                    .notificationId(event.notificationId())
                    .recipientUserId(recipientUserId)
                    .eventType("notification.wecom_push")
                    .businessKey(buildBusinessKey(command))
                    .payload(serialize(command))
                    .status(NotificationDeliveryTaskStatus.PENDING)
                    .build());
        }
    }

    private String buildBusinessKey(NotificationDeliveryCommand command) {
        return "%s:%s:%s".formatted(command.notificationId(), command.recipientUserId(), command.type());
    }

    private String serialize(NotificationDeliveryCommand command) {
        try {
            return objectMapper.writeValueAsString(command);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize notification delivery command", ex);
        }
    }
}
