package com.xiyu.bid.casework.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.ai.client.AiProviderRuntimeConfig;
import com.xiyu.bid.ai.client.RoutingAiProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

        ScoreItemSnapshot tech = snapshot(101L, "技术方案", "要求提供技术架构图", "技术方案");
        ScoreItemSnapshot biz = snapshot(102L, "商务条款", "要求提供报价明细", "商务条款");

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

        ScoreItemSnapshot tech = snapshot(101L, "技术方案", "要求提供技术架构图", "技术方案");

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

    @Test
    @DisplayName("prompt 只含相关摘录不含全文 — 摘录化防上下文超限")
    void prompt_containsExcerptOnly_notFullMarkdown() {
        when(routingAiProvider.resolveActiveConfig()).thenReturn(config());
        when(restTemplate.exchange(eq(BASE_URL), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"matchedSnippet\": \"本系统采用微服务架构\", \"confidence\": 0.9}", HttpStatus.OK));

        ScoreItemSnapshot tech = snapshot(101L, "技术方案", "要求提供技术架构图", "技术方案");
        List<CaseAiMatcher.AiMatchedSlice> slices = caseAiMatcher.extractSlicesWithAi(buildBigMarkdown(), List.of(tech));

        assertThat(slices).hasSize(1);
        assertThat(slices.get(0).getDraftId()).isEqualTo(101L);
        assertThat(slices.get(0).getMatchedSnippet()).isEqualTo("本系统采用微服务架构");

        // 校验实际发出的 prompt：只含关键词命中的相关摘录，机密段落（全文非相关区）不得进入提示词
        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(eq(BASE_URL), eq(HttpMethod.POST), entityCaptor.capture(), eq(String.class));
        Map<String, Object> body = (Map<String, Object>) entityCaptor.getValue().getBody();
        List<Map<String, String>> messages = (List<Map<String, String>>) body.get("messages");
        String userPrompt = messages.get(1).get("content");
        assertThat(userPrompt).contains("本系统采用微服务架构");
        assertThat(userPrompt).doesNotContain("机密内部价格底线");
    }

    @Test
    @DisplayName("模型改写证据文本时，通过真实段落 ID 保留 AI 调用前的来源章节")
    void rewrittenSnippet_preservesProvenanceFromSelectedSourceSegment() {
        when(routingAiProvider.resolveActiveConfig()).thenReturn(config());
        when(restTemplate.exchange(eq(BASE_URL), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("""
                        {"matchedSnippet":"模型改写后的应急证据", "sourceSegmentId":"segment-1", "confidence":0.9}
                        """, HttpStatus.OK));
        String markdown = """
                # 一、 商务方案
                商务证据正文。

                ## 二、 应急方案
                原始应急处置证据。
                """;
        ScoreItemSnapshot item = snapshot(101L, "应急方案", "应急处置", "技术方案");

        List<CaseAiMatcher.AiMatchedSlice> slices =
                caseAiMatcher.extractSlicesWithAi(markdown, List.of(item));

        assertThat(slices).hasSize(1);
        assertThat(slices.get(0).getMatchedSnippet()).isEqualTo("模型改写后的应急证据");
        assertThat(markdown).doesNotContain(slices.get(0).getMatchedSnippet());
        assertThat(slices.get(0).getProvenance()).isNotNull();
        assertThat(slices.get(0).getProvenance().title()).isEqualTo("二、 应急方案");
        assertThat(slices.get(0).getProvenance().path())
                .isEqualTo("一、 商务方案 > 二、 应急方案");

        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(eq(BASE_URL), eq(HttpMethod.POST), entityCaptor.capture(), eq(String.class));
        Map<String, Object> body = (Map<String, Object>) entityCaptor.getValue().getBody();
        List<Map<String, String>> messages = (List<Map<String, String>>) body.get("messages");
        String prompt = messages.get(1).get("content");
        assertThat(prompt).contains("[segment-0]", "[segment-1]", "sourceSegmentId");
    }

    @Test
    @DisplayName("模型编造段落 ID 时不伪造来源，但仍保留案例候选")
    void inventedSourceSegmentId_keepsSliceWithoutProvenance() {
        when(routingAiProvider.resolveActiveConfig()).thenReturn(config());
        when(restTemplate.exchange(eq(BASE_URL), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("""
                        {"matchedSnippet":"有效案例内容", "sourceSegmentId":"invented", "confidence":0.8}
                        """, HttpStatus.OK));
        ScoreItemSnapshot item = snapshot(101L, "技术方案", "架构", "技术方案");

        List<CaseAiMatcher.AiMatchedSlice> slices =
                caseAiMatcher.extractSlicesWithAi("# 一、 技术方案\n架构证据正文。", List.of(item));

        assertThat(slices).hasSize(1);
        assertThat(slices.get(0).getMatchedSnippet()).isEqualTo("有效案例内容");
        assertThat(slices.get(0).getProvenance()).isNull();
    }

    @Test
    @DisplayName("调用次数=评分项数 — 逐项单条 prompt")
    void oneCallPerCriterion() {
        when(routingAiProvider.resolveActiveConfig()).thenReturn(config());
        when(restTemplate.exchange(eq(BASE_URL), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"matchedSnippet\": \"证据片段\", \"confidence\": 0.85}", HttpStatus.OK));

        ScoreItemSnapshot tech = snapshot(101L, "技术方案", "要求提供技术架构图", "技术方案");
        ScoreItemSnapshot biz = snapshot(102L, "商务条款", "要求提供报价明细", "商务条款");
        List<CaseAiMatcher.AiMatchedSlice> slices =
                caseAiMatcher.extractSlicesWithAi(buildBigMarkdown(), List.of(tech, biz));

        assertThat(slices).hasSize(2);
        assertThat(slices).extracting(CaseAiMatcher.AiMatchedSlice::getDraftId).containsExactly(101L, 102L);
        verify(restTemplate, times(2)).exchange(eq(BASE_URL), eq(HttpMethod.POST), any(), eq(String.class));
    }

    @Test
    @DisplayName("大模型返回非合法 JSON — 该评分项降级跳过不抛错，不伪造")
    void malformedJson_skipsItemGracefully() {
        when(routingAiProvider.resolveActiveConfig()).thenReturn(config());
        when(restTemplate.exchange(eq(BASE_URL), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("抱歉我无法处理这段请求", HttpStatus.OK));

        ScoreItemSnapshot tech = snapshot(101L, "技术方案", "要求提供技术架构图", "技术方案");
        List<CaseAiMatcher.AiMatchedSlice> slices = caseAiMatcher.extractSlicesWithAi(buildBigMarkdown(), List.of(tech));

        assertThat(slices).isEmpty();
        verify(restTemplate, times(1)).exchange(eq(BASE_URL), eq(HttpMethod.POST), any(), eq(String.class));
    }

    @Test
    @DisplayName("无可用摘录（空标书）— 诚实跳过，不调 LLM、不伪造片段")
    void blankMarkdown_skipsWithoutLlmCall() {
        when(routingAiProvider.resolveActiveConfig()).thenReturn(config());

        ScoreItemSnapshot tech = snapshot(101L, "技术方案", "要求提供技术架构图", "技术方案");
        List<CaseAiMatcher.AiMatchedSlice> slices = caseAiMatcher.extractSlicesWithAi("", List.of(tech));

        assertThat(slices).isEmpty();
        verify(restTemplate, never()).exchange(anyString(), any(), any(), any(Class.class));
    }

    @Test
    @DisplayName("LLM 判定无证据（空 matchedSnippet）— 诚实降级不伪造入库")
    void emptySnippet_honestSkip() {
        when(routingAiProvider.resolveActiveConfig()).thenReturn(config());
        when(restTemplate.exchange(eq(BASE_URL), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"matchedSnippet\": \"\", \"confidence\": 0.0}", HttpStatus.OK));

        ScoreItemSnapshot tech = snapshot(101L, "技术方案", "要求提供技术架构图", "技术方案");
        List<CaseAiMatcher.AiMatchedSlice> slices = caseAiMatcher.extractSlicesWithAi(buildBigMarkdown(), List.of(tech));

        assertThat(slices).isEmpty();
    }

    @Test
    @DisplayName("大模型真实调用失败（上游 5xx）— 抛 IllegalStateException 供 Listener 发失败通知")
    void realCallFailure_throwsForListener() {
        when(routingAiProvider.resolveActiveConfig()).thenReturn(config());
        when(restTemplate.exchange(eq(BASE_URL), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "upstream 500"));

        ScoreItemSnapshot tech = snapshot(101L, "技术方案", "要求提供技术架构图", "技术方案");
        assertThatThrownBy(() -> caseAiMatcher.extractSlicesWithAi(buildBigMarkdown(), List.of(tech)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("大模型调用失败");
    }

    @Test
    @DisplayName("OpenAI choices 包装 + content 带 ```json 围栏 — 解包后命中 matchedSnippet（spec 049 验收发现）")
    void openaiChoicesWrapped_withJsonFence_parsesSnippet() {
        when(routingAiProvider.resolveActiveConfig()).thenReturn(config());
        // 网关真实形态：choices[0].message.content 内是带围栏的 JSON 对象
        String raw = "{\"choices\":[{\"finish_reason\":\"stop\",\"index\":0,"
                + "\"message\":{\"role\":\"assistant\",\"content\":\"```json\\n{\\\"matchedSnippet\\\": \\\"本系统采用微服务架构\\\", \\\"confidence\\\": 0.85}\\n```\"}}]}";
        when(restTemplate.exchange(eq(BASE_URL), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(raw, HttpStatus.OK));

        ScoreItemSnapshot tech = snapshot(101L, "技术方案", "要求提供技术架构图", "技术方案");
        List<CaseAiMatcher.AiMatchedSlice> slices = caseAiMatcher.extractSlicesWithAi(buildBigMarkdown(), List.of(tech));

        assertThat(slices).hasSize(1);
        assertThat(slices.get(0).getMatchedSnippet()).isEqualTo("本系统采用微服务架构");
        assertThat(slices.get(0).getConfidence()).isEqualTo(0.85);
    }

    @Test
    @DisplayName("OpenAI choices 包装 + content 为纯 JSON（无围栏）— 同样命中")
    void openaiChoicesWrapped_plainJsonContent_parsesSnippet() {
        when(routingAiProvider.resolveActiveConfig()).thenReturn(config());
        String raw = "{\"choices\":[{\"message\":{\"content\":\"{\\\"matchedSnippet\\\": \\\"客服响应及时\\\", \\\"confidence\\\": 0.9}\"}}]}";
        when(restTemplate.exchange(eq(BASE_URL), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(raw, HttpStatus.OK));

        ScoreItemSnapshot tech = snapshot(101L, "技术方案", "要求提供技术架构图", "技术方案");
        List<CaseAiMatcher.AiMatchedSlice> slices = caseAiMatcher.extractSlicesWithAi(buildBigMarkdown(), List.of(tech));

        assertThat(slices).hasSize(1);
        assertThat(slices.get(0).getMatchedSnippet()).isEqualTo("客服响应及时");
    }

    @Test
    @DisplayName("choices 包装但 content 为空 — 回退裸 JSON 解析（兼容直返目标 JSON 的 provider）")
    void openaiChoicesWrapped_emptyContent_fallsBackToBareJson() {
        when(routingAiProvider.resolveActiveConfig()).thenReturn(config());
        // content 为空串 → unpackContent 原样返回 → extractJson 命中裸 JSON
        String raw = "{\"choices\":[{\"message\":{\"content\":\"\"}}]}";
        when(restTemplate.exchange(eq(BASE_URL), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(raw, HttpStatus.OK));

        ScoreItemSnapshot tech = snapshot(101L, "技术方案", "要求提供技术架构图", "技术方案");
        List<CaseAiMatcher.AiMatchedSlice> slices = caseAiMatcher.extractSlicesWithAi(buildBigMarkdown(), List.of(tech));

        assertThat(slices).isEmpty();
    }

    @Test
    @DisplayName("兼容大模型返回单元素数组形态 — 归一化为单条对象解析")
    void arrayResponse_normalizedToSingleSlice() {
        when(routingAiProvider.resolveActiveConfig()).thenReturn(config());
        when(restTemplate.exchange(eq(BASE_URL), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("[{\"matchedSnippet\": \"微服务架构支撑\", \"confidence\": 0.9}]", HttpStatus.OK));

        ScoreItemSnapshot tech = snapshot(101L, "技术方案", "要求提供技术架构图", "技术方案");
        List<CaseAiMatcher.AiMatchedSlice> slices = caseAiMatcher.extractSlicesWithAi(buildBigMarkdown(), List.of(tech));

        assertThat(slices).hasSize(1);
        assertThat(slices.get(0).getMatchedSnippet()).isEqualTo("微服务架构支撑");
    }

    @Test
    @DisplayName("prompt 剥离 markdown 内嵌 base64 图片 — 防 LLM 网关 convert_request_failed 500")
    void prompt_stripsBase64Images_notSendRawDataUri() {
        when(routingAiProvider.resolveActiveConfig()).thenReturn(config());
        when(restTemplate.exchange(eq(BASE_URL), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"matchedSnippet\": \"本系统采用微服务架构\", \"confidence\": 0.9}", HttpStatus.OK));

        // 摘录命中段落同时含 base64 图片（docx→markdown 常见形态），必须被剥离后才能发给 LLM
        String markdown = "技术方案 本系统采用微服务架构。\n\n"
                + "![架构图](data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==)\n\n"
                + "性能要求满足高并发。";
        ScoreItemSnapshot tech = snapshot(101L, "技术方案", "要求提供技术架构图", "技术方案");
        caseAiMatcher.extractSlicesWithAi(markdown, List.of(tech));

        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(eq(BASE_URL), eq(HttpMethod.POST), entityCaptor.capture(), eq(String.class));
        Map<String, Object> body = (Map<String, Object>) entityCaptor.getValue().getBody();
        List<Map<String, String>> messages = (List<Map<String, String>>) body.get("messages");
        String userPrompt = messages.get(1).get("content");
        assertThat(userPrompt).doesNotContain("data:image");
        assertThat(userPrompt).doesNotContain("base64");
        assertThat(userPrompt).contains("本系统采用微服务架构");
        assertThat(userPrompt).contains("性能要求满足高并发");
    }

    @Test
    @DisplayName("stripBase64Images — 标准 markdown 图片语法替换为占位符")
    void stripBase64Images_markdownSyntax() {
        String input = "正文一\n\n![架构图](data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==)\n\n正文二";
        String out = CaseAiMatcher.stripBase64Images(input);
        assertThat(out).contains("正文一").contains("正文二");
        assertThat(out).doesNotContain("data:image").doesNotContain("base64");
        assertThat(out).contains("[image]");
    }

    @Test
    @DisplayName("stripBase64Images — 裸 data URI 残留也被剥离")
    void stripBase64Images_bareDataUri() {
        String input = "内容 data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/wAALCAABAAEBAREA/8QAFAABAAAAAAAAAAAAAAAAAAAACf/EABQQAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEAAD8AKp//2Q==";
        String out = CaseAiMatcher.stripBase64Images(input);
        assertThat(out).contains("内容");
        assertThat(out).doesNotContain("data:image").doesNotContain("base64");
        assertThat(out).contains("[image]");
    }

    @Test
    @DisplayName("stripBase64Images — 无图片文本原样返回")
    void stripBase64Images_plainTextUntouched() {
        String input = "纯文本摘录，不含任何图片数据。";
        assertThat(CaseAiMatcher.stripBase64Images(input)).isEqualTo(input);
    }

    @Test
    @DisplayName("stripBase64Images — 截断无闭合括号的图片（摘录 12K 截断切掉右括号）也被剥离")
    void stripBase64Images_truncatedNoClosingParen() {
        String input = "![架构图](data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQ";
        String out = CaseAiMatcher.stripBase64Images(input);
        assertThat(out).doesNotContain("data:image").doesNotContain("base64");
        assertThat(out).contains("[image]");
    }

    @Test
    @DisplayName("stripBase64Images — 截断后紧邻中文正文的图片（无右括号无空白）也被剥离")
    void stripBase64Images_truncatedAdjacentToChinese() {
        String input = "![架构图](data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQ以下是正文";
        String out = CaseAiMatcher.stripBase64Images(input);
        assertThat(out).doesNotContain("data:image").doesNotContain("base64");
        assertThat(out).contains("[image]").contains("以下是正文");
    }

    @Test
    @DisplayName("stripBase64Images — base64 载荷含换行也被剥离")
    void stripBase64Images_base64WithNewline() {
        String input = "![logo](data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJ\nAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAAASUVORK5CYII=)";
        String out = CaseAiMatcher.stripBase64Images(input);
        assertThat(out).doesNotContain("data:image").doesNotContain("base64");
        assertThat(out).contains("[image]");
    }

    @Test
    @DisplayName("stripBase64Images — mime 含空格等畸形形态也被剥离")
    void stripBase64Images_malformedMime() {
        String input = "![图](data:image/png ;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAA)";
        String out = CaseAiMatcher.stripBase64Images(input);
        assertThat(out).doesNotContain("data:image").doesNotContain("base64");
        assertThat(out).contains("[image]");
    }

    @Test
    @DisplayName("stripBase64Images — 空 alt 的图片也被剥离")
    void stripBase64Images_emptyAlt() {
        String input = "正文\n\n![](data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==)\n\n结尾";
        String out = CaseAiMatcher.stripBase64Images(input);
        assertThat(out).doesNotContain("data:image").doesNotContain("base64");
        assertThat(out).contains("[image]").contains("正文").contains("结尾");
    }

    private static final String BASE_URL = "https://ai.example.com/v1/chat/completions";

    private AiProviderRuntimeConfig config() {
        return new AiProviderRuntimeConfig("qwen", BASE_URL, "qwen-max", "test-key");
    }

    /**
     * 构造 >12K 字符的假标书：填充段落撑大体量（触发摘录化路径），
     * 段落 25 含关键词"技术方案"（命中），段落 5 为机密段落（不应进 prompt）。
     */
    private String buildBigMarkdown() {
        String filler = "这是与评分无关的填充段落，仅用于撑大全文长度避免整段原文直接透传。".repeat(120);
        String secret = "机密内部价格底线不应泄露给大模型" + filler;
        String hit = "技术方案 本系统采用微服务架构，支持高并发，满足性能要求。" + filler;
        List<String> paras = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            paras.add(filler + "段落序号" + i);
        }
        paras.set(5, secret);
        paras.set(25, hit);
        return String.join("\n\n", paras);
    }

    private ScoreItemSnapshot snapshot(Long id, String title, String rule, String category) {
        return new ScoreItemSnapshot(id, title, rule, category);
    }
}
