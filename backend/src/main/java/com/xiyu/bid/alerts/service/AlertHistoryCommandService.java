package com.xiyu.bid.alerts.service;

import com.xiyu.bid.alerts.dto.AlertHistoryResponse;
import com.xiyu.bid.alerts.entity.AlertHistory;
import com.xiyu.bid.alerts.repository.AlertHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AlertHistoryCommandService {

    private final AlertHistoryRepository alertHistoryRepository;
    private final AlertHistoryQueryService alertHistoryQueryService;
    private final AlertLifecyclePolicy alertLifecyclePolicy = new AlertLifecyclePolicy();

    @Transactional
    public AlertHistoryResponse acknowledgeAlertHistory(Long id) {
        AlertHistory alertHistory = alertHistoryQueryService.getAlertHistoryById(id);
        alertLifecyclePolicy.acknowledge(alertHistory, LocalDateTime.now());
        AlertHistory saved = alertHistoryRepository.save(alertHistory);
        return alertHistoryQueryService.toResponse(saved);
    }

    @Transactional
    public AlertHistoryResponse resolveAlertHistory(Long id) {
        AlertHistory alertHistory = alertHistoryQueryService.getAlertHistoryById(id);
        alertLifecyclePolicy.resolve(alertHistory, LocalDateTime.now());
        AlertHistory saved = alertHistoryRepository.save(alertHistory);
        return alertHistoryQueryService.toResponse(saved);
    }

    @Transactional
    public int deleteOldResolvedAlertHistories(LocalDateTime cutoffDate) {
        return alertHistoryRepository.deleteByResolvedTrueAndResolvedAtBefore(cutoffDate);
    }
}
