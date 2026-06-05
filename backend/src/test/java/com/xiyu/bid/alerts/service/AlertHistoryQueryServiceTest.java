package com.xiyu.bid.alerts.service;

import com.xiyu.bid.alerts.dto.AlertHistoryResponse;
import com.xiyu.bid.alerts.entity.AlertHistory;
import com.xiyu.bid.alerts.entity.AlertRule;
import com.xiyu.bid.alerts.repository.AlertHistoryRepository;
import com.xiyu.bid.alerts.repository.AlertRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertHistoryQueryServiceTest {

    @Mock
    private AlertHistoryRepository alertHistoryRepository;

    @Mock
    private AlertRuleRepository alertRuleRepository;

    private AlertHistoryQueryService alertHistoryQueryService;

    @BeforeEach
    void setUp() {
        alertHistoryQueryService = new AlertHistoryQueryService(alertHistoryRepository, alertRuleRepository);
    }

    @Test
    void getAllAlertHistories_ShouldApplyStatusLevelAndRuleFilters() {
        AlertHistory active = AlertHistory.builder()
                .id(1L)
                .ruleId(11L)
                .level(AlertHistory.AlertLevel.HIGH)
                .message("active")
                .relatedId("P-1")
                .resolved(false)
                .createdAt(LocalDateTime.of(2026, 4, 21, 8, 0))
                .build();
        AlertHistory acknowledged = AlertHistory.builder()
                .id(2L)
                .ruleId(11L)
                .level(AlertHistory.AlertLevel.HIGH)
                .message("ack")
                .relatedId("P-2")
                .resolved(false)
                .acknowledgedAt(LocalDateTime.of(2026, 4, 21, 9, 0))
                .createdAt(LocalDateTime.of(2026, 4, 21, 9, 0))
                .build();
        AlertRule rule = AlertRule.builder()
                .id(11L)
                .name("保证金提醒")
                .type(AlertRule.AlertType.DEPOSIT_RETURN)
                .condition(AlertRule.ConditionType.LESS_THAN)
                .threshold(BigDecimal.ONE)
                .enabled(true)
                .build();

        when(alertHistoryRepository.findAll(Sort.by("createdAt").descending()))
                .thenReturn(List.of(acknowledged, active));
        when(alertRuleRepository.findAllById(List.of(11L))).thenReturn(List.of(rule));
        Page<AlertHistoryResponse> page = alertHistoryQueryService.getAllAlertHistories(
                PageRequest.of(0, 10, Sort.by("createdAt").descending()),
                "ACKNOWLEDGED",
                AlertHistory.AlertLevel.HIGH,
                11L,
                "P-2"
        );

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getId()).isEqualTo(2L);
        assertThat(page.getContent().get(0).getStatus()).isEqualTo("ACKNOWLEDGED");
    }

    @Test
    void getUnresolvedAlertHistories_ShouldOnlyReturnActiveAlerts() {
        AlertHistory active = AlertHistory.builder()
                .id(1L)
                .ruleId(11L)
                .level(AlertHistory.AlertLevel.HIGH)
                .message("active")
                .resolved(false)
                .build();
        AlertHistory resolved = AlertHistory.builder()
                .id(2L)
                .ruleId(11L)
                .level(AlertHistory.AlertLevel.HIGH)
                .message("resolved")
                .resolved(true)
                .resolvedAt(LocalDateTime.of(2026, 4, 21, 9, 0))
                .build();
        AlertRule rule = AlertRule.builder()
                .id(11L)
                .name("保证金提醒")
                .type(AlertRule.AlertType.DEPOSIT_RETURN)
                .condition(AlertRule.ConditionType.LESS_THAN)
                .threshold(BigDecimal.ONE)
                .enabled(true)
                .build();

        when(alertHistoryRepository.findAll(Sort.by("createdAt").descending()))
                .thenReturn(List.of(active, resolved));
        when(alertRuleRepository.findAllById(List.of(11L))).thenReturn(List.of(rule));

        Page<AlertHistoryResponse> page = alertHistoryQueryService.getUnresolvedAlertHistories(
                PageRequest.of(0, 10, Sort.by("createdAt").descending())
        );

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getId()).isEqualTo(1L);
        assertThat(page.getContent().get(0).getStatus()).isEqualTo("ACTIVE");
    }
}
