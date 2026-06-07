package com.xiyu.bid.notification.outbound.listener;

import com.xiyu.bid.notification.outbound.event.NotificationCreatedEvent;
import com.xiyu.bid.notification.outbound.service.WeComPushService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationCreatedWeComListener — fan-out per recipient, isolate failures")
class NotificationCreatedWeComListenerTest {

    @Mock private WeComPushService pushService;

    @InjectMocks private NotificationCreatedWeComListener listener;

    @Test
    @DisplayName("empty recipients → no push service calls")
    void emptyRecipients_NoDispatch() {
        NotificationCreatedEvent event = new NotificationCreatedEvent(
            1L, List.of(), "INFO", "t", null, null);

        listener.onNotificationCreated(event);

        verify(pushService, never()).pushForRecipient(any(), any());
    }

    @Test
    @DisplayName("null recipients → no push service calls")
    void nullRecipients_NoDispatch() {
        NotificationCreatedEvent event = new NotificationCreatedEvent(
            1L, null, "INFO", "t", null, null);

        listener.onNotificationCreated(event);

        verify(pushService, never()).pushForRecipient(any(), any());
    }

    @Test
    @DisplayName("multiple recipients → push called once per recipient")
    void multipleRecipients_OneCallEach() {
        NotificationCreatedEvent event = new NotificationCreatedEvent(
            1L, List.of(7L, 8L, 9L), "MENTION", "t", null, null);
        doNothing().when(pushService).pushForRecipient(any(), any());

        listener.onNotificationCreated(event);

        verify(pushService, times(3)).pushForRecipient(eq(event), any());
        verify(pushService).pushForRecipient(eq(event), eq(7L));
        verify(pushService).pushForRecipient(eq(event), eq(8L));
        verify(pushService).pushForRecipient(eq(event), eq(9L));
    }

    @Test
    @DisplayName("one recipient throws → other recipients still processed")
    void oneRecipientFails_OthersContinue() {
        NotificationCreatedEvent event = new NotificationCreatedEvent(
            1L, List.of(7L, 8L), "MENTION", "t", null, null);
        doThrow(new RuntimeException("boom")).when(pushService).pushForRecipient(event, 7L);
        doNothing().when(pushService).pushForRecipient(event, 8L);

        listener.onNotificationCreated(event);

        verify(pushService).pushForRecipient(event, 7L);
        verify(pushService).pushForRecipient(event, 8L);
    }
}
