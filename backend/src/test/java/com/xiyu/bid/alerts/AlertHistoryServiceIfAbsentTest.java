package com.xiyu.bid.alerts;

import com.xiyu.bid.alerts.dto.AlertHistoryCreateRequest;
import com.xiyu.bid.alerts.dto.AlertHistoryCreateResult;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AlertHistoryService#createAlertHistoryIfAbsent} 单元测试。
 *
 * <p>P2-8 改造后覆盖场景：
 * <ul>
 *   <li>新建：无已有记录</li>
 *   <li>复用未处理：已有 resolved=false 记录</li>
 *   <li>P2-8 复用冷却期内已处理：已有 resolved=true 且 resolvedAt 在 24h 内</li>
 *   <li>P2-8 新建超过冷却期：已有 resolved=true 且 resolvedAt 超过 24h</li>
 *   <li>P2-8 新建无处理时间：已有 resolved=true 但 resolvedAt=null（数据异常）</li>
 *   <li>relatedId 为 null/空串跳过去重</li>
 *   <li>ruleId/level/message 校验失败抛 IllegalArgumentException</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AlertHistoryService.createAlertHistoryIfAbsent 单元测试")
class AlertHistoryServiceIfAbsentTest {

    @Mock
    private AlertHistoryRepository alertHistoryRepository;

    @Mock
    private AlertRuleRepository alertRuleRepository;

    @InjectMocks
    private AlertHistoryService alertHistoryService;

    /** 测试用规则（DEADLINE 类型，阈值 7 天） */
    private AlertRule testRule;

    @BeforeEach
    void setUp() {
        testRule = AlertRule.builder()
                .id(1L)
                .name("测试规则")
                .type(AlertRule.AlertType.DEADLINE)
                .condition(AlertRule.ConditionType.LESS_THAN)
                .threshold(BigDecimal.valueOf(7))
                .enabled(true)
                .createdBy("system")
                .build();
    }

    /** 构建标准请求：ruleId=1L, level=HIGH, message="测试告警"，relatedId 可指定 */
    private AlertHistoryCreateRequest buildRequest(String relatedId) {
        AlertHistoryCreateRequest request = new AlertHistoryCreateRequest();
        request.setRuleId(testRule.getId());
        request.setLevel(AlertHistory.AlertLevel.HIGH);
        request.setMessage("测试告警");
        request.setRelatedId(relatedId);
        return request;
    }

    @Test
    @DisplayName("relatedId 非空且无已有记录时新建告警，created=true 并调用 save")
    void shouldCreateWhenRelatedIdPresentAndNoExisting() {
        AlertHistoryCreateRequest request = buildRequest("Project:123");
        AlertHistory saved = AlertHistory.builder()
                .id(201L)
                .ruleId(1L)
                .level(AlertHistory.AlertLevel.HIGH)
                .message("测试告警")
                .relatedId("Project:123")
                .resolved(false)
                .build();

        when(alertHistoryRepository.findFirstByRuleIdAndRelatedIdOrderByCreatedAtDesc(
                1L, "Project:123")).thenReturn(Optional.empty());
        when(alertHistoryRepository.save(any(AlertHistory.class))).thenReturn(saved);

        AlertHistoryCreateResult result = alertHistoryService.createAlertHistoryIfAbsent(request);

        assertThat(result.created()).isTrue();
        assertThat(result.alertHistory()).isSameAs(saved);
        verify(alertHistoryRepository).save(any(AlertHistory.class));
    }

    @Test
    @DisplayName("relatedId 非空且已有未处理记录时复用，created=false 且不调用 save")
    void shouldReuseWhenRelatedIdPresentAndExistingUnresolvedFound() {
        AlertHistoryCreateRequest request = buildRequest("Project:123");
        AlertHistory existing = AlertHistory.builder()
                .id(101L)
                .ruleId(1L)
                .level(AlertHistory.AlertLevel.HIGH)
                .message("之前的测试告警")
                .relatedId("Project:123")
                .resolved(false)
                .createdAt(LocalDateTime.now().minusDays(3)) // 3天前创建，默认策略仍应复用
                .build();

        when(alertHistoryRepository.findFirstByRuleIdAndRelatedIdOrderByCreatedAtDesc(
                1L, "Project:123")).thenReturn(Optional.of(existing));

        AlertHistoryCreateResult result = alertHistoryService.createAlertHistoryIfAbsent(request);

        assertThat(result.created()).isFalse();
        assertThat(result.alertHistory()).isSameAs(existing);
        verify(alertHistoryRepository, never()).save(any(AlertHistory.class));
    }

