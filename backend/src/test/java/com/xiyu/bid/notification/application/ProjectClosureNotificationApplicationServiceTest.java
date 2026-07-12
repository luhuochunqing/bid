// Output: ProjectClosureNotificationApplicationService orchestration + dedup + failure isolation
// Pos: backend test source / notification application service unit test
package com.xiyu.bid.notification.application;

import com.xiyu.bid.notification.core.DispatchResult;
import com.xiyu.bid.notification.core.NotificationType;
import com.xiyu.bid.notification.core.ProjectNotificationRole;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.entity.Notification;
import com.xiyu.bid.notification.repository.NotificationRepository;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.notification.service.ProjectNotificationRecipientPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProjectClosureNotificationApplicationService — 待结项申请通知编排")
class ProjectClosureNotificationApplicationServiceTest {

    private static final Long PROJECT_ID = 42L;
    private static final String PROJECT_NAME = "西域智能投标项目";
    private static final Long TRIGGERED_BY = 7L;
    private static final Long OWNER_ID = 100L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 12, 10, 0, 0);

    @Mock
    private NotificationApplicationService notificationApplicationService;

    @Mock
    private ProjectNotificationRecipientPolicy recipientPolicy;

    @Mock
    private NotificationRepository notificationRepository;

    private ProjectClosureNotificationApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ProjectClosureNotificationApplicationService(
                notificationApplicationService,
                recipientPolicy,
                notificationRepository);
    }

    @Test
    @DisplayName("有项目负责人时创建 PENDING_CLOSURE_APPLICATION 通知")
    void sendPendingClosureApplicationNotification_withOwner_createsNotification() {
        when(recipientPolicy.resolveRecipients(
                eq(PROJECT_ID), eq(Set.of(ProjectNotificationRole.PROJECT_OWNER)), eq(null)))
                .thenReturn(List.of(OWNER_ID));
        when(notificationRepository.findBySourceEntityTypeAndSourceEntityIdAndTypeAndCreatedAtAfter(
                eq("PROJECT"), eq(PROJECT_ID), eq(NotificationType.PENDING_CLOSURE_APPLICATION.name()), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(notificationApplicationService.createNotification(any(CreateNotificationRequest.class), eq(TRIGGERED_BY)))
                .thenReturn(DispatchResult.validWithId(999L));

        service.sendPendingClosureApplicationNotification(PROJECT_ID, PROJECT_NAME, TRIGGERED_BY);

        ArgumentCaptor<CreateNotificationRequest> captor =
                ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationApplicationService).createNotification(captor.capture(), eq(TRIGGERED_BY));
        CreateNotificationRequest request = captor.getValue();
        assertThat(request.type()).isEqualTo(NotificationType.PENDING_CLOSURE_APPLICATION.name());
        assertThat(request.sourceEntityType()).isEqualTo("PROJECT");
        assertThat(request.sourceEntityId()).isEqualTo(PROJECT_ID);
        assertThat(request.title()).isEqualTo("待结项申请 - " + PROJECT_NAME);
        assertThat(request.recipientUserIds()).containsExactly(OWNER_ID);
        assertThat(request.payload()).containsEntry("targetUrl", "/project/" + PROJECT_ID + "/closure");
    }

    @Test
    @DisplayName("项目负责人为空时不创建通知")
    void sendPendingClosureApplicationNotification_noOwner_doesNothing() {
        when(recipientPolicy.resolveRecipients(
                eq(PROJECT_ID), eq(Set.of(ProjectNotificationRole.PROJECT_OWNER)), eq(null)))
                .thenReturn(List.of());

        service.sendPendingClosureApplicationNotification(PROJECT_ID, PROJECT_NAME, TRIGGERED_BY);

        verify(notificationRepository, never()).findBySourceEntityTypeAndSourceEntityIdAndTypeAndCreatedAtAfter(
                any(), any(), any(), any());
        verify(notificationApplicationService, never()).createNotification(any(), any());
    }

    @Test
    @DisplayName("5 分钟窗口内存在历史通知时跳过创建")
    void sendPendingClosureApplicationNotification_duplicate_skipsCreation() {
        when(recipientPolicy.resolveRecipients(
                eq(PROJECT_ID), eq(Set.of(ProjectNotificationRole.PROJECT_OWNER)), eq(null)))
                .thenReturn(List.of(OWNER_ID));
        Notification recent = Notification.builder()
                .id(1L)
                .type(NotificationType.PENDING_CLOSURE_APPLICATION.name())
                .sourceEntityType("PROJECT")
                .sourceEntityId(PROJECT_ID)
                .createdAt(LocalDateTime.ofInstant(Instant.now().minusSeconds(180), ZoneOffset.UTC))
                .createdBy(1L)
                .title("待结项申请 - " + PROJECT_NAME)
                .build();
        when(notificationRepository.findBySourceEntityTypeAndSourceEntityIdAndTypeAndCreatedAtAfter(
                eq("PROJECT"), eq(PROJECT_ID), eq(NotificationType.PENDING_CLOSURE_APPLICATION.name()), any(LocalDateTime.class)))
                .thenReturn(List.of(recent));

        service.sendPendingClosureApplicationNotification(PROJECT_ID, PROJECT_NAME, TRIGGERED_BY);

        verify(notificationApplicationService, never()).createNotification(any(), any());
    }

    @Test
    @DisplayName("通知服务异常时不向外抛异常")
    void sendPendingClosureApplicationNotification_serviceException_isSwallowed() {
        when(recipientPolicy.resolveRecipients(
                eq(PROJECT_ID), eq(Set.of(ProjectNotificationRole.PROJECT_OWNER)), eq(null)))
                .thenReturn(List.of(OWNER_ID));
        when(notificationRepository.findBySourceEntityTypeAndSourceEntityIdAndTypeAndCreatedAtAfter(
                eq("PROJECT"), eq(PROJECT_ID), eq(NotificationType.PENDING_CLOSURE_APPLICATION.name()), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(notificationApplicationService.createNotification(any(CreateNotificationRequest.class), eq(TRIGGERED_BY)))
                .thenThrow(new RuntimeException("通知服务故障"));

        service.sendPendingClosureApplicationNotification(PROJECT_ID, PROJECT_NAME, TRIGGERED_BY);

        // 不抛异常即通过
        verify(notificationApplicationService).createNotification(any(), eq(TRIGGERED_BY));
    }

    @Test
    @DisplayName("其他类型的 PENDING_INITIATION 通知不触发待结项去重")
    void sendPendingClosureApplicationNotification_otherPendingInitiationType_doesNotSkip() {
        when(recipientPolicy.resolveRecipients(
                eq(PROJECT_ID), eq(Set.of(ProjectNotificationRole.PROJECT_OWNER)), eq(null)))
                .thenReturn(List.of(OWNER_ID));
        Notification other = Notification.builder()
                .id(2L)
                .type(NotificationType.PENDING_INITIATION.name())
                .sourceEntityType("PROJECT")
                .sourceEntityId(PROJECT_ID)
                .createdAt(LocalDateTime.ofInstant(Instant.now().minusSeconds(180), ZoneOffset.UTC))
                .createdBy(1L)
                .title("待立项 - " + PROJECT_NAME)
                .build();
        when(notificationRepository.findBySourceEntityTypeAndSourceEntityIdAndTypeAndCreatedAtAfter(
                eq("PROJECT"), eq(PROJECT_ID), eq(NotificationType.PENDING_CLOSURE_APPLICATION.name()), any(LocalDateTime.class)))
                .thenReturn(List.of(other));
        when(notificationApplicationService.createNotification(any(CreateNotificationRequest.class), eq(TRIGGERED_BY)))
                .thenReturn(DispatchResult.validWithId(1000L));

        service.sendPendingClosureApplicationNotification(PROJECT_ID, PROJECT_NAME, TRIGGERED_BY);

        verify(notificationApplicationService).createNotification(any(CreateNotificationRequest.class), eq(TRIGGERED_BY));
    }
}
