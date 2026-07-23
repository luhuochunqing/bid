package com.xiyu.bid.resources.service;

import com.xiyu.bid.alerts.dto.AlertHistoryCreateRequest;
import com.xiyu.bid.alerts.dto.AlertHistoryCreateResult;
import com.xiyu.bid.alerts.entity.AlertHistory;
import com.xiyu.bid.alerts.entity.AlertRule;
import com.xiyu.bid.alerts.service.AlertNotificationOrchestrator;
import com.xiyu.bid.alerts.service.AlertRuleProvisioningService;
import com.xiyu.bid.resources.entity.CaBorrowApplicationEntity;
import com.xiyu.bid.resources.entity.CaCertificateEntity;
import com.xiyu.bid.resources.repository.CaBorrowApplicationRepository;
import com.xiyu.bid.resources.repository.CaCertificateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CaExpiryScanService 通知触发验证.
 *
 * <p>P1-3 / P1-4 / P1-7 改造后验证：
 * <ul>
 *   <li>使用 {@link AlertRuleProvisioningService#ensureRule} 而非直接访问 AlertRuleRepository</li>
 *   <li>使用 {@link AlertNotificationOrchestrator#createAndNotifyIfNew} 模板方法</li>
 *   <li>P1-7: 查询时直接过滤 INACTIVE 证书和 APPROVED 借用，不再 findAll 后内存过滤</li>
 *   <li>新建告警时（{@code created=true}）调用 createAndNotifyIfNew</li>
 *   <li>复用已有未处理告警时（{@code created=false}）也调用 createAndNotifyIfNew（由 Orchestrator 决定是否发通知）</li>
 *   <li>查询未返回 INACTIVE 证书时不创建告警</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class CaExpiryScanServiceNotificationTest {

    @Mock
    private CaCertificateRepository certificateRepository;
    @Mock
    private CaBorrowApplicationRepository borrowRepository;
    @Mock
    private AlertRuleProvisioningService alertRuleProvisioningService;
    @Mock
    private AlertNotificationOrchestrator alertNotificationOrchestrator;

    /**
     * 场景1：证书已过期，createAndNotifyIfNew 返回 created=true → 调用模板方法，返回 1。
     *
     * <p>验证通知文案中包含"关联平台"和"CA类型"字段，便于操作人识别具体 CA。
     * relatedPlatforms 为空时文案应显示"无"。
     */
    @Test
    void scanCertificateExpiry_newAlert_callsCreateAndNotifyIfNew() {
        CaExpiryScanService service = newService();
        CaCertificateEntity cert = CaCertificateEntity.builder()
                .id(1L)
                .holderName("张三")
                .caType("ENTITY_CA")
                .relatedPlatforms("招标网,公共资源")
                .expiryDate(LocalDate.now().minusDays(1))
                .status("ACTIVE")
                .build();
        AlertRule rule = caExpiryRule();
        // P1-7: findByStatusNot("INACTIVE") 替代 findAll()
        when(certificateRepository.findByStatusNot("INACTIVE")).thenReturn(List.of(cert));
        when(alertRuleProvisioningService.ensureRule(
                eq(AlertRule.AlertType.CA_EXPIRY), anyString(), anyInt())).thenReturn(rule);
        stubCreateAsNew();

        int created = service.scanCertificateExpiry();

        assertThat(created).isEqualTo(1);
        ArgumentCaptor<AlertHistoryCreateRequest> reqCaptor = ArgumentCaptor.forClass(AlertHistoryCreateRequest.class);
        verify(alertNotificationOrchestrator)
                .createAndNotifyIfNew(reqCaptor.capture(), eq(rule), any());
        String message = reqCaptor.getValue().getMessage();
        assertThat(message).contains("关联平台：招标网,公共资源");
        assertThat(message).contains("CA类型：实体CA");
        assertThat(message).contains("张三");
    }

    /**
     * 场景2：证书已过期，createAndNotifyIfNew 返回 created=false（复用）→ 仍调用模板方法，返回仍为 1。
     */
    @Test
    void scanCertificateExpiry_reusedAlert_stillCallsCreateAndNotifyIfNew() {
        CaExpiryScanService service = newService();
        CaCertificateEntity cert = CaCertificateEntity.builder()
                .id(1L)
                .holderName("张三")
                .caType("CA")
                .expiryDate(LocalDate.now().minusDays(1))
                .status("ACTIVE")
                .build();
        AlertRule rule = caExpiryRule();
        when(certificateRepository.findByStatusNot("INACTIVE")).thenReturn(List.of(cert));
        when(alertRuleProvisioningService.ensureRule(
                eq(AlertRule.AlertType.CA_EXPIRY), anyString(), anyInt())).thenReturn(rule);
        stubCreateAsReused();

        int created = service.scanCertificateExpiry();

        // P1-3: createAndNotifyIfNew 是统一入口，无论新建/复用都会调用
        assertThat(created).isEqualTo(1);
        verify(alertNotificationOrchestrator)
                .createAndNotifyIfNew(any(AlertHistoryCreateRequest.class), eq(rule), any());
    }

    /**
     * 场景3：借用已逾期，createAndNotifyIfNew 返回 created=true → 调用模板方法。
     *
     * <p>验证通知文案中包含"关联平台"和"CA类型"字段，便于操作人识别具体 CA。
     */
    @Test
    void scanBorrowOverdue_newAlert_callsCreateAndNotifyIfNew() {
        CaExpiryScanService service = newService();
        CaCertificateEntity cert = CaCertificateEntity.builder()
                .id(10L)
                .holderName("张三公司")
                .caType("ENTITY_CA")
                .relatedPlatforms("A平台,B平台")
                .build();
        CaBorrowApplicationEntity borrow = CaBorrowApplicationEntity.builder()
                .id(1L)
                .caCertificateId(10L)
                .applicantName("李四")
                .expectedReturnDate(LocalDate.now().minusDays(1))
                .status("APPROVED")
                .build();
        AlertRule rule = caBorrowOverdueRule();
        // P1-7: findByStatusOrderByCreatedAtDesc("APPROVED") 替代 findAll() + 内存过滤
        when(borrowRepository.findByStatusOrderByCreatedAtDesc("APPROVED")).thenReturn(List.of(borrow));
        // 批量查询 stub（替代循环内 findById）
        when(certificateRepository.findAllById(any(java.util.Collection.class))).thenReturn(List.of(cert));
        when(alertRuleProvisioningService.ensureRule(
                eq(AlertRule.AlertType.CA_BORROW_OVERDUE), anyString(), anyInt())).thenReturn(rule);
        stubCreateAsNew();

        int created = service.scanBorrowOverdue();

        assertThat(created).isEqualTo(1);
        ArgumentCaptor<AlertHistoryCreateRequest> reqCaptor = ArgumentCaptor.forClass(AlertHistoryCreateRequest.class);
        verify(alertNotificationOrchestrator)
                .createAndNotifyIfNew(reqCaptor.capture(), eq(rule), any());
        String message = reqCaptor.getValue().getMessage();
        assertThat(message).contains("关联平台：A平台,B平台");
        assertThat(message).contains("CA类型：实体CA");
        assertThat(message).contains("张三公司");
        assertThat(message).contains("李四");
    }

    /**
     * 场景4：P1-7 查询未返回 INACTIVE 证书（已通过 query 过滤），createAndNotifyIfNew 不被调用。
     *
     * <p>改造前：findAll() 返回 INACTIVE 证书，循环里 continue 跳过。
     * 改造后：findByStatusNot("INACTIVE") 直接不返回 INACTIVE，循环不会处理它们。</p>
     */
    @Test
    void scanCertificateExpiry_queryReturnsEmpty_noAlertCreated() {
        CaExpiryScanService service = newService();
        AlertRule rule = caExpiryRule();
        when(certificateRepository.findByStatusNot("INACTIVE")).thenReturn(List.of());
        when(alertRuleProvisioningService.ensureRule(
                eq(AlertRule.AlertType.CA_EXPIRY), anyString(), anyInt())).thenReturn(rule);

        int created = service.scanCertificateExpiry();

        assertThat(created).isEqualTo(0);
        verify(alertNotificationOrchestrator, never())
                .createAndNotifyIfNew(any(), any(), any());
    }

    /**
     * CO-546: 证书即将到期，payload 中必须包含 custodianId，供通知编排器将 CA 保管员加入接收人。
     *
     * <p>定时扫描路径此前仅通知投标管理员/投标组长（角色广播），缺少 CA 保管员。
     * 业务需求要求通知发送给 CA 保管员（与 returnBorrow 路径的 CaNotificationDispatcher 一致）。
     * 通过在 payload 中携带 custodianId，AlertNotificationOrchestrator 可将其加入接收人列表。</p>
     */
    @Test
    void scanCertificateExpiry_payloadContainsCustodianId() {
        CaExpiryScanService service = newService();
        CaCertificateEntity cert = CaCertificateEntity.builder()
                .id(1L)
                .holderName("张三")
                .caType("CA")
                .expiryDate(LocalDate.now().plusDays(5))
                .status("ACTIVE")
                .custodianId(99L)
                .custodianName("保管员99")
                .build();
        AlertRule rule = caExpiryRule();
        when(certificateRepository.findByStatusNot("INACTIVE")).thenReturn(List.of(cert));
        when(alertRuleProvisioningService.ensureRule(
                eq(AlertRule.AlertType.CA_EXPIRY), anyString(), anyInt())).thenReturn(rule);
        stubCreateAsNew();

        service.scanCertificateExpiry();

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(alertNotificationOrchestrator)
                .createAndNotifyIfNew(any(AlertHistoryCreateRequest.class), eq(rule), payloadCaptor.capture());
        Map<String, Object> payload = payloadCaptor.getValue();
        assertThat(payload).containsKey(com.xiyu.bid.alerts.domain.AlertMessagePolicy.PAYLOAD_KEY_CUSTODIAN_ID);
        assertThat(payload.get(com.xiyu.bid.alerts.domain.AlertMessagePolicy.PAYLOAD_KEY_CUSTODIAN_ID))
                .isEqualTo(99L);
    }

    private CaExpiryScanService newService() {
        return new CaExpiryScanService(
                certificateRepository,
                borrowRepository,
                alertRuleProvisioningService,
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

    /** 桩 createAndNotifyIfNew 返回"新建"结果。 */
    private void stubCreateAsNew() {
        AlertHistory history = AlertHistory.builder().id(100L).build();
        when(alertNotificationOrchestrator.createAndNotifyIfNew(
                any(AlertHistoryCreateRequest.class), any(AlertRule.class), any()))
                .thenReturn(new AlertHistoryCreateResult(history, true));
    }

    /** 桩 createAndNotifyIfNew 返回"复用"结果。 */
    private void stubCreateAsReused() {
        AlertHistory history = AlertHistory.builder().id(100L).build();
        when(alertNotificationOrchestrator.createAndNotifyIfNew(
                any(AlertHistoryCreateRequest.class), any(AlertRule.class), any()))
                .thenReturn(new AlertHistoryCreateResult(history, false));
    }
}
