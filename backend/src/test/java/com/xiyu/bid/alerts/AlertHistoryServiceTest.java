package com.xiyu.bid.alerts;

import com.xiyu.bid.alerts.dto.AlertHistoryCreateRequest;
import com.xiyu.bid.alerts.entity.AlertHistory;
import com.xiyu.bid.alerts.entity.AlertRule;
import com.xiyu.bid.alerts.repository.AlertHistoryRepository;
import com.xiyu.bid.alerts.repository.AlertRuleRepository;
import com.xiyu.bid.alerts.service.AlertHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AlertHistoryService 单元测试")
class AlertHistoryServiceTest {

    @Mock
    private AlertHistoryRepository alertHistoryRepository;

    @Mock
    private AlertRuleRepository alertRuleRepository;

    @InjectMocks
    private AlertHistoryService alertHistoryService;

    private AlertRule qualificationRule;

    @BeforeEach
    void setUp() {
        qualificationRule = AlertRule.builder()
                .id(11L)
                .name("资质到期提醒")
                .type(AlertRule.AlertType.QUALIFICATION_EXPIRY)
                .condition(AlertRule.ConditionType.LESS_THAN)
                .threshold(new BigDecimal("30"))
                .enabled(true)
                .createdBy("tester")
                .build();
    }

    @Test
    @DisplayName("同一规则和关联对象的未解决提醒应去重")
    void shouldDeduplicateUnresolvedAlertByRuleAndRelatedId() {
        AlertHistoryCreateRequest request = new AlertHistoryCreateRequest();
        request.setRuleId(11L);
        request.setLevel(AlertHistory.AlertLevel.HIGH);
        request.setMessage("资质将在 7 天后到期");
        request.setRelatedId("Qualification:5:2026-04-26");

        AlertHistory existingAlert = AlertHistory.builder()
                .id(101L)
                .ruleId(11L)
                .level(AlertHistory.AlertLevel.HIGH)
                .message("资质将在 7 天后到期")
                .relatedId("Qualification:5:2026-04-26")
                .resolved(false)
                .createdAt(LocalDateTime.now().minusHours(1))
                .build();

        when(alertRuleRepository.findById(11L)).thenReturn(Optional.of(qualificationRule));
        when(alertHistoryRepository.findFirstByRuleIdAndRelatedIdAndResolvedFalseOrderByCreatedAtDesc(
                11L, "Qualification:5:2026-04-26")).thenReturn(Optional.of(existingAlert));

        AlertHistory result = alertHistoryService.createAlertHistory(request);

        assertThat(result).isSameAs(existingAlert);
        verify(alertHistoryRepository, never()).save(any(AlertHistory.class));
    }

    @Test
    @DisplayName("已解决的旧提醒不应阻止重新生成提醒")
    void shouldCreateNewAlertWhenPreviousAlertResolved() {
        AlertHistoryCreateRequest request = new AlertHistoryCreateRequest();
        request.setRuleId(11L);
        request.setLevel(AlertHistory.AlertLevel.HIGH);
        request.setMessage("资质将在 3 天后到期");
        request.setRelatedId("Qualification:5:2026-04-22");

        AlertHistory savedAlert = AlertHistory.builder()
                .id(102L)
                .ruleId(11L)
                .level(AlertHistory.AlertLevel.HIGH)
                .message("资质将在 3 天后到期")
                .relatedId("Qualification:5:2026-04-22")
                .resolved(false)
                .build();

        when(alertRuleRepository.findById(11L)).thenReturn(Optional.of(qualificationRule));
        when(alertHistoryRepository.findFirstByRuleIdAndRelatedIdAndResolvedFalseOrderByCreatedAtDesc(
                11L, "Qualification:5:2026-04-22")).thenReturn(Optional.empty());
        when(alertHistoryRepository.save(any(AlertHistory.class))).thenReturn(savedAlert);

        AlertHistory result = alertHistoryService.createAlertHistory(request);

        assertThat(result.getId()).isEqualTo(102L);
        assertThat(result.getRelatedId()).isEqualTo("Qualification:5:2026-04-22");
        verify(alertHistoryRepository).save(any(AlertHistory.class));
    }
}
