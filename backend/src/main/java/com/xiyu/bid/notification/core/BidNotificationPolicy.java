// Input: tenderId, projectId, tenderName, projectName, targetUrl, triggeredByUserId + dedup context
// Output: optional CreateNotificationRequest (empty when deduplicated)
// Pos: Pure Core/投标立项待立项通知策略
package com.xiyu.bid.notification.core;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 投标立项待立项通知策略 —— 纯核心。
 *
 * <p>不依赖 Spring、Repository、系统时间或任何 IO。调用方显式传入当前时间和已存在通知时间戳，
 * 由 {@link NotificationDedupPolicy} 判断是否需要在默认 5 分钟窗口内去重。</p>
 */
public final class BidNotificationPolicy {

    private BidNotificationPolicy() {
    }

    /**
     * 生成待立项站内通知请求。
     *
     * @param tenderId            标讯 ID
     * @param projectId           项目 ID
     * @param tenderName          标讯名称
     * @param projectName         项目名称
     * @param targetUrl           跳转链接
     * @param triggeredByUserId   触发人用户 ID（保留给调用方审计，当前不写入请求字段）
     * @param recipientUserIds    接收人用户 ID 列表
     * @param now                 当前时间（显式传入）
     * @param existingTimestamps  同一项目已创建的 SYSTEM 通知时间戳列表（可为 null/空）
     * @return 去重窗口内已存在通知时返回 {@link Optional#empty()}，否则返回请求
     */
    public static Optional<CreateNotificationRequest> createRequest(
            Long tenderId,
            Long projectId,
            String tenderName,
            String projectName,
            String targetUrl,
            Long triggeredByUserId,
            List<Long> recipientUserIds,
            Instant now,
            List<Instant> existingTimestamps) {
        if (NotificationDedupPolicy.isDuplicate(now, existingTimestamps)) {
            return Optional.empty();
        }
        NotificationMessagePolicy.NotificationMessage message = NotificationMessagePolicy.forPendingInitiation(
                Project.builder().id(projectId).name(projectName).build(),
                Tender.builder().id(tenderId).title(tenderName).build(),
                targetUrl);
        CreateNotificationRequest request = new CreateNotificationRequest(
                message.type(),
                message.sourceEntityType(),
                message.sourceEntityId(),
                message.title(),
                message.body(),
                message.payload(),
                recipientUserIds);
        return Optional.of(request);
    }
}
