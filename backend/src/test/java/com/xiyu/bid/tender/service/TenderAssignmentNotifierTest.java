package com.xiyu.bid.tender.service;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CO-261: TenderAssignmentNotifier 契约——通知发送失败不抛异常，不影响调用方事务。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TenderAssignmentNotifier 通知外壳（CO-261）")
class TenderAssignmentNotifierTest {

    @Mock
    private NotificationApplicationService notificationAppService;

    @Test
    @DisplayName("notifyAutoAssigned 调用 notificationAppService 发送给被分配人")
    void notifyAutoAssigned_SendsToProjectManager() {
        Tender tender = Tender.builder()
                .id(1L).title("测试标讯").projectManagerId(100L).creatorId(2L).build();
        TenderAssignmentNotifier notifier = new TenderAssignmentNotifier(notificationAppService);

        notifier.notifyAutoAssigned(tender);

        verify(notificationAppService).createNotification(any(CreateNotificationRequest.class), anyLong());
    }

    @Test
    @DisplayName("projectManagerId 为 null 时不发送通知")
    void notifyAutoAssigned_NullManagerId_NoSend() {
        Tender tender = Tender.builder().id(1L).title("测试标讯").projectManagerId(null).build();
        TenderAssignmentNotifier notifier = new TenderAssignmentNotifier(notificationAppService);

        notifier.notifyAutoAssigned(tender);

        verify(notificationAppService, org.mockito.Mockito.never())
                .createNotification(any(CreateNotificationRequest.class), anyLong());
    }

    @Test
    @DisplayName("CO-261: notificationAppService 抛异常时 Notifier 吞掉，不向上抛")
    void notifyAutoAssigned_NotificationThrows_Swallowed() {
        Tender tender = Tender.builder()
                .id(1L).title("测试标讯").projectManagerId(100L).creatorId(2L).build();
        when(notificationAppService.createNotification(any(CreateNotificationRequest.class), anyLong()))
                .thenThrow(new RuntimeException("notification service down"));
        TenderAssignmentNotifier notifier = new TenderAssignmentNotifier(notificationAppService);

        // 不应抛异常——保证调用方事务不受影响
        notifier.notifyAutoAssigned(tender);

        verify(notificationAppService).createNotification(any(CreateNotificationRequest.class), anyLong());
    }

    @Test
    @DisplayName("notifyTransferred 调用 notificationAppService 发送给新负责人")
    void notifyTransferred_SendsToNewOwner() {
        Tender tender = Tender.builder().id(1L).title("测试标讯").build();
        TenderAssignmentNotifier notifier = new TenderAssignmentNotifier(notificationAppService);

        notifier.notifyTransferred(tender, 20L, "旧负责人", "操作人", 99L);

        verify(notificationAppService).createNotification(any(CreateNotificationRequest.class), anyLong());
    }

    @Test
    @DisplayName("CO-261: notifyTransferred 在通知异常时也吞掉，不向上抛")
    void notifyTransferred_NotificationThrows_Swallowed() {
        Tender tender = Tender.builder().id(1L).title("测试标讯").build();
        doThrow(new RuntimeException("notification service down"))
                .when(notificationAppService).createNotification(any(CreateNotificationRequest.class), anyLong());
        TenderAssignmentNotifier notifier = new TenderAssignmentNotifier(notificationAppService);

        // 不应抛异常——保证调用方事务不受影响
        notifier.notifyTransferred(tender, 20L, "旧负责人", "操作人", 99L);
    }
}
