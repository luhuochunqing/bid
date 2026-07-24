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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CaExpiryScanService 边界与防御场景单测。
 *
 * <p>覆盖 PR !2193 引入的缺口：
 * <ul>
 *   <li>证书即将到期文案包含"关联平台"和"CA类型"字段</li>
 *   <li>借用记录关联证书不存在时跳过，避免 NPE 中断扫描</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class CaExpiryScanServiceEdgeCaseTest {

    @Mock
    private CaCertificateRepository certificateRepository;
    @Mock
    private CaBorrowApplicationRepository borrowRepository;
    @Mock
    private AlertRuleProvisioningService alertRuleProvisioningService;
    @Mock
    private AlertNotificationOrchestrator alertNotificationOrchestrator;

    @Test
    @DisplayName("证书即将到期：文案必须包含关联平台和 CA 类型字段")
    void scanCertificateExpiry_expiringSoon_messageContainsNewFields() {
        CaExpiryScanService service = newService();
        CaCertificateEntity cert = CaCertificateEntity.builder()
                .id(2L)
                .holderName("李四")
                .caType("ELECTRONIC_CA")
                .relatedPlatforms("")
                .expiryDate(LocalDate.now().plusDays(7))
                .status("ACTIVE")
                .build();
        AlertRule rule = caExpiryRule();
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
        assertThat(message).contains("关联平台：无");
        assertThat(message).contains("CA类型：电子CA");
        assertThat(message).contains("李四");
        assertThat(message).contains("还有 7 天到期");
    }

    @Test
    @DisplayName("借用记录关联证书不存在：跳过该条记录且不抛异常")
    void scanBorrowOverdue_missingCertificate_skipsWithoutNpe() {
        CaExpiryScanService service = newService();
        CaBorrowApplicationEntity borrow = CaBorrowApplicationEntity.builder()
                .id(2L)
                .caCertificateId(999L)
                .applicantName("王五")
                .expectedReturnDate(LocalDate.now().minusDays(1))
                .status("APPROVED")
                .build();
        AlertRule rule = caBorrowOverdueRule();
        when(borrowRepository.findByStatusOrderByCreatedAtDesc("APPROVED")).thenReturn(List.of(borrow));
        // 批量查询返回空，模拟证书不存在
        when(certificateRepository.findAllById(any(java.util.Collection.class))).thenReturn(List.of());
        when(alertRuleProvisioningService.ensureRule(
                eq(AlertRule.AlertType.CA_BORROW_OVERDUE), anyString(), anyInt())).thenReturn(rule);

        int created = service.scanBorrowOverdue();

        assertThat(created).isEqualTo(0);
        verify(alertNotificationOrchestrator, never())
                .createAndNotifyIfNew(any(), any(), any());
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

    private void stubCreateAsNew() {
        AlertHistory history = AlertHistory.builder().id(100L).build();
        when(alertNotificationOrchestrator.createAndNotifyIfNew(
                any(AlertHistoryCreateRequest.class), any(AlertRule.class), any()))
                .thenReturn(new AlertHistoryCreateResult(history, true));
    }
}
