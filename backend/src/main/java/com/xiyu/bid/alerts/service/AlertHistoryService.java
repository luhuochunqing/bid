// Input: alerts repositories, dedup lookup, and request DTOs
// Output: Alert History business service operations with unresolved-alert dedup and create-if-absent result
// Pos: Service/业务层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.alerts.service;

import com.xiyu.bid.alerts.dto.AlertHistoryCreateRequest;
import com.xiyu.bid.alerts.dto.AlertHistoryCreateResult;
import com.xiyu.bid.alerts.entity.AlertHistory;
import com.xiyu.bid.alerts.entity.AlertRule;
import com.xiyu.bid.alerts.repository.AlertHistoryRepository;
import com.xiyu.bid.alerts.repository.AlertRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertHistoryService {

    private final AlertHistoryRepository alertHistoryRepository;
    private final AlertRuleRepository alertRuleRepository;

    @Transactional
    public AlertHistory createAlertHistory(AlertHistoryCreateRequest request) {
        if (request.getRuleId() == null) {
            throw new IllegalArgumentException("Rule ID is required");
        }
        if (request.getLevel() == null) {
            throw new IllegalArgumentException("Level is required");
        }
        if (request.getMessage() == null || request.getMessage().trim().isEmpty()) {
            throw new IllegalArgumentException("Message is required");
        }

        AlertRule rule = alertRuleRepository.findById(request.getRuleId())
                .orElseThrow(() -> new RuntimeException("AlertRule not found with id: " + request.getRuleId()));

        AlertHistory existingAlert = null;
        if (request.getRelatedId() != null && !request.getRelatedId().trim().isEmpty()) {
            existingAlert = alertHistoryRepository.findFirstByRuleIdAndRelatedIdAndResolvedFalseOrderByCreatedAtDesc(
                    request.getRuleId(), request.getRelatedId()).orElse(null);
        }
        if (existingAlert != null) {
            log.debug("Returning existing unresolved alert for rule {} and relatedId {}", rule.getId(), request.getRelatedId());
            return existingAlert;
        }

        AlertHistory alertHistory = AlertHistory.builder()
                .ruleId(request.getRuleId())
                .level(request.getLevel())
                .message(request.getMessage())
                .relatedId(request.getRelatedId())
                .resolved(false)
                .build();

        return alertHistoryRepository.save(alertHistory);
    }

    /**
     * P2-8: 已处理告警冷却期（小时）。
     *
     * <p>如果最近一条同 ruleId+relatedId 的告警已被处理且处理时间在冷却期内，
     * 则不创建新告警，避免短期内重复告警骚扰。</p>
     */
    static final int RESOLVED_COOLDOWN_HOURS = 24;

    /**
     * 创建告警历史，仅在不存在同 ruleId+relatedId 未处理记录时新建。
     *
     * <p>与 {@link #createAlertHistory(AlertHistoryCreateRequest)} 的区别：
     * <ul>
     *   <li>返回 {@link AlertHistoryCreateResult} 明确区分新建 vs 复用，供调用方决定是否触发通知。</li>
     *   <li>P2-8: 如果最近一条告警已被处理但在冷却期内（默认 24h），也视为复用，
     *       避免告警被处理后短期内又因相同条件触发而重复创建。</li>
     * </ul>
     *
     * @param request 创建请求
     * @return 创建结果，包含是否新建的标志和告警历史记录
     */
    @Transactional
    public AlertHistoryCreateResult createAlertHistoryIfAbsent(AlertHistoryCreateRequest request) {
        if (request.getRuleId() == null) {
            throw new IllegalArgumentException("Rule ID is required");
        }
        if (request.getLevel() == null) {
            throw new IllegalArgumentException("Level is required");
        }
        if (request.getMessage() == null || request.getMessage().trim().isEmpty()) {
            throw new IllegalArgumentException("Message is required");
        }

        String relatedId = request.getRelatedId();
        boolean hasRelatedId = relatedId != null && !relatedId.trim().isEmpty();
        if (hasRelatedId) {
            // P2-8: 查最近一条告警（不论 resolved），统一判断未处理或冷却期内
            AlertHistory latest = alertHistoryRepository
                    .findFirstByRuleIdAndRelatedIdOrderByCreatedAtDesc(
                            request.getRuleId(), relatedId)
                    .orElse(null);
            if (latest != null && shouldReuseAlert(latest)) {
                log.debug("复用已有告警：ruleId={}, relatedId={}, resolved={}, resolvedAt={}",
                        request.getRuleId(), relatedId, latest.getResolved(), latest.getResolvedAt());
                return new AlertHistoryCreateResult(latest, false);
            }
        }

        AlertHistory alertHistory = AlertHistory.builder()
                .ruleId(request.getRuleId())
                .level(request.getLevel())
                .message(request.getMessage())
                .relatedId(request.getRelatedId())
                .resolved(false)
                .build();
        AlertHistory saved = alertHistoryRepository.save(alertHistory);
        log.debug("新建告警历史：ruleId={}, relatedId={}", request.getRuleId(), request.getRelatedId());
        return new AlertHistoryCreateResult(saved, true);
    }

    /**
     * P2-8: 判断是否应复用已有告警（不创建新告警）。
     *
     * <p>复用条件：
     * <ul>
     *   <li>告警未处理（resolved=false）→ 复用，避免重复创建未处理告警</li>
     *   <li>告警已处理（resolved=true）但处理时间在冷却期内（默认 24h）→ 复用，避免短期内重复告警</li>
     * </ul>
     *
     * @param alert 最近的告警记录
     * @return true 表示复用（不创建新告警），false 表示新建
     */
    private boolean shouldReuseAlert(AlertHistory alert) {
        if (alert.getResolved() == null || !alert.getResolved()) {
            // 未处理告警 → 复用
            return true;
        }
        // 已处理告警 → 检查是否在冷却期内
        if (alert.getResolvedAt() == null) {
            // 已处理但无处理时间（数据异常）→ 不复用，允许新建
            return false;
        }
        return alert.getResolvedAt().isAfter(
                java.time.LocalDateTime.now().minusHours(RESOLVED_COOLDOWN_HOURS));
    }

    public AlertHistory getAlertHistoryById(Long id) {
        return alertHistoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("AlertHistory not found with id: " + id));
    }
}