    @Test
    @DisplayName("CO-546: 默认策略下未处理告警跨日仍复用（保证不干扰其他告警类型）")
    void shouldReuseUnresolvedAcrossDaysUnderDefaultPolicy() {
        // 场景：DEADLINE 等告警使用默认 REUSE_UNTIL_RESOLVED 策略，
        // 未处理告警即使 createdAt 在昨天也应复用，不新建。
        AlertHistoryCreateRequest request = buildRequest("Project:123");
        // 默认策略：request 未设置 dedupPolicy，使用 REUSE_UNTIL_RESOLVED
        AlertHistory existing = AlertHistory.builder()
                .id(101L)
                .ruleId(1L)
                .level(AlertHistory.AlertLevel.HIGH)
                .message("之前的测试告警")
                .relatedId("Project:123")
                .resolved(false)
                .createdAt(LocalDateTime.now().minusDays(1)) // 昨天创建
                .build();

        when(alertHistoryRepository.findFirstByRuleIdAndRelatedIdOrderByCreatedAtDesc(
                1L, "Project:123")).thenReturn(Optional.of(existing));

        AlertHistoryCreateResult result = alertHistoryService.createAlertHistoryIfAbsent(request);

        assertThat(result.created()).isFalse();
        verify(alertHistoryRepository, never()).save(any(AlertHistory.class));
    }

    @Test
    @DisplayName("P2-8: 已处理告警在冷却期内（24h）时复用，created=false")
    void shouldReuseWhenResolvedAlertWithinCooldown() {
        AlertHistoryCreateRequest request = buildRequest("Project:123");
        AlertHistory resolvedRecent = AlertHistory.builder()
                .id(101L)
                .ruleId(1L)
                .level(AlertHistory.AlertLevel.HIGH)
                .message("已处理的告警")
                .relatedId("Project:123")
                .resolved(true)
                .resolvedAt(LocalDateTime.now().minusHours(2)) // 2h 前，在 24h 冷却期内
                .build();

        when(alertHistoryRepository.findFirstByRuleIdAndRelatedIdOrderByCreatedAtDesc(
                1L, "Project:123")).thenReturn(Optional.of(resolvedRecent));

        AlertHistoryCreateResult result = alertHistoryService.createAlertHistoryIfAbsent(request);

        assertThat(result.created()).isFalse();
        assertThat(result.alertHistory()).isSameAs(resolvedRecent);
        verify(alertHistoryRepository, never()).save(any(AlertHistory.class));
    }

    @Test
    @DisplayName("P2-8: 已处理告警超过冷却期（24h）时新建，created=true")
    void shouldCreateWhenResolvedAlertBeyondCooldown() {
        AlertHistoryCreateRequest request = buildRequest("Project:123");
        AlertHistory resolvedOld = AlertHistory.builder()
                .id(101L)
                .ruleId(1L)
                .level(AlertHistory.AlertLevel.HIGH)
                .message("很久前已处理的告警")
                .relatedId("Project:123")
                .resolved(true)
                .resolvedAt(LocalDateTime.now().minusHours(48)) // 48h 前，超过 24h 冷却期
                .build();
        AlertHistory saved = AlertHistory.builder()
                .id(201L)
                .ruleId(1L)
                .level(AlertHistory.AlertLevel.HIGH)
                .message("测试告警")
                .relatedId("Project:123")
                .resolved(false)
                .build();

        when(alertHistoryRepository.findFirstByRuleIdAndRelatedIdOrderByCreatedAtDesc(
                1L, "Project:123")).thenReturn(Optional.of(resolvedOld));
        when(alertHistoryRepository.save(any(AlertHistory.class))).thenReturn(saved);

        AlertHistoryCreateResult result = alertHistoryService.createAlertHistoryIfAbsent(request);

        assertThat(result.created()).isTrue();
        assertThat(result.alertHistory()).isSameAs(saved);
        verify(alertHistoryRepository).save(any(AlertHistory.class));
    }

