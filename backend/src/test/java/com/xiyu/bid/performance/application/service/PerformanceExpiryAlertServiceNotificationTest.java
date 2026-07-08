package com.xiyu.bid.performance.application.service;

import com.xiyu.bid.alerts.dto.AlertHistoryCreateRequest;
import com.xiyu.bid.alerts.dto.AlertHistoryCreateResult;
import com.xiyu.bid.alerts.entity.AlertHistory;
import com.xiyu.bid.alerts.entity.AlertRule;
import com.xiyu.bid.alerts.repository.AlertRuleRepository;
import com.xiyu.bid.alerts.service.AlertHistoryService;
import com.xiyu.bid.alerts.service.AlertNotificationOrchestrator;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PerformanceExpiryAlertService#createAlerts} 通知触发逻辑单元测试。
 *
 * <p>验证核心契约：
 * <ul>
 *   <li>created=true 时调用 {@link AlertNotificationOrchestrator#dispatchNotification}</li>
 *   <li>created=false 时不调用 dispatchNotification</li>
 *   <li>created 计数按原逻辑（每次扫描命中即计数，不区分新建/复用）</li>
 *   <li>dispatchNotification 传入正确的 alertHistory 和 alertRule</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PerformanceExpiryAlertService 通知触发单元测试")
class PerformanceExpiryAlertServiceNotificationTest {

    @Mock
    private AlertRuleRepository alertRuleRepository;
    @Mock
    private AlertHistoryService alertHistoryService;
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

        when(alertRuleRepository.findByType(AlertRule.AlertType.PERFORMANCE_EXPIRY))
                .thenReturn(List.of(rule));
        when(scanService.scan(config)).thenReturn(List.of(record));
    }

    @Test
    @DisplayName("新建告警(created=true)时触发通知，created 计数为 1")
    void shouldDispatchNotificationWhenAlertCreated() {
        when(alertHistoryService.createAlertHistoryIfAbsent(any(AlertHistoryCreateRequest.class)))
                .thenReturn(new AlertHistoryCreateResult(alertHistory, true));

        int created = service.createAlerts(config);

        // 验证通知被触发，传入正确的 alertHistory 和 rule
        verify(alertNotificationOrchestrator).dispatchNotification(eq(alertHistory), eq(rule), any(Map.class));
        // 计数保留：每次扫描命中即计数
        assertThat(created).isEqualTo(1);
    }

    @Test
    @DisplayName("复用已有告警(created=false)时不触发通知，created 计数仍为 1")
    void shouldNotDispatchNotificationWhenAlertReused() {
        when(alertHistoryService.createAlertHistoryIfAbsent(any(AlertHistoryCreateRequest.class)))
                .thenReturn(new AlertHistoryCreateResult(alertHistory, false));

        int created = service.createAlerts(config);

        // 验证通知未触发
        verify(alertNotificationOrchestrator, never()).dispatchNotification(any(), any(), any());
        // 计数保留：不区分新建/复用
        assertThat(created).isEqualTo(1);
    }
}
