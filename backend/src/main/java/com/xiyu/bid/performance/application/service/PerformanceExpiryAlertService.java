package com.xiyu.bid.performance.application.service;

import com.xiyu.bid.alerts.dto.AlertHistoryCreateRequest;
import com.xiyu.bid.alerts.entity.AlertHistory;
import com.xiyu.bid.alerts.entity.AlertRule;
import com.xiyu.bid.alerts.repository.AlertRuleRepository;
import com.xiyu.bid.alerts.service.AlertHistoryService;
import com.xiyu.bid.performance.application.view.ExpiringPerformanceAlertView;
import com.xiyu.bid.performance.domain.model.PerformanceAlertConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PerformanceExpiryAlertService {

    private final AlertRuleRepository alertRuleRepository;
    private final AlertHistoryService alertHistoryService;
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
            alertHistoryService.createAlertHistory(request);
            created++;
        }
        log.info("Created {} performance expiry alerts (config: SOE={}d, default={}d, enabled={})",
                created, config.alertDaysSoe(), config.alertDaysDefault(), config.enabled());
        return created;
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
