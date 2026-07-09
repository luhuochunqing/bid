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

        // P2-1: createAlertHistory 委托给 createAlertHistoryIfAbsent，使用 findFirstByRuleIdAndRelatedIdOrderByCreatedAtDesc
        when(alertHistoryRepository.findFirstByRuleIdAndRelatedIdOrderByCreatedAtDesc(
                11L, "Qualification:5:2026-04-26")).thenReturn(Optional.of(existingAlert));

        AlertHistory result = alertHistoryService.createAlertHistory(request);

        assertThat(result).isSameAs(existingAlert);
        verify(alertHistoryRepository, never()).save(any(AlertHistory.class));
    }

    @Test
    @DisplayName("已解决的旧提醒在冷却期外应重新生成")
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

        // P2-1: createAlertHistory 委托给 createAlertHistoryIfAbsent
        // 无已有记录 → 新建
        when(alertHistoryRepository.findFirstByRuleIdAndRelatedIdOrderByCreatedAtDesc(
                11L, "Qualification:5:2026-04-22")).thenReturn(Optional.empty());
        when(alertHistoryRepository.save(any(AlertHistory.class))).thenReturn(savedAlert);

        AlertHistory result = alertHistoryService.createAlertHistory(request);

        assertThat(result.getId()).isEqualTo(102L);
        assertThat(result.getRelatedId()).isEqualTo("Qualification:5:2026-04-22");
        verify(alertHistoryRepository).save(any(AlertHistory.class));
    }

    @Test
    @DisplayName("已解决告警在冷却期内应被复用，不重复创建")
    void shouldReuseResolvedAlertWithinCooldownPeriod() {
        // P2-8 核心正向测试：已解决告警在 24h 冷却期内 → 复用，不新建
        AlertHistoryCreateRequest request = new AlertHistoryCreateRequest();
        request.setRuleId(11L);
        request.setLevel(AlertHistory.AlertLevel.HIGH);
        request.setMessage("资质将在 5 天后到期");
        request.setRelatedId("Qualification:5:2026-04-28");

        // 已解决告警，处理时间在 12 小时前（冷却期内）
        AlertHistory resolvedRecently = AlertHistory.builder()
                .id(103L)
                .ruleId(11L)
                .level(AlertHistory.AlertLevel.HIGH)
                .message("资质将在 7 天后到期")
                .relatedId("Qualification:5:2026-04-28")
                .resolved(true)
                .resolvedAt(LocalDateTime.now().minusHours(12))
                .build();

        when(alertHistoryRepository.findFirstByRuleIdAndRelatedIdOrderByCreatedAtDesc(
                11L, "Qualification:5:2026-04-28")).thenReturn(Optional.of(resolvedRecently));

        AlertHistory result = alertHistoryService.createAlertHistory(request);

        assertThat(result).isSameAs(resolvedRecently);
        assertThat(result.getId()).isEqualTo(103L);
        verify(alertHistoryRepository, never()).save(any(AlertHistory.class));
    }

    @Test
    @DisplayName("已解决告警在冷却期边界外应重新生成")
    void shouldCreateNewAlertAfterCooldownBoundary() {
        // P2-8 边界测试：已解决告警处理时间在 25 小时前（冷却期外）→ 新建
        AlertHistoryCreateRequest request = new AlertHistoryCreateRequest();
        request.setRuleId(11L);
        request.setLevel(AlertHistory.AlertLevel.HIGH);
        request.setMessage("资质将在 5 天后到期");
        request.setRelatedId("Qualification:5:2026-04-28");

        AlertHistory resolvedOld = AlertHistory.builder()
                .id(104L)
                .ruleId(11L)
                .level(AlertHistory.AlertLevel.HIGH)
                .message("资质将在 10 天后到期")
                .relatedId("Qualification:5:2026-04-28")
                .resolved(true)
                .resolvedAt(LocalDateTime.now().minusHours(25))
                .build();

        AlertHistory newAlert = AlertHistory.builder()
                .id(105L)
                .ruleId(11L)
                .level(AlertHistory.AlertLevel.HIGH)
                .message("资质将在 5 天后到期")
                .relatedId("Qualification:5:2026-04-28")
                .resolved(false)
                .build();

        when(alertHistoryRepository.findFirstByRuleIdAndRelatedIdOrderByCreatedAtDesc(
                11L, "Qualification:5:2026-04-28")).thenReturn(Optional.of(resolvedOld));
        when(alertHistoryRepository.save(any(AlertHistory.class))).thenReturn(newAlert);

        AlertHistory result = alertHistoryService.createAlertHistory(request);

        assertThat(result.getId()).isEqualTo(105L);
        verify(alertHistoryRepository).save(any(AlertHistory.class));
    }

    @Test
    @DisplayName("已解决告警无 resolvedAt 时间（数据异常）应重新生成")
    void shouldCreateNewAlertWhenResolvedAtIsNull() {
        // P2-8 数据异常测试：resolved=true 但 resolvedAt=null（异常数据）→ 不复用，允许新建
        AlertHistoryCreateRequest request = new AlertHistoryCreateRequest();
        request.setRuleId(11L);
        request.setLevel(AlertHistory.AlertLevel.HIGH);
        request.setMessage("资质将在 5 天后到期");
        request.setRelatedId("Qualification:5:2026-04-28");

        AlertHistory anomalousResolved = AlertHistory.builder()
                .id(106L)
                .ruleId(11L)
                .level(AlertHistory.AlertLevel.HIGH)
                .message("资质将在 10 天后到期")
                .relatedId("Qualification:5:2026-04-28")
                .resolved(true)
                .resolvedAt(null) // 数据异常：已解决但无处理时间
                .build();

        AlertHistory newAlert = AlertHistory.builder()
                .id(107L)
                .ruleId(11L)
                .level(AlertHistory.AlertLevel.HIGH)
                .message("资质将在 5 天后到期")
                .relatedId("Qualification:5:2026-04-28")
                .resolved(false)
                .build();

        when(alertHistoryRepository.findFirstByRuleIdAndRelatedIdOrderByCreatedAtDesc(
                11L, "Qualification:5:2026-04-28")).thenReturn(Optional.of(anomalousResolved));
        when(alertHistoryRepository.save(any(AlertHistory.class))).thenReturn(newAlert);

        AlertHistory result = alertHistoryService.createAlertHistory(request);

        assertThat(result.getId()).isEqualTo(107L);
        verify(alertHistoryRepository).save(any(AlertHistory.class));
    }

    // ===== CO-546: 每日重复通知场景（DAILY_DEDUP 策略） =====

    @Test
    @DisplayName("CO-546: DAILY_DEDUP 策略下昨日未处理告警今日扫描应新建，实现每日通知")
    void shouldCreateNewAlertWhenPreviousUnresolvedFromYesterday() {
        // 场景：CA 证书到期扫描使用 DAILY_DEDUP 策略，昨日创建的告警未处理（resolved=false），
        // 今日扫描时应新建告警（而非复用），以触发每日通知。
        AlertHistoryCreateRequest request = new AlertHistoryCreateRequest();
        request.setRuleId(20L);
        request.setLevel(AlertHistory.AlertLevel.MEDIUM);
        request.setMessage("【CA即将到期】张三（CA）还有 5 天到期");
        request.setRelatedId("CaCertificate:1");
        request.setDedupPolicy(com.xiyu.bid.alerts.domain.DedupPolicy.DAILY_DEDUP);

        AlertHistory yesterdayUnresolved = AlertHistory.builder()
                .id(200L)
                .ruleId(20L)
                .level(AlertHistory.AlertLevel.MEDIUM)
                .message("【CA即将到期】张三（CA）还有 6 天到期")
                .relatedId("CaCertificate:1")
                .resolved(false)
                .createdAt(LocalDateTime.now().minusDays(1)) // 昨日创建
                .build();

        AlertHistory newAlert = AlertHistory.builder()
                .id(201L)
                .ruleId(20L)
                .level(AlertHistory.AlertLevel.MEDIUM)
                .message("【CA即将到期】张三（CA）还有 5 天到期")
                .relatedId("CaCertificate:1")
                .resolved(false)
                .build();

        when(alertHistoryRepository.findFirstByRuleIdAndRelatedIdOrderByCreatedAtDesc(
                20L, "CaCertificate:1")).thenReturn(Optional.of(yesterdayUnresolved));
        when(alertHistoryRepository.save(any(AlertHistory.class))).thenReturn(newAlert);

        AlertHistory result = alertHistoryService.createAlertHistory(request);

        assertThat(result.getId()).isEqualTo(201L);
        verify(alertHistoryRepository).save(any(AlertHistory.class));
    }

    @Test
    @DisplayName("CO-546: DAILY_DEDUP 策略下今日未处理告警今日扫描应复用，实现当日去重")
    void shouldReuseUnresolvedAlertCreatedToday() {
        // 场景：CA 证书到期扫描使用 DAILY_DEDUP 策略，今日已创建的告警未处理（resolved=false），
        // 今日再次扫描时应复用（当日去重），避免当日重复通知。
        AlertHistoryCreateRequest request = new AlertHistoryCreateRequest();
        request.setRuleId(20L);
        request.setLevel(AlertHistory.AlertLevel.MEDIUM);
        request.setMessage("【CA即将到期】张三（CA）还有 5 天到期");
        request.setRelatedId("CaCertificate:1");
        request.setDedupPolicy(com.xiyu.bid.alerts.domain.DedupPolicy.DAILY_DEDUP);

        AlertHistory todayUnresolved = AlertHistory.builder()
                .id(200L)
                .ruleId(20L)
                .level(AlertHistory.AlertLevel.MEDIUM)
                .message("【CA即将到期】张三（CA）还有 5 天到期")
                .relatedId("CaCertificate:1")
                .resolved(false)
                .createdAt(LocalDateTime.now().minusHours(1)) // 今日创建
                .build();

        when(alertHistoryRepository.findFirstByRuleIdAndRelatedIdOrderByCreatedAtDesc(
                20L, "CaCertificate:1")).thenReturn(Optional.of(todayUnresolved));

        AlertHistory result = alertHistoryService.createAlertHistory(request);

        assertThat(result).isSameAs(todayUnresolved);
        verify(alertHistoryRepository, never()).save(any(AlertHistory.class));
    }
}