    @Test
    @DisplayName("P2-8: 已处理告警但 resolvedAt=null（数据异常）时新建，created=true")
    void shouldCreateWhenResolvedAlertHasNullResolvedAt() {
        AlertHistoryCreateRequest request = buildRequest("Project:123");
        AlertHistory resolvedNoAt = AlertHistory.builder()
                .id(101L)
                .ruleId(1L)
                .level(AlertHistory.AlertLevel.HIGH)
                .message("已处理但无处理时间的告警")
                .relatedId("Project:123")
                .resolved(true)
                .resolvedAt(null) // 数据异常
                .build();
        AlertHistory saved = AlertHistory.builder()
                .id(201L)
                .ruleId(1L)
                .level(AlertHistory.AlertLevel.HIGH)
                .message("测试告警")
                .relatedId("Project:123")
                .resolved(false)
                .build();

        when(alertHistoryRepository.findFirstByRuleIdAndRelatedIdOrderByCreatedAtDesc(
                1L, "Project:123")).thenReturn(Optional.of(resolvedNoAt));
        when(alertHistoryRepository.save(any(AlertHistory.class))).thenReturn(saved);

        AlertHistoryCreateResult result = alertHistoryService.createAlertHistoryIfAbsent(request);

        assertThat(result.created()).isTrue();
        assertThat(result.alertHistory()).isSameAs(saved);
        verify(alertHistoryRepository).save(any(AlertHistory.class));
    }

    @Test
    @DisplayName("relatedId 为 null 时直接新建，created=true，跳过去重查询")
    void shouldCreateWhenRelatedIdIsNull() {
        AlertHistoryCreateRequest request = buildRequest(null);
        AlertHistory saved = AlertHistory.builder()
                .id(202L)
                .ruleId(1L)
                .level(AlertHistory.AlertLevel.HIGH)
                .message("测试告警")
                .resolved(false)
                .build();

        when(alertHistoryRepository.save(any(AlertHistory.class))).thenReturn(saved);

        AlertHistoryCreateResult result = alertHistoryService.createAlertHistoryIfAbsent(request);

        assertThat(result.created()).isTrue();
        assertThat(result.alertHistory()).isSameAs(saved);
        verify(alertHistoryRepository, never())
                .findFirstByRuleIdAndRelatedIdOrderByCreatedAtDesc(any(), any());
        verify(alertHistoryRepository).save(any(AlertHistory.class));
    }

    @Test
    @DisplayName("relatedId 为空字符串时直接新建，created=true，跳过去重查询")
    void shouldCreateWhenRelatedIdIsBlank() {
        AlertHistoryCreateRequest request = buildRequest("");
        AlertHistory saved = AlertHistory.builder()
                .id(203L)
                .ruleId(1L)
                .level(AlertHistory.AlertLevel.HIGH)
                .message("测试告警")
                .resolved(false)
                .build();

        when(alertHistoryRepository.save(any(AlertHistory.class))).thenReturn(saved);

        AlertHistoryCreateResult result = alertHistoryService.createAlertHistoryIfAbsent(request);

        assertThat(result.created()).isTrue();
        assertThat(result.alertHistory()).isSameAs(saved);
        verify(alertHistoryRepository, never())
                .findFirstByRuleIdAndRelatedIdOrderByCreatedAtDesc(any(), any());
        verify(alertHistoryRepository).save(any(AlertHistory.class));
    }

    @Test
    @DisplayName("ruleId 为 null 时抛 IllegalArgumentException")
    void shouldThrowWhenRuleIdIsNull() {
        AlertHistoryCreateRequest request = new AlertHistoryCreateRequest();
        request.setRuleId(null);
        request.setLevel(AlertHistory.AlertLevel.HIGH);
        request.setMessage("测试告警");

        assertThatThrownBy(() -> alertHistoryService.createAlertHistoryIfAbsent(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Rule ID");

        verify(alertHistoryRepository, never()).save(any(AlertHistory.class));
    }

    @Test
    @DisplayName("level 为 null 时抛 IllegalArgumentException")
    void shouldThrowWhenLevelIsNull() {
        AlertHistoryCreateRequest request = new AlertHistoryCreateRequest();
        request.setRuleId(1L);
        request.setLevel(null);
        request.setMessage("测试告警");

        assertThatThrownBy(() -> alertHistoryService.createAlertHistoryIfAbsent(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Level");

        verify(alertHistoryRepository, never()).save(any(AlertHistory.class));
    }

    @Test
    @DisplayName("message 为空时抛 IllegalArgumentException")
    void shouldThrowWhenMessageIsBlank() {
        AlertHistoryCreateRequest request = new AlertHistoryCreateRequest();
        request.setRuleId(1L);
        request.setLevel(AlertHistory.AlertLevel.HIGH);
        request.setMessage("");

        assertThatThrownBy(() -> alertHistoryService.createAlertHistoryIfAbsent(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Message");

        verify(alertHistoryRepository, never()).save(any(AlertHistory.class));
    }
}
