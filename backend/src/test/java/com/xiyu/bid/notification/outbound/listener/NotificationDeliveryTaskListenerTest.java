package com.xiyu.bid.notification.outbound.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.notification.outbound.application.NotificationDeliveryTaskListener;
import com.xiyu.bid.notification.outbound.event.NotificationCreatedEvent;
import com.xiyu.bid.notification.outbound.infrastructure.NotificationDeliveryTask;
import com.xiyu.bid.notification.outbound.infrastructure.NotificationDeliveryTaskRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationDeliveryTaskListener — enqueue delivery task per recipient")
class NotificationDeliveryTaskListenerTest {

    @Mock private NotificationDeliveryTaskRepository taskRepository;

    @Test
    @DisplayName("empty recipients -> no task created")
    void emptyRecipients_NoTask() {
        NotificationDeliveryTaskListener listener = new NotificationDeliveryTaskListener(taskRepository, new ObjectMapper());
        NotificationCreatedEvent event = new NotificationCreatedEvent(1L, List.of(), "INFO", "title", null, null);

        listener.onNotificationCreated(event);

        verify(taskRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("multiple recipients -> one task per recipient")
    void multipleRecipients_OneTaskEach() {
        NotificationDeliveryTaskListener listener = new NotificationDeliveryTaskListener(taskRepository, new ObjectMapper());
        NotificationCreatedEvent event = new NotificationCreatedEvent(1L, List.of(7L, 8L), "MENTION", "title", "PROJECT", 3L);

        listener.onNotificationCreated(event);

        ArgumentCaptor<NotificationDeliveryTask> captor = ArgumentCaptor.forClass(NotificationDeliveryTask.class);
        verify(taskRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(NotificationDeliveryTask::getRecipientUserId)
                .containsExactly(7L, 8L);
        assertThat(captor.getAllValues())
                .extracting(NotificationDeliveryTask::getStatus)
                .allMatch(status -> status.name().equals("PENDING"));
    }
}
