// Input: project_id
// Output: 商机预测提醒日志（Optional）
// Pos: repository/ - 持久化访问
package com.xiyu.bid.workbench.repository;

import com.xiyu.bid.workbench.entity.OpportunityPredictionReminderLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 商机预测提醒日志仓库。
 *
 * <p>{@code project_id} 唯一：{@link #findByProjectId} 至多返回一条。
 */
public interface OpportunityPredictionReminderLogRepository
        extends JpaRepository<OpportunityPredictionReminderLog, Long> {

    Optional<OpportunityPredictionReminderLog> findByProjectId(Long projectId);
}
