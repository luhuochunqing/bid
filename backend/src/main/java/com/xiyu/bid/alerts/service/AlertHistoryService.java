// Input: alerts repositories, dedup lookup, and request DTOs
// Output: Alert History business service operations with unresolved-alert dedup
// Pos: Service/业务层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.alerts.service;

import com.xiyu.bid.alerts.dto.AlertHistoryCreateRequest;
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

    public AlertHistory getAlertHistoryById(Long id) {
        return alertHistoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("AlertHistory not found with id: " + id));
    }
}
