package com.xiyu.bid.alerts;

import com.xiyu.bid.alerts.dto.AlertRuleCreateRequest;
import com.xiyu.bid.alerts.entity.AlertRule;
import com.xiyu.bid.alerts.repository.AlertRuleRepository;
import com.xiyu.bid.alerts.service.AlertRuleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AlertRuleService 单元测试")
class AlertRuleServiceTest {

    @Mock
    private AlertRuleRepository alertRuleRepository;

    @InjectMocks
    private AlertRuleService alertRuleService;

    @Test
    @DisplayName("应支持创建资质到期提醒规则")
    void shouldCreateQualificationExpiryRule() {
        AlertRuleCreateRequest request = new AlertRuleCreateRequest();
        request.setName("资质到期前 30 天提醒");
        request.setType(AlertRule.AlertType.QUALIFICATION_EXPIRY);
        request.setCondition(AlertRule.ConditionType.LESS_THAN);
        request.setThreshold(new BigDecimal("30"));
        request.setEnabled(true);
        request.setCreatedBy("tester");

        AlertRule savedRule = AlertRule.builder()
                .id(21L)
                .name(request.getName())
                .type(request.getType())
                .condition(request.getCondition())
                .threshold(request.getThreshold())
                .enabled(true)
                .createdBy("tester")
                .build();

        when(alertRuleRepository.save(any(AlertRule.class))).thenReturn(savedRule);

        AlertRule result = alertRuleService.createAlertRule(request);

        assertThat(result.getType()).isEqualTo(AlertRule.AlertType.QUALIFICATION_EXPIRY);
        assertThat(result.getThreshold()).isEqualByComparingTo("30");
        verify(alertRuleRepository).save(any(AlertRule.class));
    }
}
