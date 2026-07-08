package com.xiyu.bid.performance.application.service;

import com.xiyu.bid.alerts.dto.AlertHistoryCreateRequest;
import com.xiyu.bid.alerts.entity.AlertHistory;
import com.xiyu.bid.alerts.entity.AlertRule;
import com.xiyu.bid.alerts.service.AlertNotificationOrchestrator;
import com.xiyu.bid.alerts.service.AlertRuleProvisioningService;
import com.xiyu.bid.performance.application.view.ExpiringPerformanceAlertView;
import com.xiyu.bid.performance.domain.model.PerformanceAlertConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PerformanceExpiryAlertService {

    private final AlertRuleProvisioningService alertRuleProvisioningService;
    private final AlertNotificationOrchestrator alertNotificationOrchestrator;
    private final ScanExpiringPerformanceAppService scanService;

    @Transactional
    public int createAlerts(PerformanceAlertConfig config) {
        // P1-4: 使用共享的 AlertRuleProvisioningService.ensureRule
        AlertRule rule = alertRuleProvisioningService.ensureRule(
                AlertRule.AlertType.PERFORMANCE_EXPIRY,
                "业绩合同到期提醒",
                config.alertDaysSoe());
        List<ExpiringPerformanceAlertView> expiring = scanService.scan(config);
        int created = 0;
        for (ExpiringPerformanceAlertView record : expiring) {
            AlertHistoryCreateRequest request = new AlertHistoryCreateRequest();
            request.setRuleId(rule.getId());
            request.setLevel(AlertHistory.AlertLevel.HIGH);
            request.setRelatedId(record.getRelatedId());
            request.setMessage(record.getMessage());
            // P1-3: 使用 createAndNotifyIfNew 模板方法，消除 create + dispatch 重复
            alertNotificationOrchestrator.createAndNotifyIfNew(
                    request, rule, buildPerformancePayload(record));
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
     * <p>供 {@link AlertNotificationOrchestrator#createAndNotifyIfNew} 使用，
     * 携带跳转到业绩到期列表页所需的业务字段。</p>
     */
    private Map<String, Object> buildPerformancePayload(ExpiringPerformanceAlertView record) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("relatedId", record.getRelatedId());
        payload.put("targetUrl", "/performance?filter=expiring");
        return payload;
    }
}
