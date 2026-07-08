package com.xiyu.bid.performance.application.service;

import com.xiyu.bid.alerts.dto.AlertHistoryCreateRequest;
import com.xiyu.bid.alerts.dto.AlertHistoryCreateResult;
import com.xiyu.bid.alerts.entity.AlertHistory;
import com.xiyu.bid.alerts.entity.AlertRule;
import com.xiyu.bid.alerts.repository.AlertRuleRepository;
import com.xiyu.bid.alerts.service.AlertHistoryService;
import com.xiyu.bid.alerts.service.AlertNotificationOrchestrator;
import com.xiyu.bid.performance.application.view.ExpiringPerformanceAlertView;
import com.xiyu.bid.performance.domain.model.PerformanceAlertConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PerformanceExpiryAlertService {

    private final AlertRuleRepository alertRuleRepository;
    private final AlertHistoryService alertHistoryService;
    private final AlertNotificationOrchestrator alertNotificationOrchestrator;
    private final ScanExpiringPerformanceAppService scanService;

    @Transactional
    public int createAlerts(PerformanceAlertConfig config) {
        AlertRule rule = ensureAlertRule(config);
        List<ExpiringPerformanceAlertView> expiring = scanService.scan(config);
        int created = 0;
        for (ExpiringPerformanceAlertView record : expiring) {
            AlertHistoryCreateRequest request = new AlertHistoryCreateRequest();
            request.setRuleId(rule.getId());
            request.setLevel(AlertHistory.AlertLevel.HIGH);
            request.setRelatedId(record.getRelatedId());
            request.setMessage(record.getMessage());
            AlertHistoryCreateResult alertResult = alertHistoryService.createAlertHistoryIfAbsent(request);
            // 仅在新建告警时触发通知，复用已有未处理告警不重复推送
            if (alertResult.created()) {
                alertNotificationOrchestrator.dispatchNotification(
                        alertResult.alertHistory(), rule, buildPerformancePayload(record));
            }
            // 计数保留：每次扫描命中即计数，不区分新建/复用
            created++;
        }
        log.info("Created {} performance expiry alerts (config: SOE={}d, default={}d, enabled={})",
                created, config.alertDaysSoe(), config.alertDaysDefault(), config.enabled());
        return created;
    }

    /**
     * 构建业绩到期通知的附加 payload。
     *
     * <p>供 {@link AlertNotificationOrchestrator#dispatchNotification} 使用，
     * 携带跳转到业绩到期列表页所需的业务字段。</p>
     */
    private Map<String, Object> buildPerformancePayload(ExpiringPerformanceAlertView record) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("relatedId", record.getRelatedId());
        payload.put("targetUrl", "/performance?filter=expiring");
        return payload;
    }

    private AlertRule ensureAlertRule(PerformanceAlertConfig config) {
        return alertRuleRepository.findByType(AlertRule.AlertType.PERFORMANCE_EXPIRY).stream()
                .findFirst()
                .orElseGet(() -> alertRuleRepository.save(AlertRule.builder()
                        .name("业绩合同到期提醒")
                        .type(AlertRule.AlertType.PERFORMANCE_EXPIRY)
                        .condition(AlertRule.ConditionType.LESS_THAN)
                        // 差异化阈值：央企客户 180 天 / 其他 90 天。
                        // 当前 AlertRule 只存储一个 threshold，扫描时以 config.alertDays() 的真实值为准。
                        .threshold(BigDecimal.valueOf(config.alertDaysSoe()))
                        .enabled(config.enabled())
                        .createdBy("system")
                        .build()));
    }
}
