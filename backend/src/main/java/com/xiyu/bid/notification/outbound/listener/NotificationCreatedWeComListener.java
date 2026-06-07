// Input: NotificationCreatedEvent (AFTER_COMMIT, async)
// Output: per-recipient WeCom push dispatch via WeComPushService
// Pos: Listener/通知 → 企微推送扩散
package com.xiyu.bid.notification.outbound.listener;

import com.xiyu.bid.notification.outbound.event.NotificationCreatedEvent;
import com.xiyu.bid.notification.outbound.service.WeComPushService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Component
public class NotificationCreatedWeComListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationCreatedWeComListener.class);

    private final WeComPushService pushService;

    public NotificationCreatedWeComListener(WeComPushService pushService) {
        this.pushService = pushService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotificationCreated(NotificationCreatedEvent event) {
        List<Long> recipients = event.recipientUserIds();
        if (recipients == null || recipients.isEmpty()) {
            return;
        }
        for (Long recipientId : recipients) {
            try {
                pushService.pushForRecipient(event, recipientId);
            } catch (RuntimeException e) {
                log.error("WeCom push dispatch failed for notification={} user={}: {}",
                    event.notificationId(), recipientId, e.getMessage());
            }
        }
    }
}
