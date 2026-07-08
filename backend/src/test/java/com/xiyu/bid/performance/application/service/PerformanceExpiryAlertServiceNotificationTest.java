package com.xiyu.bid.performance.application.service;

import com.xiyu.bid.alerts.dto.AlertHistoryCreateRequest;
import com.xiyu.bid.alerts.dto.AlertHistoryCreateResult;
import com.xiyu.bid.alerts.entity.AlertHistory;
import com.xiyu.bid.alerts.entity.AlertRule;
import com.xiyu.bid.alerts.service.AlertNotificationOrchestrator;
import com.xiyu.bid.alerts.service.AlertRuleProvisioningService;
import com.xiyu.bid.performance.application.view.ExpiringPerformanceAlertView;
import com.xiyu.bid.performance.domain.model.PerformanceAlertConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PerformanceExpiryAlertService#createAlerts} 通知触发逻辑单元测试。
 *
 * <p>P1-3 / P1-4 改造后验证：
 * <ul>
 *   <li>使用 {@link AlertRuleProvisioningService#ensureRule} 而非直接访问 AlertRuleRepository</li>
 *   <li>使用 {@link AlertNotificationOrchestrator#createAndNotifyIfNew} 模板方法（统一 create + dispatch）</li>
 *   <li>created 计数按原逻辑（每次扫描命中即计数，不区分新建/复用）</li>
 *   <li>createAndNotifyIfNew 传入正确的 alertHistory 和 alertRule</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PerformanceExpiryAlertService 通知触发单元测试")
class PerformanceExpiryAlertServiceNotificationTest {

    @Mock
    private AlertRuleProvisioningService alertRuleProvisioningService;
    @Mock
    private AlertNotificationOrchestrator alertNotificationOrchestrator;
    @Mock
    private ScanExpiringPerformanceAppService scanService;

    @InjectMocks
    private PerformanceExpiryAlertService service;

    private PerformanceAlertConfig config;
    private AlertRule rule;
    private ExpiringPerformanceAlertView record;
    private AlertHistory alertHistory;

    @BeforeEach
    void setUp() {
        config = new PerformanceAlertConfig(1L, 180, 90, true);

        rule = AlertRule.builder()
                .id(10L)
                .name("业绩合同到期提醒")
                .type(AlertRule.AlertType.PERFORMANCE_EXPIRY)
                .condition(AlertRule.ConditionType.LESS_THAN)
                .threshold(BigDecimal.valueOf(180))
                .enabled(true)
                .createdBy("system")
                .build();

        record = ExpiringPerformanceAlertView.builder()
                .recordId(500L)
                .contractName("测试合同")
                .relatedId("Performance:500:2026-12-31")
                .message("测试业绩到期提醒")
                .build();

        alertHistory = AlertHistory.builder()
                .id(601L)
                .ruleId(10L)
                .level(AlertHistory.AlertLevel.HIGH)
                .message("测试业绩到期提醒")
                .relatedId("Performance:500:2026-12-31")
                .resolved(false)
                .build();

        // P1-4: 共享 provisioning service
        when(alertRuleProvisioningService.ensureRule(
                AlertRule.AlertType.PERFORMANCE_EXPIRY, "业绩合同到期提醒", config.alertDaysSoe()))
                .thenReturn(rule);
        when(scanService.scan(config)).thenReturn(List.of(record));
    }

    @Test
    @DisplayName("新建告警(created=true)时调用 createAndNotifyIfNew，created 计数为 1")
    void shouldCallCreateAndNotifyIfNewWhenAlertCreated() {
        when(alertNotificationOrchestrator.createAndNotifyIfNew(
                any(AlertHistoryCreateRequest.class), eq(rule), any(Map.class)))
                .thenReturn(new AlertHistoryCreateResult(alertHistory, true));

        int created = service.createAlerts(config);

        // 验证 createAndNotifyIfNew 被调用，传入正确的 rule
        verify(alertNotificationOrchestrator)
                .createAndNotifyIfNew(any(AlertHistoryCreateRequest.class), eq(rule), any(Map.class));
        // 计数保留：每次扫描命中即计数
        assertThat(created).isEqualTo(1);
    }

    @Test
    @DisplayName("复用已有告警(created=false)时仍调用 createAndNotifyIfNew，created 计数仍为 1")
    void shouldStillCallCreateAndNotifyIfNewWhenAlertReused() {
        when(alertNotificationOrchestrator.createAndNotifyIfNew(
                any(AlertHistoryCreateRequest.class), eq(rule), any(Map.class)))
                .thenReturn(new AlertHistoryCreateResult(alertHistory, false));

        int created = service.createAlerts(config);

        // P1-3: createAndNotifyIfNew 是统一入口，无论新建/复用都会调用
        verify(alertNotificationOrchestrator)
                .createAndNotifyIfNew(any(AlertHistoryCreateRequest.class), eq(rule), any(Map.class));
        // 计数保留：不区分新建/复用
        assertThat(created).isEqualTo(1);
    }
}
