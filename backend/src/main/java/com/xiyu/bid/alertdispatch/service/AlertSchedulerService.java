package com.xiyu.bid.alertdispatch.service;

import com.xiyu.bid.alerts.entity.AlertRule;
import com.xiyu.bid.alerts.repository.AlertRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertSchedulerService {

    private final AlertRuleRepository alertRuleRepository;
    private final AlertRuleDispatchService alertRuleDispatchService;

    /**
     * P0-4: 调度频率从每 2 分钟改为每日 09:00。
     *
     * <p>原 cron {@code 0 0/2 * * * ?} 每 2 分钟扫描一次所有规则，包括
     * DEADLINE/RISK/DOCUMENT/BUDGET。这些规则的业务数据变更粒度均为天级：
     * <ul>
     *   <li>DEADLINE: 标讯截止日期以天为单位</li>
     *   <li>RISK: 风险等级变更不频繁</li>
     *   <li>DOCUMENT: 文档缺失状态变更不频繁</li>
     *   <li>BUDGET: 费用占比以天为单位波动</li>
     * </ul>
     * 每日扫描足够覆盖业务需求，同时大幅减少无谓的全表扫描负载。</p>
     */
    @Scheduled(cron = "0 0 9 * * ?")
    public void checkAlertRules() {
        log.info("Starting scheduled alert rule check at {}", LocalDateTime.now());

        List<AlertRule> enabledRules = alertRuleRepository.findByEnabledTrue();
        for (AlertRule rule : enabledRules) {
            try {
                alertRuleDispatchService.dispatch(rule);
            } catch (RuntimeException e) {
                log.error("Error checking alert rule {}: {}", rule.getId(), e.getMessage(), e);
            }
        }

        log.info("Completed scheduled alert rule check. Processed {} rules", enabledRules.size());
    }

    public void triggerAlertCheck() {
        log.info("Manual trigger of alert check at {}", LocalDateTime.now());
        checkAlertRules();
    }
}
