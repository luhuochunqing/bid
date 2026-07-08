package com.xiyu.bid.resources.service;

import com.xiyu.bid.alerts.dto.AlertHistoryCreateRequest;
import com.xiyu.bid.alerts.dto.AlertHistoryCreateResult;
import com.xiyu.bid.alerts.entity.AlertHistory;
import com.xiyu.bid.alerts.entity.AlertRule;
import com.xiyu.bid.alerts.repository.AlertRuleRepository;
import com.xiyu.bid.alerts.service.AlertHistoryService;
import com.xiyu.bid.alerts.service.AlertNotificationOrchestrator;
import com.xiyu.bid.resources.entity.CaBorrowApplicationEntity;
import com.xiyu.bid.resources.entity.CaCertificateEntity;
import com.xiyu.bid.resources.repository.CaBorrowApplicationRepository;
import com.xiyu.bid.resources.repository.CaCertificateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CaExpiryScanService 通知触发验证.
 *
 * <p>验证改造后：
 * <ul>
 *   <li>新建告警时（{@code created=true}）触发 {@link AlertNotificationOrchestrator#dispatchNotification}</li>
 *   <li>复用已有未处理告警时（{@code created=false}）不触发通知</li>
 *   <li>INACTIVE 证书被跳过，不创建告警也不触发通知</li>
 *   <li>借用逾期场景同样在新建告警时触发通知</li>
 * </ul>
 *
 * <p>status 回写逻辑由 {@link CaExpiryScanServiceTest} 覆盖，本测试聚焦通知触发路径。</p>
 */
@ExtendWith(MockitoExtension.class)
class CaExpiryScanServiceNotificationTest {

    @Mock
    private CaCertificateRepository certificateRepository;
    @Mock
    private CaBorrowApplicationRepository borrowRepository;
    @Mock
    private AlertRuleRepository alertRuleRepository;
    @Mock
    private AlertHistoryService alertHistoryService;
    @Mock
    private AlertNotificationOrchestrator alertNotificationOrchestrator;

    /**
     * 场景1：证书已过期，createAlertHistoryIfAbsent 返回 created=true → 触发通知，返回 1。
     */
    @Test
    void scanCertificateExpiry_newAlert_dispatchesNotification() {
        CaExpiryScanService service = newService();
        CaCertificateEntity cert = CaCertificateEntity.builder()
                .id(1L)
                .holderName("张三")
                .caType("CA")
                .expiryDate(LocalDate.now().minusDays(1))
                .status("ACTIVE")
                .build();
        AlertRule rule = caExpiryRule();
        when(certificateRepository.findAll()).thenReturn(List.of(cert));
        when(alertRuleRepository.findByType(any())).thenReturn(List.of(rule));
        stubCreateAsNew();

        int created = service.scanCertificateExpiry();

        assertThat(created).isEqualTo(1);
        verify(alertNotificationOrchestrator).dispatchNotification(any(), any(), any());
    }

    /**
     * 场景2：证书已过期，createAlertHistoryIfAbsent 返回 created=false（复用）→ 不触发通知，返回仍为 1。
     */
    @Test
    void scanCertificateExpiry_reusedAlert_doesNotDispatchNotification() {
        CaExpiryScanService service = newService();
        CaCertificateEntity cert = CaCertificateEntity.builder()
                .id(1L)
                .holderName("张三")
                .caType("CA")
                .expiryDate(LocalDate.now().minusDays(1))
                .status("ACTIVE")
                .build();
        AlertRule rule = caExpiryRule();
        when(certificateRepository.findAll()).thenReturn(List.of(cert));
        when(alertRuleRepository.findByType(any())).thenReturn(List.of(rule));
        stubCreateAsReused();

        int created = service.scanCertificateExpiry();

        assertThat(created).isEqualTo(1);
        verify(alertNotificationOrchestrator, never()).dispatchNotification(any(), any(), any());
    }

    /**
     * 场景3：借用已逾期，createAlertHistoryIfAbsent 返回 created=true → 触发通知。
     */
    @Test
    void scanBorrowOverdue_newAlert_dispatchesNotification() {
        CaExpiryScanService service = newService();
        CaBorrowApplicationEntity borrow = CaBorrowApplicationEntity.builder()
                .id(1L)
                .applicantName("李四")
                .expectedReturnDate(LocalDate.now().minusDays(1))
                .status("APPROVED")
                .build();
        AlertRule rule = caBorrowOverdueRule();
        when(borrowRepository.findAll()).thenReturn(List.of(borrow));
        when(alertRuleRepository.findByType(any())).thenReturn(List.of(rule));
        stubCreateAsNew();

        int created = service.scanBorrowOverdue();

        assertThat(created).isEqualTo(1);
        verify(alertNotificationOrchestrator).dispatchNotification(any(), any(), any());
    }

    /**
     * 场景4：INACTIVE 证书被跳过，createAlertHistoryIfAbsent 不被调用，dispatchNotification 也不被调用。
     */
    @Test
    void scanCertificateExpiry_inactiveCert_skipsAlertAndNotification() {
        CaExpiryScanService service = newService();
        CaCertificateEntity inactive = CaCertificateEntity.builder()
                .id(1L)
                .holderName("张三")
                .caType("CA")
                .expiryDate(LocalDate.now().minusDays(10))
                .status("INACTIVE")
                .build();
        when(certificateRepository.findAll()).thenReturn(List.of(inactive));

        int created = service.scanCertificateExpiry();

        assertThat(created).isEqualTo(0);
        verify(alertHistoryService, never()).createAlertHistoryIfAbsent(any());
        verify(alertNotificationOrchestrator, never()).dispatchNotification(any(), any(), any());
    }

    private CaExpiryScanService newService() {
        return new CaExpiryScanService(
                certificateRepository,
                borrowRepository,
                alertRuleRepository,
                alertHistoryService,
                alertNotificationOrchestrator
        );
    }

    private AlertRule caExpiryRule() {
        return AlertRule.builder()
                .id(1L)
                .name("CA证书到期提醒")
                .type(AlertRule.AlertType.CA_EXPIRY)
                .condition(AlertRule.ConditionType.LESS_THAN)
                .threshold(BigDecimal.valueOf(30))
                .enabled(true)
                .createdBy("system")
                .build();
    }

    private AlertRule caBorrowOverdueRule() {
        return AlertRule.builder()
                .id(1L)
                .name("CA借用归还提醒")
                .type(AlertRule.AlertType.CA_BORROW_OVERDUE)
                .condition(AlertRule.ConditionType.LESS_THAN)
                .threshold(BigDecimal.valueOf(30))
                .enabled(true)
                .createdBy("system")
                .build();
    }

    /** 桩 createAlertHistoryIfAbsent 返回"新建"结果。 */
    private void stubCreateAsNew() {
        AlertHistory history = AlertHistory.builder().id(100L).build();
        when(alertHistoryService.createAlertHistoryIfAbsent(any(AlertHistoryCreateRequest.class)))
                .thenReturn(new AlertHistoryCreateResult(history, true));
    }

    /** 桩 createAlertHistoryIfAbsent 返回"复用"结果。 */
    private void stubCreateAsReused() {
        AlertHistory history = AlertHistory.builder().id(100L).build();
        when(alertHistoryService.createAlertHistoryIfAbsent(any(AlertHistoryCreateRequest.class)))
                .thenReturn(new AlertHistoryCreateResult(history, false));
    }
}
