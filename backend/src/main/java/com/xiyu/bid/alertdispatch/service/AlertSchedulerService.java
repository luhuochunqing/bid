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

    @Scheduled(cron = "0 0/2 * * * ?")
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
