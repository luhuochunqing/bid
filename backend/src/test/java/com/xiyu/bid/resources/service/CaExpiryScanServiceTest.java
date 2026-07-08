package com.xiyu.bid.resources.service;

import com.xiyu.bid.alerts.dto.AlertHistoryCreateRequest;
import com.xiyu.bid.alerts.dto.AlertHistoryCreateResult;
import com.xiyu.bid.alerts.entity.AlertHistory;
import com.xiyu.bid.alerts.entity.AlertRule;
import com.xiyu.bid.alerts.repository.AlertRuleRepository;
import com.xiyu.bid.alerts.service.AlertHistoryService;
import com.xiyu.bid.alerts.service.AlertNotificationOrchestrator;
import com.xiyu.bid.resources.entity.CaCertificateEntity;
import com.xiyu.bid.resources.repository.CaBorrowApplicationRepository;
import com.xiyu.bid.resources.repository.CaCertificateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CO-477: CaExpiryScanService 定时回写 status 字段验证.
 *
 * <p>验证扫描时按 expiryDate 实时重算 status 并持久化（INACTIVE 不被覆盖），
 * 且仅在 status 变化时才调用 save，避免不必要的 UPDATE。
 *
 * <p>注：本测试聚焦 status 回写逻辑，通知触发由 {@link CaExpiryScanServiceNotificationTest} 覆盖。
 * 此处将 {@link AlertHistoryService#createAlertHistoryIfAbsent} 统一桩为"新建"，
 * 避免因真实调用返回 null 而引发 NPE。</p>
 */
@ExtendWith(MockitoExtension.class)
class CaExpiryScanServiceTest {

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

    @Test
    void scanCertificateExpiry_staleExpiringStatus_persistedAsExpired() {
        // Bug 场景：数据库存 EXPIRING，实际已过到期日 2 天
        CaExpiryScanService service = newService();
        CaCertificateEntity stale = caCertificate(1L, LocalDate.now().minusDays(2), "EXPIRING");
        AlertRule rule = AlertRule.builder().id(1L).build();
        when(certificateRepository.findAll()).thenReturn(List.of(stale));
        when(alertRuleRepository.findByType(any())).thenReturn(List.of(rule));
        stubCreateAsNew();

        service.scanCertificateExpiry();

        assertThat(stale.getStatus()).isEqualTo("EXPIRED"); // 内存中被刷新
        verify(certificateRepository).save(stale); // 持久化回写
    }

    @Test
    void scanCertificateExpiry_staleActiveStatus_persistedAsExpiring() {
        // 数据库存 ACTIVE，实际只剩 5 天到期
        CaExpiryScanService service = newService();
        CaCertificateEntity stale = caCertificate(1L, LocalDate.now().plusDays(5), "ACTIVE");
        AlertRule rule = AlertRule.builder().id(1L).build();
        when(certificateRepository.findAll()).thenReturn(List.of(stale));
        when(alertRuleRepository.findByType(any())).thenReturn(List.of(rule));
        stubCreateAsNew();

        service.scanCertificateExpiry();

        assertThat(stale.getStatus()).isEqualTo("EXPIRING");
        verify(certificateRepository).save(stale);
    }

    @Test
    void scanCertificateExpiry_statusUnchanged_doesNotPersist() {
        // status 已是正确值，不应再 save
        CaExpiryScanService service = newService();
        CaCertificateEntity fresh = caCertificate(1L, LocalDate.now().plusDays(5), "EXPIRING");
        AlertRule rule = AlertRule.builder().id(1L).build();
        when(certificateRepository.findAll()).thenReturn(List.of(fresh));
        when(alertRuleRepository.findByType(any())).thenReturn(List.of(rule));
        stubCreateAsNew();

        service.scanCertificateExpiry();

        verify(certificateRepository, never()).save(any());
    }

    @Test
    void scanCertificateExpiry_inactiveStatus_skipped() {
        // INACTIVE 跳过，不刷新也不 save
        CaExpiryScanService service = newService();
        CaCertificateEntity inactive = caCertificate(1L, LocalDate.now().minusDays(10), "INACTIVE");
        when(certificateRepository.findAll()).thenReturn(List.of(inactive));

        service.scanCertificateExpiry();

        assertThat(inactive.getStatus()).isEqualTo("INACTIVE"); // 保持不变
        verify(certificateRepository, never()).save(any());
        verify(alertHistoryService, never()).createAlertHistoryIfAbsent(any());
    }

    @Test
    void scanCertificateExpiry_mixedCertificates_onlyPersistsChanged() {
        CaExpiryScanService service = newService();
        CaCertificateEntity stale = caCertificate(1L, LocalDate.now().minusDays(1), "EXPIRING"); // → EXPIRED
        CaCertificateEntity fresh = caCertificate(2L, LocalDate.now().plusDays(5), "EXPIRING"); // 不变
        CaCertificateEntity inactive = caCertificate(3L, LocalDate.now().minusDays(10), "INACTIVE"); // 跳过
        AlertRule rule = AlertRule.builder().id(1L).build();
        when(certificateRepository.findAll()).thenReturn(List.of(stale, fresh, inactive));
        when(alertRuleRepository.findByType(any())).thenReturn(List.of(rule));
        stubCreateAsNew();

        service.scanCertificateExpiry();

        verify(certificateRepository, times(1)).save(stale); // 只有 stale 被 save
        verify(certificateRepository, never()).save(fresh);
        verify(certificateRepository, never()).save(inactive);
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

    /** 统一将 createAlertHistoryIfAbsent 桩为"新建"结果，避免 NPE。 */
    private void stubCreateAsNew() {
        AlertHistory history = AlertHistory.builder().id(99L).build();
        when(alertHistoryService.createAlertHistoryIfAbsent(any(AlertHistoryCreateRequest.class)))
                .thenReturn(new AlertHistoryCreateResult(history, true));
    }

    private CaCertificateEntity caCertificate(Long id, LocalDate expiryDate, String status) {
        return CaCertificateEntity.builder()
                .id(id)
                .caType("ENTITY_CA")
                .sealType("OFFICIAL_SEAL")
                .expiryDate(expiryDate)
                .custodianId(20L)
                .custodianName("保管员")
                .borrowStatus("IN_STOCK")
                .status(status)
                .build();
    }
}
