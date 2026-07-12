// Output: BidNotificationApplicationService 待立项通知编排逻辑覆盖
// Pos: notification/application/ - 投标立项通知应用服务单元测试
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
@DisplayName("BidNotificationApplicationService — 待立项通知应用服务编排")
class BidNotificationApplicationServiceTest {

    @Mock
    private NotificationApplicationService notificationApplicationService;

    @Mock
    private ProjectNotificationRecipientPolicy recipientPolicy;

    @Mock
    private NotificationRepository notificationRepository;

    private BidNotificationApplicationService service;

    @BeforeEach
    void setUp() {
        service = new BidNotificationApplicationService(
                notificationApplicationService,
                recipientPolicy,
                notificationRepository);
    }

    @Test
    @DisplayName("有项目负责人且无重复通知时创建待立项通知")
    void sendPendingInitiationNotification_shouldCreateNotification_whenRecipientExistsAndNoDuplicate() {
        Long tenderId = 10L;
        Long projectId = 100L;
        Long ownerId = 7L;
        Long triggeredBy = 99L;
        String tenderName = "西域智能标讯";
        String projectName = "西域智能投标项目";

        when(recipientPolicy.resolveRecipients(
                eq(projectId), eq(Set.of(ProjectNotificationRole.PROJECT_OWNER)), eq(null)))
                .thenReturn(List.of(ownerId));
        when(notificationRepository.findBySourceEntityTypeAndSourceEntityIdAndTypeAndCreatedAtAfter(
                eq("PROJECT"), eq(projectId), eq(NotificationType.SYSTEM.name()), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(notificationApplicationService.createNotification(any(CreateNotificationRequest.class), eq(triggeredBy)))
                .thenReturn(DispatchResult.validWithId(1000L));

        service.sendPendingInitiationNotification(tenderId, projectId, tenderName, projectName, triggeredBy);

        ArgumentCaptor<CreateNotificationRequest> captor = ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationApplicationService).createNotification(captor.capture(), eq(triggeredBy));
        CreateNotificationRequest request = captor.getValue();
        assertThat(request.type()).isEqualTo(NotificationType.SYSTEM.name());
        assertThat(request.sourceEntityType()).isEqualTo("PROJECT");
        assertThat(request.sourceEntityId()).isEqualTo(projectId);
        assertThat(request.title()).isEqualTo("待立项 - " + projectName);
        assertThat(request.recipientUserIds()).containsExactly(ownerId);
        assertThat(request.payload()).containsEntry("targetUrl", "/project/" + projectId + "/initiation");
    }

    @Test
    @DisplayName("无项目负责人时不创建通知")
    void sendPendingInitiationNotification_shouldSkip_whenNoRecipient() {
        Long tenderId = 10L;
        Long projectId = 100L;

        when(recipientPolicy.resolveRecipients(
                eq(projectId), eq(Set.of(ProjectNotificationRole.PROJECT_OWNER)), eq(null)))
                .thenReturn(List.of());

        service.sendPendingInitiationNotification(tenderId, projectId, "标讯", "项目", 99L);

        verify(notificationRepository, never()).findBySourceEntityTypeAndSourceEntityIdAndTypeAndCreatedAtAfter(
                any(), any(), any(), any());
        verify(notificationApplicationService, never()).createNotification(any(), any());
    }

    @Test
    @DisplayName("5 分钟内已存在 SYSTEM 通知时去重跳过")
    void sendPendingInitiationNotification_shouldSkip_whenDuplicateExists() {
        Long tenderId = 10L;
        Long projectId = 100L;
        Long ownerId = 7L;

        when(recipientPolicy.resolveRecipients(
                eq(projectId), eq(Set.of(ProjectNotificationRole.PROJECT_OWNER)), eq(null)))
                .thenReturn(List.of(ownerId));
        Notification existing = Notification.builder()
                .id(1L)
                .type(NotificationType.SYSTEM.name())
                .sourceEntityType("PROJECT")
                .sourceEntityId(projectId)
                .title("待立项 - 已有项目")
                .createdBy(1L)
                .createdAt(LocalDateTime.ofInstant(Instant.now().minusSeconds(120), ZoneOffset.UTC))
                .build();
        when(notificationRepository.findBySourceEntityTypeAndSourceEntityIdAndTypeAndCreatedAtAfter(
                eq("PROJECT"), eq(projectId), eq(NotificationType.SYSTEM.name()), any(LocalDateTime.class)))
                .thenReturn(List.of(existing));

        service.sendPendingInitiationNotification(tenderId, projectId, "西域智能标讯", "西域智能投标项目", 99L);

        verify(notificationApplicationService, never()).createNotification(any(), any());
    }

    @Test
    @DisplayName("NotificationApplicationService 抛异常时不影响调用方")
    void sendPendingInitiationNotification_shouldNotThrow_whenCreateNotificationFails() {
        Long tenderId = 10L;
        Long projectId = 100L;
        Long ownerId = 7L;

        when(recipientPolicy.resolveRecipients(
                eq(projectId), eq(Set.of(ProjectNotificationRole.PROJECT_OWNER)), eq(null)))
                .thenReturn(List.of(ownerId));
        when(notificationRepository.findBySourceEntityTypeAndSourceEntityIdAndTypeAndCreatedAtAfter(
                eq("PROJECT"), eq(projectId), eq(NotificationType.SYSTEM.name()), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(notificationApplicationService.createNotification(any(CreateNotificationRequest.class), any()))
                .thenThrow(new RuntimeException("notification service down"));

        service.sendPendingInitiationNotification(tenderId, projectId, "西域智能标讯", "西域智能投标项目", 99L);

        verify(notificationApplicationService).createNotification(any(CreateNotificationRequest.class), any());
    }

    @Test
    @DisplayName("其他类型的 SYSTEM 通知不触发待立项去重")
    void sendPendingInitiationNotification_shouldCreate_whenOtherSystemNotificationExists() {
        Long tenderId = 10L;
        Long projectId = 100L;
        Long ownerId = 7L;

        when(recipientPolicy.resolveRecipients(
                eq(projectId), eq(Set.of(ProjectNotificationRole.PROJECT_OWNER)), eq(null)))
                .thenReturn(List.of(ownerId));
        Notification other = Notification.builder()
                .id(2L)
                .type(NotificationType.SYSTEM.name())
                .sourceEntityType("PROJECT")
                .sourceEntityId(projectId)
                .title("阶段自动推进 - 西域智能投标项目")
                .createdBy(1L)
                .createdAt(LocalDateTime.ofInstant(Instant.now().minusSeconds(120), ZoneOffset.UTC))
                .build();
        when(notificationRepository.findBySourceEntityTypeAndSourceEntityIdAndTypeAndCreatedAtAfter(
                eq("PROJECT"), eq(projectId), eq(NotificationType.SYSTEM.name()), any(LocalDateTime.class)))
                .thenReturn(List.of(other));
        when(notificationApplicationService.createNotification(any(CreateNotificationRequest.class), any()))
                .thenReturn(DispatchResult.validWithId(1001L));

        service.sendPendingInitiationNotification(tenderId, projectId, "西域智能标讯", "西域智能投标项目", 99L);

        verify(notificationApplicationService).createNotification(any(CreateNotificationRequest.class), any());
    }
}
