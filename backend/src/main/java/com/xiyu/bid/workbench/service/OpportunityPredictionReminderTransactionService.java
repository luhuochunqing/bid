// Input: 提醒日志、通知请求、事件投递领取信息
// Output: 独立短事务内的提醒创建、事件领取和投递状态更新
// Pos: Service/事务边界层 - 隔离商机预测扫描与外部 SDK 副作用
// 维护声明: 每个方法仅持有一个 REQUIRES_NEW 短事务，外部 SDK 调用由编排层负责。
package com.xiyu.bid.workbench.service;

import com.xiyu.bid.notification.core.DispatchResult;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.workbench.entity.OpportunityPredictionReminderLog;
import com.xiyu.bid.workbench.repository.OpportunityPredictionReminderLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 商机预测提醒的数据库事务边界。
 *
 * <p>外层扫描和 SDK 调用均不持有数据库事务；这里的每个方法只完成一个本地原子动作，
 * 避免某个项目失败后把其他项目已经提交的状态一起回滚。
 */
@Service
@RequiredArgsConstructor
public class OpportunityPredictionReminderTransactionService {

    private final OpportunityPredictionReminderLogRepository reminderLogRepository;
    private final NotificationApplicationService notificationApplicationService;

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<ReminderState> findState(Long projectId) {
        return reminderLogRepository.findByProjectId(projectId)
                .map(log -> new ReminderState(log.getPredictedBidDate(), log.getEventPublishedAt()));
    }

    /**
     * 在同一短事务中先插入唯一提醒日志，再创建站内通知。
     * saveAndFlush 让并发实例在发通知前由 project_id 唯一键决出唯一执行者。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createNotificationAndClaim(OpportunityPredictionReminderLog reminderLog,
                                           CreateNotificationRequest notificationRequest,
                                           Long systemActor,
                                           String claimToken,
                                           LocalDateTime claimedAt) {
        reminderLog.setEventPublishClaimToken(claimToken);
        reminderLog.setEventPublishClaimedAt(claimedAt);
        reminderLogRepository.saveAndFlush(reminderLog);

        DispatchResult dispatchResult = notificationApplicationService
                .createNotification(notificationRequest, systemActor);
        if (!dispatchResult.isValid()) {
            throw new NotificationRejectedException(dispatchResult.errorCode(), dispatchResult.errorMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claimPendingEvent(Long projectId, String claimToken,
                                     LocalDateTime claimedAt, LocalDateTime staleBefore) {
        return reminderLogRepository.claimPendingEvent(projectId, claimToken, claimedAt, staleBefore) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean completeEventPublish(Long projectId, String claimToken, LocalDateTime publishedAt) {
        return reminderLogRepository.completeEventPublish(projectId, claimToken, publishedAt) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseEventClaim(Long projectId, String claimToken) {
        reminderLogRepository.releaseEventClaim(projectId, claimToken);
    }

    public record ReminderState(LocalDate predictedBidDate, LocalDateTime eventPublishedAt) {
    }

    public static final class NotificationRejectedException extends RuntimeException {
        public NotificationRejectedException(String code, String message) {
            super("notification rejected: code=" + code + ", message=" + message);
        }
    }
}
