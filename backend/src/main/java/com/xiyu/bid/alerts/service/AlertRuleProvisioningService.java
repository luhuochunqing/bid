// Input: AlertRule type + name + threshold
// Output: AlertRule entity (found or created)
// Pos: alerts/service - 告警规则供应服务（应用服务层）
package com.xiyu.bid.alerts.service;

import com.xiyu.bid.alerts.entity.AlertRule;
import com.xiyu.bid.alerts.repository.AlertRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 告警规则供应服务：统一管理告警规则的"查找或创建"逻辑。
 *
 * <p>P1-4: 消除多个扫描器中重复的 ensureAlertRule/ensureRule/syncRuleThreshold 模式。
 * 原先在 {@code CaExpiryScanService.ensureRule}、
 * {@code PerformanceExpiryAlertService.ensureAlertRule}、
 * {@code ScanDepositReturnTrackingAppService.ensureAlertRule} 中各自实现。</p>
 *
 * <p>两种模式：
 * <ul>
 *   <li>{@link #ensureRule} — 查找或创建（不同步阈值），用于 CA 扫描、业绩到期</li>
 *   <li>{@link #ensureRuleWithThresholdSync} — 查找并同步阈值或创建，用于保证金退还（阈值来自系统配置）</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlertRuleProvisioningService {

    private final AlertRuleRepository alertRuleRepository;

    /**
     * 查找或创建告警规则（不同步阈值）。
     *
     * <p>如果指定类型的规则已存在，直接返回；不存在则创建新规则。</p>
     *
     * @param type          告警规则类型
     * @param name          规则名称（仅在创建时使用）
     * @param thresholdDays 阈值天数（仅在创建时使用）
     * @return 找到或新建的告警规则
     */
    public AlertRule ensureRule(AlertRule.AlertType type, String name, int thresholdDays) {
        return alertRuleRepository.findByType(type).stream()
                .findFirst()
                .orElseGet(() -> alertRuleRepository.save(AlertRule.builder()
                        .name(name)
                        .type(type)
                        .condition(AlertRule.ConditionType.LESS_THAN)
                        .threshold(BigDecimal.valueOf(thresholdDays))
                        .enabled(true)
                        .createdBy("system")
                        .build()));
    }

    /**
     * 查找或创建告警规则，并在存在时同步阈值。
     *
     * <p>如果规则已存在但阈值/条件/启用状态不同，更新之；不存在则创建新规则。
     * 用于阈值来自系统配置的场景（如保证金退还 warnDays）。</p>
     *
     * @param type          告警规则类型
     * @param name          规则名称（仅在创建时使用）
     * @param thresholdDays 阈值天数
     * @return 同步或新建的告警规则
     */
    public AlertRule ensureRuleWithThresholdSync(AlertRule.AlertType type, String name, int thresholdDays) {
        return alertRuleRepository.findByType(type).stream()
                .findFirst()
                .map(rule -> syncRuleThreshold(rule, thresholdDays))
                .orElseGet(() -> alertRuleRepository.save(AlertRule.builder()
                        .name(name)
                        .type(type)
                        .condition(AlertRule.ConditionType.LESS_THAN)
                        .threshold(BigDecimal.valueOf(thresholdDays))
                        .enabled(true)
                        .createdBy("system")
                        .build()));
    }

    private AlertRule syncRuleThreshold(AlertRule rule, int thresholdDays) {
        if (rule.getThreshold() != null
                && rule.getThreshold().compareTo(BigDecimal.valueOf(thresholdDays)) == 0
                && rule.getCondition() == AlertRule.ConditionType.LESS_THAN
                && rule.getEnabled()) {
            return rule;
        }
        rule.setThreshold(BigDecimal.valueOf(thresholdDays));
        rule.setCondition(AlertRule.ConditionType.LESS_THAN);
        rule.setEnabled(true);
        return alertRuleRepository.save(rule);
    }
}
