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
 * <p>覆盖 7 个场景：新建、复用、relatedId 为 null/空串跳过去重、
 * 以及 ruleId/level/message 校验失败抛 IllegalArgumentException。</p>
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

        when(alertHistoryRepository.findFirstByRuleIdAndRelatedIdAndResolvedFalseOrderByCreatedAtDesc(
                1L, "Project:123")).thenReturn(Optional.empty());
        when(alertHistoryRepository.save(any(AlertHistory.class))).thenReturn(saved);

        AlertHistoryCreateResult result = alertHistoryService.createAlertHistoryIfAbsent(request);

        assertThat(result.created()).isTrue();
        assertThat(result.alertHistory()).isSameAs(saved);
        verify(alertHistoryRepository).save(any(AlertHistory.class));
    }

    @Test
    @DisplayName("relatedId 非空且已有未处理记录时复用，created=false 且不调用 save")
    void shouldReuseWhenRelatedIdPresentAndExistingFound() {
        AlertHistoryCreateRequest request = buildRequest("Project:123");
        AlertHistory existing = AlertHistory.builder()
                .id(101L)
                .ruleId(1L)
                .level(AlertHistory.AlertLevel.HIGH)
                .message("之前的测试告警")
                .relatedId("Project:123")
                .resolved(false)
                .build();

        when(alertHistoryRepository.findFirstByRuleIdAndRelatedIdAndResolvedFalseOrderByCreatedAtDesc(
                1L, "Project:123")).thenReturn(Optional.of(existing));

        AlertHistoryCreateResult result = alertHistoryService.createAlertHistoryIfAbsent(request);

        assertThat(result.created()).isFalse();
        assertThat(result.alertHistory()).isSameAs(existing);
        verify(alertHistoryRepository, never()).save(any(AlertHistory.class));
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
                .findFirstByRuleIdAndRelatedIdAndResolvedFalseOrderByCreatedAtDesc(any(), any());
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
                .findFirstByRuleIdAndRelatedIdAndResolvedFalseOrderByCreatedAtDesc(any(), any());
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
