// Input: NotificationCreatedEvent (AFTER_COMMIT)
// Output: delegated task enqueue handled by NotificationDeliveryTaskListener
// Pos: Listener/通知 → 兼容保留入口
package com.xiyu.bid.notification.outbound.listener;

import com.xiyu.bid.notification.outbound.application.NotificationDeliveryTaskListener;
import com.xiyu.bid.notification.outbound.event.NotificationCreatedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NotificationCreatedWeComListener {
    private final NotificationDeliveryTaskListener taskListener;

    public NotificationCreatedWeComListener(NotificationDeliveryTaskListener taskListener) {
        this.taskListener = taskListener;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotificationCreated(NotificationCreatedEvent event) {
        taskListener.onNotificationCreated(event);
    }
}
