package com.xiyu.bid.alerts.service;

import com.xiyu.bid.alerts.dto.AlertHistoryResponse;
import com.xiyu.bid.alerts.dto.AlertStatisticsResponse;
import com.xiyu.bid.alerts.entity.AlertHistory;
import com.xiyu.bid.alerts.entity.AlertRule;
import com.xiyu.bid.alerts.repository.AlertHistoryRepository;
import com.xiyu.bid.alerts.repository.AlertRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AlertHistoryQueryService {

    private final AlertHistoryRepository alertHistoryRepository;
    private final AlertRuleRepository alertRuleRepository;

    public AlertHistoryResponse getAlertHistoryResponseById(Long id) {
        return toResponse(getAlertHistoryById(id));
    }

    public Page<AlertHistoryResponse> getAllAlertHistories(
            Pageable pageable,
            String status,
            AlertHistory.AlertLevel level,
            Long ruleId,
            String relatedId
    ) {
        List<AlertHistory> all = alertHistoryRepository.findAll(pageable.getSort());
        Map<Long, AlertRule> rules = loadRules(all);

        Predicate<AlertHistory> filter = item -> true;
        if (status != null && !status.isBlank()) {
            filter = filter.and(item -> AlertHistoryViewAssembler.resolveStatus(item).equalsIgnoreCase(status));
        }
        if (level != null) {
            filter = filter.and(item -> item.getLevel() == level);
        }
        if (ruleId != null) {
            filter = filter.and(item -> ruleId.equals(item.getRuleId()));
        }
        if (relatedId != null && !relatedId.isBlank()) {
            filter = filter.and(item -> relatedId.equals(item.getRelatedId()));
        }

        List<AlertHistoryResponse> filtered = all.stream()
                .filter(filter)
                .map(item -> AlertHistoryViewAssembler.toResponse(item, rules.get(item.getRuleId())))
                .toList();

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        List<AlertHistoryResponse> content = start >= filtered.size() ? List.of() : filtered.subList(start, end);
        return new PageImpl<>(content, pageable, filtered.size());
    }

    public Page<AlertHistoryResponse> getUnresolvedAlertHistories(Pageable pageable) {
        return getAllAlertHistories(pageable, "ACTIVE", null, null, null);
    }

    public AlertStatisticsResponse getAlertStatistics() {
        return AlertStatisticsResponse.builder()
                .totalAlerts(alertHistoryRepository.count())
                .unresolvedAlerts(alertHistoryRepository.countByResolvedFalse())
                .highAlerts(alertHistoryRepository.countByLevel(AlertHistory.AlertLevel.HIGH))
                .mediumAlerts(alertHistoryRepository.countByLevel(AlertHistory.AlertLevel.MEDIUM))
                .lowAlerts(alertHistoryRepository.countByLevel(AlertHistory.AlertLevel.LOW))
                .criticalAlerts(alertHistoryRepository.countByLevel(AlertHistory.AlertLevel.CRITICAL))
                .build();
    }

    public AlertHistoryResponse toResponse(AlertHistory entity) {
        AlertRule rule = alertRuleRepository.findById(entity.getRuleId()).orElse(null);
        return AlertHistoryViewAssembler.toResponse(entity, rule);
    }

    AlertHistory getAlertHistoryById(Long id) {
        return alertHistoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("AlertHistory not found with id: " + id));
    }

    private Map<Long, AlertRule> loadRules(List<AlertHistory> all) {
        return alertRuleRepository.findAllById(
                all.stream().map(AlertHistory::getRuleId).distinct().toList()
        ).stream().collect(Collectors.toMap(AlertRule::getId, item -> item));
    }
}
