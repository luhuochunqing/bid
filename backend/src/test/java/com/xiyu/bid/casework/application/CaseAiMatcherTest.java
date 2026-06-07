package com.xiyu.bid.casework.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.ai.client.RoutingAiProvider;
import com.xiyu.bid.projectworkflow.entity.ProjectScoreDraft;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CaseAiMatcher 单元测试。
 *
 * <p>核心 AI 切片逻辑：评分项 → 标书应答片段的配对集。验证两个分支：
 * <ol>
 *   <li>无 AI 配置（resolveActiveConfig 抛异常 / 返回 null）→ 必须抛 IllegalStateException，禁用 Mock 降级</li>
 *   <li>extractCategory 智能打标：技术/商务/实施服务/资质业绩 分类</li>
 * </ol>
 */
class CaseAiMatcherTest {

    private RoutingAiProvider routingAiProvider;
    private RestTemplate restTemplate;
    private ObjectMapper objectMapper;
    private CaseAiMatcher caseAiMatcher;

    @BeforeEach
    void setUp() {
        routingAiProvider = mock(RoutingAiProvider.class);
        restTemplate = mock(RestTemplate.class);
        objectMapper = new ObjectMapper();
        caseAiMatcher = new CaseAiMatcher(routingAiProvider, restTemplate, objectMapper);
    }

    @Test
    @DisplayName("无 AI 配置时 throw IllegalStateException — 禁止 Mock 降级硬编码")
    void extractSlices_throwsWhenNoProvider() {
        when(routingAiProvider.resolveActiveConfig())
                .thenThrow(new IllegalStateException("AI disabled"));

        ProjectScoreDraft tech = draft(101L, "技术方案", "要求提供技术架构图");
        ProjectScoreDraft biz = draft(102L, "商务条款", "要求提供报价明细");

        assertThatThrownBy(() -> caseAiMatcher.extractSlicesWithAi("## 标书内容", List.of(tech, biz)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AI Provider")
                .hasMessageContaining("启用");
        verify(routingAiProvider, times(1)).resolveActiveConfig();
        verify(restTemplate, never()).exchange(anyString(), any(), any(), any(Class.class));
    }

    @Test
    @DisplayName("resolveActiveConfig 返回 null 时也必须 throw — 禁用 Mock 降级")
    void extractSlices_throwsWhenConfigNull() {
        when(routingAiProvider.resolveActiveConfig()).thenReturn(null);

        ProjectScoreDraft tech = draft(101L, "技术方案", "要求提供技术架构图");

        assertThatThrownBy(() -> caseAiMatcher.extractSlicesWithAi("## 标书内容", List.of(tech)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AI Provider")
                .hasMessageContaining("启用");
        verify(restTemplate, never()).exchange(anyString(), any(), any(), any(Class.class));
    }

    @Test
    @DisplayName("空评分项列表直接返回空配对集（无需 throw）")
    void extractSlices_emptyDrafts() {
        when(routingAiProvider.resolveActiveConfig())
                .thenThrow(new IllegalStateException("AI disabled"));

        List<CaseAiMatcher.AiMatchedSlice> slices =
                caseAiMatcher.extractSlicesWithAi("## 标书内容", List.of());

        assertThat(slices).isEmpty();
    }

    @Test
    @DisplayName("extractCategory 智能打标 — 技术类归'技术'")
    void extractCategory_tech() {
        assertThat(caseAiMatcher.extractCategory("技术方案")).isEqualTo("技术");
        assertThat(caseAiMatcher.extractCategory("系统技术要求")).isEqualTo("技术");
    }

    @Test
    @DisplayName("extractCategory 智能打标 — 商务类归'商务'")
    void extractCategory_biz() {
        assertThat(caseAiMatcher.extractCategory("商务评分")).isEqualTo("商务");
        assertThat(caseAiMatcher.extractCategory("商务条款应答")).isEqualTo("商务");
    }

    @Test
    @DisplayName("extractCategory 智能打标 — 实施/服务类归'实施服务'")
    void extractCategory_implementation() {
        assertThat(caseAiMatcher.extractCategory("实施计划")).isEqualTo("实施服务");
        assertThat(caseAiMatcher.extractCategory("售后服务")).isEqualTo("实施服务");
    }

    @Test
    @DisplayName("extractCategory 智能打标 — 资质/业绩类归'资质业绩'")
    void extractCategory_qualification() {
        assertThat(caseAiMatcher.extractCategory("资质证书")).isEqualTo("资质业绩");
        assertThat(caseAiMatcher.extractCategory("项目业绩")).isEqualTo("资质业绩");
    }

    @Test
    @DisplayName("extractCategory 智能打标 — 未知分类保留原值")
    void extractCategory_unknown() {
        assertThat(caseAiMatcher.extractCategory("其他类")).isEqualTo("其他类");
    }

    @Test
    @DisplayName("extractCategory 智能打标 — null 安全返回'其他'")
    void extractCategory_null() {
        assertThat(caseAiMatcher.extractCategory(null)).isEqualTo("其他");
    }

    private ProjectScoreDraft draft(Long id, String title, String rule) {
        ProjectScoreDraft d = new ProjectScoreDraft();
        d.setId(id);
        d.setScoreItemTitle(title);
        d.setScoreRuleText(rule);
        return d;
    }
}
