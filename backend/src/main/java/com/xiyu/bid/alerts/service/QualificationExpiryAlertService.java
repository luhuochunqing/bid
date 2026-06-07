// Input: alert repositories/services and expiring qualification scan results
// Output: qualification-expiry alert creation orchestration with deduplicated history writes
// Pos: Service/业务层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.alerts.service;

import com.xiyu.bid.alerts.dto.AlertHistoryCreateRequest;
import com.xiyu.bid.alerts.entity.AlertHistory;
import com.xiyu.bid.alerts.entity.AlertRule;
import com.xiyu.bid.alerts.repository.AlertRuleRepository;
import com.xiyu.bid.businessqualification.application.service.ScanExpiringQualificationsAppService;
import com.xiyu.bid.businessqualification.application.view.ExpiringQualificationAlertView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class QualificationExpiryAlertService {

    private final AlertRuleRepository alertRuleRepository;
    private final AlertHistoryService alertHistoryService;
    private final ScanExpiringQualificationsAppService scanExpiringQualificationsAppService;

    @Transactional
    public int createAlerts(int thresholdDays) {
        AlertRule rule = ensureAlertRule(thresholdDays);
        List<ExpiringQualificationAlertView> expiringQualifications = scanExpiringQualificationsAppService.scan(thresholdDays);
        int created = 0;
        for (ExpiringQualificationAlertView qualification : expiringQualifications) {
            AlertHistoryCreateRequest request = new AlertHistoryCreateRequest();
            request.setRuleId(rule.getId());
            request.setLevel(AlertHistory.AlertLevel.HIGH);
            request.setRelatedId(qualification.getRelatedId());
            request.setMessage(qualification.getMessage());
            alertHistoryService.createAlertHistory(request);
            created++;
        }
        log.info("Created {} qualification expiry alerts for threshold {} days", created, thresholdDays);
        return created;
    }

    private AlertRule ensureAlertRule(int thresholdDays) {
        return alertRuleRepository.findByType(AlertRule.AlertType.QUALIFICATION_EXPIRY).stream()
                .findFirst()
                .orElseGet(() -> alertRuleRepository.save(AlertRule.builder()
                        .name("资质到期提醒")
                        .type(AlertRule.AlertType.QUALIFICATION_EXPIRY)
                        .condition(AlertRule.ConditionType.LESS_THAN)
                        .threshold(BigDecimal.valueOf(thresholdDays))
                        .enabled(true)
                        .createdBy("system")
                        .build()));
    }
}
