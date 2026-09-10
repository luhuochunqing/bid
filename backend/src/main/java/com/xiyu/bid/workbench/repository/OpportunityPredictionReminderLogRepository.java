// Input: project_id 与事件投递领取条件
// Output: 商机预测提醒日志及原子投递状态变更
// Pos: repository/ - 持久化访问
package com.xiyu.bid.workbench.repository;

import com.xiyu.bid.workbench.entity.OpportunityPredictionReminderLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/** 商机预测提醒日志仓库；project_id 唯一。 */
public interface OpportunityPredictionReminderLogRepository
        extends JpaRepository<OpportunityPredictionReminderLog, Long> {

    Optional<OpportunityPredictionReminderLog> findByProjectId(Long projectId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update OpportunityPredictionReminderLog reminder
            set reminder.eventPublishClaimToken = :claimToken,
                reminder.eventPublishClaimedAt = :claimedAt
            where reminder.projectId = :projectId
              and reminder.eventPublishedAt is null
              and (reminder.eventPublishClaimToken is null
                   or reminder.eventPublishClaimedAt is null
                   or reminder.eventPublishClaimedAt < :staleBefore)
            """)
    int claimPendingEvent(@Param("projectId") Long projectId,
                          @Param("claimToken") String claimToken,
                          @Param("claimedAt") LocalDateTime claimedAt,
                          @Param("staleBefore") LocalDateTime staleBefore);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update OpportunityPredictionReminderLog reminder
            set reminder.eventPublishedAt = :publishedAt,
                reminder.eventPublishClaimToken = null,
                reminder.eventPublishClaimedAt = null
            where reminder.projectId = :projectId
              and reminder.eventPublishedAt is null
              and reminder.eventPublishClaimToken = :claimToken
            """)
    int completeEventPublish(@Param("projectId") Long projectId,
                             @Param("claimToken") String claimToken,
                             @Param("publishedAt") LocalDateTime publishedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update OpportunityPredictionReminderLog reminder
            set reminder.eventPublishClaimToken = null,
                reminder.eventPublishClaimedAt = null
            where reminder.projectId = :projectId
              and reminder.eventPublishedAt is null
              and reminder.eventPublishClaimToken = :claimToken
            """)
    int releaseEventClaim(@Param("projectId") Long projectId,
                          @Param("claimToken") String claimToken);
}
