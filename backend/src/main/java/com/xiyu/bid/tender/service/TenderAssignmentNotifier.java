// Input: Tender, 被分配人 ID/姓名, 操作人 ID
// Output: 发送站内通知给被分配的负责人（失败不影响调用方事务）
// Pos: Service/标讯分配通知外壳
package com.xiyu.bid.tender.service;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * CO-261: 标讯分配/转派成功后给被分配的负责人发站内通知。
 * 通知失败不影响调用方事务（与 BatchTenderAssignAppService 一致）。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenderAssignmentNotifier {

    private final NotificationApplicationService notificationAppService;

    /** 自动分配成功后通知被分配人。createdBy 兜底为系统(1L)。 */
    public void notifyAutoAssigned(Tender tender) {
        if (tender.getProjectManagerId() == null) {
            return;
        }
        send(tender, tender.getProjectManagerId(),
                "【标讯分配】" + tender.getTitle(),
                "您已被分配负责标讯「" + tender.getTitle() + "」，请尽快处理。",
                tender.getCreatorId() != null ? tender.getCreatorId() : 1L);
    }

    /** 转派成功后通知新负责人。 */
    public void notifyTransferred(Tender tender, Long newOwnerId, String oldOwnerName,
                                   String operatorName, Long operatorId) {
        send(tender, newOwnerId,
                "【标讯转派】" + tender.getTitle(),
                "标讯「" + tender.getTitle() + "」已转派给您负责，原负责人："
                        + (oldOwnerName != null ? oldOwnerName : "无")
                        + "。转派人：" + operatorName + "。",
                operatorId);
    }

    private void send(Tender tender, Long recipientId, String title, String body, Long createdBy) {
        try {
            notificationAppService.createNotification(
                    new CreateNotificationRequest("APPROVAL", "TENDER", tender.getId(),
                            title, body, null, List.of(recipientId)),
                    createdBy);
        } catch (RuntimeException e) {
            log.warn("Failed to send assignment notification for tender {}: {}",
                    tender.getId(), e.getMessage());
        }
    }
}
