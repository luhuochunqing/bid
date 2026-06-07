package com.xiyu.bid.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.notification.core.NotificationDispatchPolicy;
import com.xiyu.bid.notification.core.NotificationReadPolicy;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.dto.NotificationSummary;
import com.xiyu.bid.notification.entity.Notification;
import com.xiyu.bid.notification.entity.UserNotification;
import com.xiyu.bid.notification.repository.NotificationRepository;
import com.xiyu.bid.notification.repository.UserNotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationApplicationService — split-first orchestration")
class NotificationApplicationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserNotificationRepository userNotificationRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private NotificationApplicationService service;

    private NotificationApplicationService buildService() {
        return new NotificationApplicationService(
            notificationRepository, userNotificationRepository, new ObjectMapper(), eventPublisher);
    }

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = buildService();
    }

    private static Notification baseNotification(Long id) {
        return Notification.builder()
            .id(id)
            .type("INFO")
            .title("test title")
            .body("hello")
            .sourceEntityType("project")
            .sourceEntityId(42L)
            .createdBy(1L)
            .createdAt(LocalDateTime.now())
            .build();
    }

    private static UserNotification baseUserNotification(Long id, Long userId, Notification notif, LocalDateTime readAt) {
        return UserNotification.builder()
            .id(id)
            .userId(userId)
            .notification(notif)
            .notificationId(notif.getId())
            .readAt(readAt)
            .createdAt(LocalDateTime.now())
            .build();
    }

    @Test
    @DisplayName("getNotifications returns mapped page of NotificationSummary")
    void getNotifications_ReturnsPagedSummaries() {
        Long userId = 7L;
        Notification notif = baseNotification(100L);
        UserNotification un = baseUserNotification(11L, userId, notif, null);
        Pageable pageable = PageRequest.of(0, 10);
        Page<UserNotification> page = new PageImpl<>(List.of(un), pageable, 1);
        when(userNotificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable))
            .thenReturn(page);

        Page<NotificationSummary> result = service.getNotifications(userId, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        NotificationSummary first = result.getContent().get(0);
        assertThat(first.id()).isEqualTo(11L);
        assertThat(first.notificationId()).isEqualTo(100L);
        assertThat(first.title()).isEqualTo("test title");
        assertThat(first.read()).isFalse();
    }

    @Test
    @DisplayName("getUnreadCount returns repo count")
    void getUnreadCount_ReturnsCount() {
        when(userNotificationRepository.countByUserIdAndReadAtIsNull(5L)).thenReturn(3L);

        long count = service.getUnreadCount(5L);

        assertThat(count).isEqualTo(3L);
    }

    @Test
    @DisplayName("markAsRead on valid unread sets readAt and saves")
    void markAsRead_ValidUnread_SetsReadAt() {
        Long userId = 7L;
        Long notificationId = 100L;
        Notification notif = baseNotification(notificationId);
        UserNotification un = baseUserNotification(11L, userId, notif, null);
        when(userNotificationRepository.findByNotificationIdAndUserId(notificationId, userId))
            .thenReturn(Optional.of(un));

        NotificationReadPolicy.ReadResult result = service.markAsRead(notificationId, userId);

        assertThat(result.isValid()).isTrue();
        assertThat(result.alreadyRead()).isFalse();
        assertThat(un.getReadAt()).isNotNull();
        verify(userNotificationRepository).save(un);
    }

    @Test
    @DisplayName("markAsRead by another user returns forbidden")
    void markAsRead_CrossUser_ReturnsForbidden() {
        Long ownerId = 7L;
        Long requestingId = 8L;
        Long notificationId = 100L;
        Notification notif = baseNotification(notificationId);
        UserNotification un = baseUserNotification(11L, ownerId, notif, null);
        when(userNotificationRepository.findByNotificationIdAndUserId(notificationId, requestingId))
            .thenReturn(Optional.of(un));

        NotificationReadPolicy.ReadResult result = service.markAsRead(notificationId, requestingId);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errorCode()).isEqualTo("FORBIDDEN");
        verify(userNotificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("markAsRead is idempotent on already-read notifications")
    void markAsRead_AlreadyRead_ReturnsIdempotent() {
        Long userId = 7L;
        Long notificationId = 100L;
        Notification notif = baseNotification(notificationId);
        UserNotification un = baseUserNotification(11L, userId, notif, LocalDateTime.now().minusMinutes(5));
        when(userNotificationRepository.findByNotificationIdAndUserId(notificationId, userId))
            .thenReturn(Optional.of(un));

        NotificationReadPolicy.ReadResult result = service.markAsRead(notificationId, userId);

        assertThat(result.isValid()).isTrue();
        assertThat(result.alreadyRead()).isTrue();
        verify(userNotificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("markAllAsRead issues bulk UPDATE via repository")
    void markAllAsRead_SetsReadAtOnAllUnread() {
        Long userId = 7L;
        when(userNotificationRepository.markAllReadForUser(eq(userId), any(LocalDateTime.class)))
            .thenReturn(2);

        service.markAllAsRead(userId);

        verify(userNotificationRepository).markAllReadForUser(eq(userId), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("createNotification stores Notification + a UserNotification per recipient")
    void createNotification_Valid_CreatesForAllRecipients() {
        CreateNotificationRequest request = new CreateNotificationRequest(
            "INFO", "project", 42L, "title", "body", null, List.of(7L, 8L));
        Notification persisted = baseNotification(101L);
        when(notificationRepository.save(any(Notification.class))).thenReturn(persisted);

        NotificationDispatchPolicy.DispatchResult result =
            service.createNotification(request, 1L);

        assertThat(result.isValid()).isTrue();
        verify(notificationRepository, times(1)).save(any(Notification.class));
        verify(userNotificationRepository, times(1)).saveAll(any(Iterable.class));
    }

    @Test
    @DisplayName("createNotification with blank title returns dispatch error and saves nothing")
    void createNotification_InvalidTitle_ReturnsError() {
        CreateNotificationRequest request = new CreateNotificationRequest(
            "INFO", null, null, "  ", "body", null, List.of(7L));

        NotificationDispatchPolicy.DispatchResult result =
            service.createNotification(request, 1L);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errorCode()).isEqualTo("INVALID_TITLE");
        verify(notificationRepository, never()).save(any());
        verify(userNotificationRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("createNotification with unknown type returns INVALID_TYPE")
    void createNotification_UnknownType_ReturnsError() {
        CreateNotificationRequest request = new CreateNotificationRequest(
            "BOGUS_TYPE", null, null, "title", "body", null, List.of(7L));

        NotificationDispatchPolicy.DispatchResult result =
            service.createNotification(request, 1L);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errorCode()).isEqualTo("INVALID_TYPE");
        verify(notificationRepository, never()).save(any());
    }
}
