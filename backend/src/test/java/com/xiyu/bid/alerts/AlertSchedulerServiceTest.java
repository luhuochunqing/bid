package com.xiyu.bid.alerts;

import com.xiyu.bid.alertdispatch.service.AlertRuleDispatchService;
import com.xiyu.bid.alertdispatch.service.AlertSchedulerService;
import com.xiyu.bid.alerts.entity.AlertRule;
import com.xiyu.bid.alerts.repository.AlertRuleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AlertSchedulerService 单元测试")
class AlertSchedulerServiceTest {

    @Mock private AlertRuleRepository alertRuleRepository;
    @Mock private AlertRuleDispatchService alertRuleDispatchService;

    @InjectMocks
    private AlertSchedulerService alertSchedulerService;

    @Test
    @DisplayName("中央调度器应把启用规则委托给规则分发器")
    void shouldDelegateEnabledRuleToDispatchService() {
        AlertRule rule = AlertRule.builder()
                .id(31L)
                .name("保证金退还提醒")
                .type(AlertRule.AlertType.DEPOSIT_RETURN)
                .condition(AlertRule.ConditionType.LESS_THAN)
                .threshold(new BigDecimal("7"))
                .enabled(true)
                .createdBy("tester")
                .build();

        when(alertRuleRepository.findByEnabledTrue()).thenReturn(List.of(rule));

        alertSchedulerService.checkAlertRules();

        verify(alertRuleDispatchService).dispatch(rule);
    }
}
