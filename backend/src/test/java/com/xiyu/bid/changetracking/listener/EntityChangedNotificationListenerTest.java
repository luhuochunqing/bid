package com.xiyu.bid.changetracking.listener;

import com.xiyu.bid.changetracking.event.EntityChangedEvent;
import com.xiyu.bid.notification.core.NotificationDispatchPolicy.DispatchResult;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.subscription.entity.Subscription;
import com.xiyu.bid.subscription.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntityChangedNotificationListenerTest {

    private SubscriptionRepository subscriptionRepository;
    private NotificationApplicationService notificationService;
    private EntityChangedNotificationListener listener;

    @BeforeEach
    void setUp() {
        subscriptionRepository = mock(SubscriptionRepository.class);
        notificationService = mock(NotificationApplicationService.class);
        listener = new EntityChangedNotificationListener(subscriptionRepository, notificationService);
    }

    private static Subscription subscription(Long userId) {
        return Subscription.builder()
            .userId(userId)
            .targetEntityType("DOCUMENT")
            .targetEntityId(100L)
            .build();
    }

    @Test
    void onEntityChanged_ShouldSkip_WhenNoSubscribers() {
        when(subscriptionRepository.findByTargetEntityTypeAndTargetEntityId("DOCUMENT", 100L))
            .thenReturn(List.of());

        listener.onEntityChanged(new EntityChangedEvent(
            "DOCUMENT", 100L, 1L, Map.of("a", 1), Map.of("a", 2), "某文档"
        ));

        verify(notificationService, never()).createNotification(any(), any());
    }

    @Test
    void onEntityChanged_ShouldExcludeActor_FromRecipients() {
        when(subscriptionRepository.findByTargetEntityTypeAndTargetEntityId("DOCUMENT", 100L))
            .thenReturn(List.of(subscription(1L), subscription(2L), subscription(3L)));
        when(notificationService.createNotification(any(), any())).thenReturn(DispatchResult.valid());

        listener.onEntityChanged(new EntityChangedEvent(
            "DOCUMENT", 100L, 1L, Map.of("a", 1), Map.of("a", 2), "某文档"
        ));

        ArgumentCaptor<CreateNotificationRequest> captor =
            ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationService, times(1)).createNotification(captor.capture(), eq(1L));
        assertThat(captor.getValue().recipientUserIds()).containsExactlyInAnyOrder(2L, 3L);
    }

    @Test
    void onEntityChanged_ShouldDistinctRecipients_WhenDuplicatedSubscribers() {
        when(subscriptionRepository.findByTargetEntityTypeAndTargetEntityId("DOCUMENT", 100L))
            .thenReturn(List.of(subscription(2L), subscription(2L), subscription(3L)));
        when(notificationService.createNotification(any(), any())).thenReturn(DispatchResult.valid());

        listener.onEntityChanged(new EntityChangedEvent(
            "DOCUMENT", 100L, 1L, Map.of("a", 1), Map.of("a", 2), "某文档"
        ));

        ArgumentCaptor<CreateNotificationRequest> captor =
            ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationService).createNotification(captor.capture(), eq(1L));
        assertThat(captor.getValue().recipientUserIds()).containsExactlyInAnyOrder(2L, 3L);
    }

    @Test
    void onEntityChanged_ShouldSkip_WhenDiffEmpty() {
        when(subscriptionRepository.findByTargetEntityTypeAndTargetEntityId("DOCUMENT", 100L))
            .thenReturn(List.of(subscription(2L)));

        listener.onEntityChanged(new EntityChangedEvent(
            "DOCUMENT", 100L, 1L, Map.of("a", 1), Map.of("a", 1), "某文档"
        ));

        verify(notificationService, never()).createNotification(any(), any());
    }

    @Test
    void onEntityChanged_ShouldIncludeChangesInPayload() {
        when(subscriptionRepository.findByTargetEntityTypeAndTargetEntityId("DOCUMENT", 100L))
            .thenReturn(List.of(subscription(2L)));
        when(notificationService.createNotification(any(), any())).thenReturn(DispatchResult.valid());

        listener.onEntityChanged(new EntityChangedEvent(
            "DOCUMENT", 100L, 1L, Map.of("a", 1), Map.of("a", 2), "某文档"
        ));

        ArgumentCaptor<CreateNotificationRequest> captor =
            ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationService).createNotification(captor.capture(), eq(1L));
        CreateNotificationRequest request = captor.getValue();
        assertThat(request.payload()).containsKey("changes");
        Object changes = request.payload().get("changes");
        assertThat(changes).isInstanceOf(List.class);
        assertThat((List<?>) changes).hasSize(1);
    }

    @Test
    void onEntityChanged_ShouldIncludeEntityTitleInNotificationTitle() {
        when(subscriptionRepository.findByTargetEntityTypeAndTargetEntityId("DOCUMENT", 100L))
            .thenReturn(List.of(subscription(2L)));
        when(notificationService.createNotification(any(), any())).thenReturn(DispatchResult.valid());

        listener.onEntityChanged(new EntityChangedEvent(
            "DOCUMENT", 100L, 1L, Map.of("a", 1), Map.of("a", 2), "合同草稿A"
        ));

        ArgumentCaptor<CreateNotificationRequest> captor =
            ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationService).createNotification(captor.capture(), eq(1L));
        assertThat(captor.getValue().title()).contains("合同草稿A");
    }

    @Test
    void onEntityChanged_ShouldUseTaskUpdateTypeAndCarryMetadata_ForTaskEvents() {
        Subscription taskSubscriber = Subscription.builder()
            .userId(2L)
            .targetEntityType("TASK")
            .targetEntityId(99L)
            .build();
        when(subscriptionRepository.findByTargetEntityTypeAndTargetEntityId("TASK", 99L))
            .thenReturn(List.of(taskSubscriber));
        when(notificationService.createNotification(any(), any())).thenReturn(DispatchResult.valid());

        listener.onEntityChanged(new EntityChangedEvent(
            "TASK",
            99L,
            1L,
            Map.of("title", "准备商务标"),
            Map.of("title", "准备商务标 V2"),
            "准备商务标 V2",
            Map.of("projectId", 10L)
        ));

        ArgumentCaptor<CreateNotificationRequest> captor =
            ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationService).createNotification(captor.capture(), eq(1L));
        CreateNotificationRequest request = captor.getValue();
        assertThat(request.type()).isEqualTo("TASK_UPDATE");
        assertThat(request.payload()).containsEntry("projectId", 10L);
    }
}
