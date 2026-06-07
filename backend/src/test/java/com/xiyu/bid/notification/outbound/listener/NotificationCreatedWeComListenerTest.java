package com.xiyu.bid.notification.outbound.listener;

import com.xiyu.bid.notification.outbound.application.NotificationDeliveryTaskListener;
import com.xiyu.bid.notification.outbound.event.NotificationCreatedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationCreatedWeComListener — delegate enqueue to task listener")
class NotificationCreatedWeComListenerTest {

    @Mock private NotificationDeliveryTaskListener taskListener;

    @InjectMocks private NotificationCreatedWeComListener listener;

    @Test
    @DisplayName("empty recipients still delegate once for consistent event handling")
    void delegatesToTaskListener() {
        NotificationCreatedEvent event = new NotificationCreatedEvent(1L, List.of(), "INFO", "t", null, null);

        listener.onNotificationCreated(event);

        verify(taskListener).onNotificationCreated(event);
        verify(taskListener, never()).onNotificationCreated(new NotificationCreatedEvent(2L, List.of(), "INFO", "x", null, null));
    }
}
