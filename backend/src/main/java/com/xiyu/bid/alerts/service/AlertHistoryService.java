// Input: alerts repositories, dedup lookup, and request DTOs
// Output: Alert History business service operations with unresolved-alert dedup and create-if-absent result
// Pos: Service/业务层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.alerts.service;

import com.xiyu.bid.alerts.domain.DedupPolicy;
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

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertHistoryService {

    private final AlertHistoryRepository alertHistoryRepository;
    private final AlertRuleRepository alertRuleRepository;

    @Transactional
    public AlertHistory createAlertHistory(AlertHistoryCreateRequest request) {
        // P2-1: 委托给 createAlertHistoryIfAbsent，统一使用冷却期去重逻辑
        // （旧方法仅按未处理去重，是 createAlertHistoryIfAbsent 的子集）
        return createAlertHistoryIfAbsent(request).alertHistory();
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
     *   <li>CO-546: 支持通过 {@link AlertHistoryCreateRequest#getDedupPolicy()} 指定去重策略。
     *       默认 {@link DedupPolicy#REUSE_UNTIL_RESOLVED} 保持历史行为；
     *       CA 到期预警使用 {@link DedupPolicy#DAILY_DEDUP} 实现每日通知。</li>
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

        DedupPolicy policy = request.getDedupPolicy() == null
                ? DedupPolicy.REUSE_UNTIL_RESOLVED : request.getDedupPolicy();

        String relatedId = request.getRelatedId();
        boolean hasRelatedId = relatedId != null && !relatedId.trim().isEmpty();
        if (hasRelatedId) {
            // P2-8: 查最近一条告警（不论 resolved），统一判断未处理或冷却期内
            AlertHistory latest = alertHistoryRepository
                    .findFirstByRuleIdAndRelatedIdOrderByCreatedAtDesc(
                            request.getRuleId(), relatedId)
                    .orElse(null);
            if (latest != null && shouldReuseAlert(latest, policy)) {
                log.debug("复用已有告警：ruleId={}, relatedId={}, resolved={}, resolvedAt={}, policy={}",
                        request.getRuleId(), relatedId, latest.getResolved(), latest.getResolvedAt(), policy);
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
        log.debug("新建告警历史：ruleId={}, relatedId={}, policy={}", request.getRuleId(), request.getRelatedId(), policy);
        return new AlertHistoryCreateResult(saved, true);
    }

    /**
     * P2-8: 判断是否应复用已有告警（不创建新告警）。
     *
     * <p>复用条件根据 {@link DedupPolicy} 决定：
     * <ul>
     *   <li>{@link DedupPolicy#REUSE_UNTIL_RESOLVED}（默认，原行为）：
     *     <ul>
     *       <li>未处理告警 → 复用，直到人工 resolve</li>
     *       <li>已处理告警在冷却期内（默认 24h）→ 复用</li>
     *     </ul>
     *   </li>
     *   <li>{@link DedupPolicy#DAILY_DEDUP}（CO-546 CA 到期预警）：
     *     <ul>
     *       <li>未处理告警：createdAt 在今天 → 复用（当日去重）；否则新建（每日通知）</li>
     *       <li>已处理告警在冷却期内 → 复用（与默认策略一致）</li>
     *     </ul>
     *   </li>
     * </ul>
     *
     * @param alert  最近的告警记录
     * @param policy 去重策略
     * @return true 表示复用（不创建新告警），false 表示新建
     */
    private boolean shouldReuseAlert(AlertHistory alert, DedupPolicy policy) {
        if (alert.getResolved() == null || !alert.getResolved()) {
            // 未处理告警
            if (policy == DedupPolicy.DAILY_DEDUP) {
                // CO-546: 当日去重 — createdAt 在今天复用，昨日及之前新建
                LocalDateTime createdAt = alert.getCreatedAt();
                if (createdAt == null) {
                    return true; // 数据异常：默认复用，避免重复创建
                }
                return createdAt.toLocalDate().equals(LocalDate.now());
            }
            // REUSE_UNTIL_RESOLVED（原行为）：未处理告警一律复用
            return true;
        }
        // 已处理告警 → 检查是否在冷却期内（两种策略一致）
        if (alert.getResolvedAt() == null) {
            // 已处理但无处理时间（数据异常）→ 不复用，允许新建
            return false;
        }
        return alert.getResolvedAt().isAfter(
                LocalDateTime.now().minusHours(RESOLVED_COOLDOWN_HOURS));
    }

    public AlertHistory getAlertHistoryById(Long id) {
        return alertHistoryRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("AlertHistory not found with id: " + id));
    }
}
