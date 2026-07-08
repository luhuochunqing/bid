package com.xiyu.bid.alerts.repository;

import com.xiyu.bid.alerts.entity.AlertHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface AlertHistoryRepository extends JpaRepository<AlertHistory, Long> {

    Page<AlertHistory> findByRuleId(Long ruleId, Pageable pageable);

    Page<AlertHistory> findByLevel(AlertHistory.AlertLevel level, Pageable pageable);

    Page<AlertHistory> findByResolvedFalse(Pageable pageable);

    Page<AlertHistory> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

    Page<AlertHistory> findByRelatedId(@Param("relatedId") String relatedId, Pageable pageable);

    Optional<AlertHistory> findFirstByRuleIdAndRelatedIdAndResolvedFalseOrderByCreatedAtDesc(Long ruleId, String relatedId);

    /**
     * P2-8: 查询指定规则+关联实体的最近一条告警（不论 resolved 状态）。
     *
     * <p>用于冷却期判断：如果最近一条告警已被处理且在冷却期内（默认 24h），
     * 则不创建新告警，避免短期内重复告警骚扰。</p>
     */
    Optional<AlertHistory> findFirstByRuleIdAndRelatedIdOrderByCreatedAtDesc(Long ruleId, String relatedId);

    long countByResolvedFalse();

    long countByLevel(AlertHistory.AlertLevel level);

    int deleteByResolvedTrueAndResolvedAtBefore(LocalDateTime cutoffDate);
}
