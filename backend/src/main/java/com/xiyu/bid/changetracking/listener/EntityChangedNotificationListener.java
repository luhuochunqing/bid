// Input: EntityChangedEvent 领域事件（AFTER_COMMIT）
// Output: 订阅者扇出 + DOCUMENT_CHANGE 通知派发
// Pos: Listener/变更追踪通知扩散
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.changetracking.listener;

import com.xiyu.bid.changetracking.core.ChangeDiffPolicy;
import com.xiyu.bid.changetracking.core.FieldChange;
import com.xiyu.bid.changetracking.event.EntityChangedEvent;
import com.xiyu.bid.notification.core.NotificationType;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.subscription.entity.Subscription;
import com.xiyu.bid.subscription.repository.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class EntityChangedNotificationListener {

    private static final int MAX_FANOUT = 500;
    private static final Logger log = LoggerFactory.getLogger(EntityChangedNotificationListener.class);

    private final SubscriptionRepository subscriptionRepository;
    private final NotificationApplicationService notificationService;

    public EntityChangedNotificationListener(
        SubscriptionRepository subscriptionRepository,
        NotificationApplicationService notificationService
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.notificationService = notificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEntityChanged(EntityChangedEvent event) {
        List<Long> allSubscribers = subscriptionRepository
            .findByTargetEntityTypeAndTargetEntityId(event.entityType(), event.entityId())
            .stream()
            .map(Subscription::getUserId)
            .filter(id -> !Objects.equals(id, event.actorUserId()))
            .distinct()
            .toList();

        if (allSubscribers.isEmpty()) {
            return;
        }

        List<Long> recipientIds = allSubscribers.size() <= MAX_FANOUT
            ? allSubscribers
            : allSubscribers.subList(0, MAX_FANOUT);

        if (allSubscribers.size() > MAX_FANOUT) {
            log.warn("Subscriber fan-out truncated for {} {}: {} -> {}",
                event.entityType(), event.entityId(), allSubscribers.size(), MAX_FANOUT);
        }

        List<FieldChange> changes = ChangeDiffPolicy.diff(event.before(), event.after());
        if (changes.isEmpty()) {
            return;
        }

        String title = buildTitle(event.entityType(), event.entityTitle());
        Map<String, Object> payload = new LinkedHashMap<>(event.metadata());
        payload.put("changes", changes);

        notificationService.createNotification(
            new CreateNotificationRequest(
                notificationType(event.entityType()).name(),
                event.entityType(),
                event.entityId(),
                title,
                null,
                payload,
                recipientIds
            ),
            event.actorUserId()
        );
    }

    private static NotificationType notificationType(String entityType) {
        if ("TASK".equals(entityType)) {
            return NotificationType.TASK_UPDATE;
        }
        if ("DOCUMENT".equals(entityType)) {
            return NotificationType.DOCUMENT_CHANGE;
        }
        return NotificationType.INFO;
    }

    private static String buildTitle(String entityType, String entityTitle) {
        String safe = entityTitle == null || entityTitle.isBlank() ? "对象" : entityTitle;
        if ("TASK".equals(entityType)) {
            return "任务《" + safe + "》有更新";
        }
        return "《" + safe + "》有更新";
    }
}
