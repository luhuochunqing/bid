// Input: alerts repositories, DTOs, and support services
// Output: Alert Rule business service operations
// Pos: Service/业务层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.alerts.service;

import com.xiyu.bid.alerts.dto.AlertRuleCreateRequest;
import com.xiyu.bid.alerts.dto.AlertRuleUpdateRequest;
import com.xiyu.bid.alerts.entity.AlertRule;
import com.xiyu.bid.alerts.repository.AlertRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertRuleService {

    private final AlertRuleRepository alertRuleRepository;

    @Transactional
    public AlertRule createAlertRule(AlertRuleCreateRequest request) {
        // Validation
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Name is required");
        }
        if (request.getType() == null) {
            throw new IllegalArgumentException("Type is required");
        }
        if (request.getCondition() == null) {
            throw new IllegalArgumentException("Condition is required");
        }
        if (request.getThreshold() == null || request.getThreshold().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Threshold must be positive");
        }
        if (request.getCreatedBy() == null || request.getCreatedBy().trim().isEmpty()) {
            throw new IllegalArgumentException("Created by is required");
        }

        AlertRule alertRule = AlertRule.builder()
                .name(request.getName())
                .type(request.getType())
                .condition(request.getCondition())
                .threshold(request.getThreshold())
                .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                .createdBy(request.getCreatedBy())
                .build();

        return alertRuleRepository.save(alertRule);
    }

    public AlertRule getAlertRuleById(Long id) {
        return alertRuleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("AlertRule not found with id: " + id));
    }

    public List<AlertRule> getAllAlertRules() {
        return alertRuleRepository.findAll();
    }

    public List<AlertRule> getEnabledAlertRules() {
        return alertRuleRepository.findByEnabledTrue();
    }

    public List<AlertRule> getAlertRulesByType(AlertRule.AlertType type) {
        return alertRuleRepository.findByType(type);
    }

    @Transactional
    public AlertRule updateAlertRule(Long id, AlertRuleUpdateRequest request) {
        AlertRule alertRule = getAlertRuleById(id);

        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            alertRule.setName(request.getName());
        }
        if (request.getType() != null) {
            alertRule.setType(request.getType());
        }
        if (request.getCondition() != null) {
            alertRule.setCondition(request.getCondition());
        }
        if (request.getThreshold() != null) {
            if (request.getThreshold().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Threshold must be positive");
            }
            alertRule.setThreshold(request.getThreshold());
        }
        if (request.getEnabled() != null) {
            alertRule.setEnabled(request.getEnabled());
        }

        return alertRuleRepository.save(alertRule);
    }

    @Transactional
    public void deleteAlertRule(Long id) {
        if (!alertRuleRepository.existsById(id)) {
            throw new RuntimeException("AlertRule not found with id: " + id);
        }
        alertRuleRepository.deleteById(id);
    }

    @Transactional
    public AlertRule toggleAlertRuleEnabled(Long id) {
        AlertRule alertRule = getAlertRuleById(id);
        alertRule.setEnabled(!alertRule.getEnabled());
        return alertRuleRepository.save(alertRule);
    }
}
