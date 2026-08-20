package com.xiyu.bid.casework.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.ai.client.AiProviderRuntimeConfig;
import com.xiyu.bid.ai.client.RoutingAiProvider;
import com.xiyu.bid.scoreparse.application.ScoreDocExcerptExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class CaseAiMatcher {

    /** 摘录预算：每评分项交给 LLM 的相关段落上限（字符）。避免超大标书全文直灌导致 LLM 上下文超限 */
    private static final int EXCERPT_MAX_CHARS = 12_000;

    private final RoutingAiProvider routingAiProvider;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public List<AiMatchedSlice> extractSlicesWithAi(String markdown, List<ScoreItemSnapshot> criteria) {
        if (criteria == null || criteria.isEmpty()) {
            // 没有评分项时无需 AI 提取；前置条件层（Listener）已对外承诺 0 cases 也算成功。
            return List.of();
        }
        log.info("AI 案例匹配开始：{} 个评分项，标书文本长度 {} 字符", criteria.size(),
                markdown != null ? markdown.length() : 0);
        AiProviderRuntimeConfig config;
        try {
            config = routingAiProvider.resolveActiveConfig();
        } catch (RuntimeException ex) {
            log.error("AI 案例匹配失败：无法解析 AI Provider 配置", ex);
            throw new IllegalStateException("AI 案例沉淀失败：未配置可用的 AI Provider，请联系管理员在 AI 管理页面启用。", ex);
        }

        if (config == null) {
            log.error("AI 案例匹配失败：当前未启用任何 AI Provider");
            throw new IllegalStateException("AI 案例沉淀失败：当前未启用任何 AI Provider，请联系管理员在 AI 管理页面启用。");
        }

        // 逐评分项摘录化（spec 049 T007）：每项先取 ≤12K 字符相关段落，再单条 prompt 调 LLM，
        // 避免超大标书全文直灌上下文超限。单项目标失败（非 JSON/无证据）降级跳过，真实调用失败整体上报。
        List<AiMatchedSlice> result = new ArrayList<>();
        for (ScoreItemSnapshot criterion : criteria) {
            String excerpt = ScoreDocExcerptExtractor.extractRelevantExcerpt(
                    markdown, criterion.title(), criterion.ruleText(), EXCERPT_MAX_CHARS);
            if (excerpt == null || excerpt.isBlank()) {
                log.warn("AI 案例匹配：评分项 {}（{}）无可用摘录，跳过（诚实降级，不伪造）", criterion.id(), criterion.title());
                continue;
            }
            AiMatchedSlice slice;
            try {
                slice = extractSliceForCriterion(config, criterion, excerpt);
            } catch (RuntimeException e) {
                log.error("AI 案例匹配：评分项 {}（{}）大模型调用失败", criterion.id(), criterion.title(), e);
                // AI 真实调用失败时直接抛错；Listener 统一发"AI 沉淀失败"通知给触发者，禁止伪造证明片段入库。
                throw new IllegalStateException("AI 案例沉淀失败：大模型调用失败，原因：" + e.getMessage(), e);
            }
            if (slice != null) {
                result.add(slice);
            }
        }
        log.info("AI 案例匹配完成：解析到 {} 个匹配片段", result.size());
        return result;
    }

    /**
     * 单个评分项的 LLM 抽取：prompt 只含相关摘录（不含全文），请求返回单条 JSON 对象。
     * draftId 由调用方绑定评分项 id，不信任 LLM 回显的结构 id（LLM 编号复述不可靠，lessons §133）。
     * 返回 null 表示该评分项无匹配证据（诚实降级），由调用方跳过。
     */
    private AiMatchedSlice extractSliceForCriterion(AiProviderRuntimeConfig config, ScoreItemSnapshot criterion, String excerpt) {
        // 剥离 markdown 内嵌 base64 图片数据：docx→markdown 转换会把图片内联成 data:image/...;base64,
        // LLM 网关会把 prompt 中的 base64 当多模态输入解析，数据不完整时直接 500（convert_request_failed）。
        // 摘录只服务于文本证据提取，图片对评分证据无意义，统一替换为占位符（spec 049 验收发现）。
        String cleanExcerpt = stripBase64Images(excerpt);
        if (cleanExcerpt.contains("data:image") || cleanExcerpt.contains("base64")) {
            // 剥离后仍残留 base64 痕迹：说明真实文档中存在 stripBase64Images 未覆盖的形态，
            // 打印 data:image/base64 出现处的上下文（含序号），便于精确定位残留形态。
            log.warn("AI 案例匹配：评分项 {}（{}）剥离后仍残留 base64 痕迹，data:image 出现 {} 处。上下文：{}",
                    criterion.id(), criterion.title(), countOccurrences(cleanExcerpt, "data:image"),
                    excerptContexts(cleanExcerpt, "data:image", 120));
        }
        String userPrompt = "CRITERION TITLE:\n" + criterion.title()
                + "\n\nCRITERION RULE:\n" + criterion.ruleText()
                + "\n\nRELEVANT DOCUMENT EXCERPT:\n" + cleanExcerpt
                + "\n\nReturn a single JSON object: {\"matchedSnippet\": \"exact proof text from the excerpt\", \"confidence\": 0.0-1.0}. "
                + "If the excerpt contains no proof for the criterion, return {\"matchedSnippet\": \"\", \"confidence\": 0.0}.";

        if (log.isDebugEnabled()) {
            log.debug("AI 案例匹配 Prompt（model={}，评分项 {}）：\n{}", config.model(), criterion.id(), userPrompt);
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.model());
        requestBody.put("messages", List.of(
                Map.of("role", "system", "content", "You are a professional bidding consultant. Answer only in valid JSON format. Extract exact proof text or snippets from the document that satisfy the criterion."),
                Map.of("role", "user", "content", userPrompt)
        ));
        requestBody.put("temperature", 0.2);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(config.apiKey());

        log.info("AI 案例匹配：调用大模型 {} (baseUrl={}) 抽取评分项 {}", config.model(), config.baseUrl(), criterion.id());
        ResponseEntity<String> response = restTemplate.exchange(
                config.baseUrl(),
                HttpMethod.POST,
                new HttpEntity<>(requestBody, headers),
                String.class
        );

        String rawBody = response.getBody();
        if (rawBody == null || rawBody.isBlank()) {
            log.warn("AI 案例匹配：评分项 {}（{}）大模型返回 body 为 null", criterion.id(), criterion.title());
            return null;
        }
        if (log.isDebugEnabled()) {
            log.debug("AI 案例匹配：评分项 {} 大模型原始返回（HTTP {}）：\n{}",
                    criterion.id(), response.getStatusCode().value(), truncateForLog(rawBody, 2000));
        }

        JsonNode node;
        try {
            node = objectMapper.readTree(extractJson(rawBody));
        } catch (JsonProcessingException e) {
            log.warn("AI 案例匹配：评分项 {}（{}）大模型返回非合法 JSON，跳过。原始: {}",
                    criterion.id(), criterion.title(), truncateForLog(rawBody, 300));
            return null;
        }

        AiMatchedSlice slice = new AiMatchedSlice();
        slice.setDraftId(criterion.id());
        if (node.isObject()) {
            slice.setMatchedSnippet(node.path("matchedSnippet").asText(""));
            slice.setConfidence(node.path("confidence").asDouble(0.8));
        } else if (node.isArray() && node.size() > 0) {
            // 兼容大模型返回单元素数组的形态
            JsonNode first = node.get(0);
            slice.setMatchedSnippet(first.path("matchedSnippet").asText(""));
            slice.setConfidence(first.path("confidence").asDouble(0.8));
        } else {
            log.warn("AI 案例匹配：评分项 {}（{}）返回非对象非数组 JSON，跳过", criterion.id(), criterion.title());
            return null;
        }
        if (slice.getMatchedSnippet() == null || slice.getMatchedSnippet().isBlank()) {
            log.info("AI 案例匹配：评分项 {}（{}）无匹配证据，诚实降级不伪造", criterion.id(), criterion.title());
            return null;
        }
        log.info("AI 案例匹配：评分项 {}（{}）命中，confidence={}, snippet 长度 {}",
                criterion.id(), criterion.title(), slice.getConfidence(), slice.getMatchedSnippet().length());
        return slice;
    }

    private static String truncateForLog(String text, int maxLen) {
        if (text == null) return "<null>";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...(截断，共 " + text.length() + " 字符)";
    }

    /**
     * 剥离摘录中的 markdown 内嵌 base64 图片数据，避免 LLM 网关把 base64 当多模态输入解析失败（spec 049 验收发现）。
     * 处理三种形态：标准 markdown 图片 {@code ![alt](data:image/png;base64,xxxx)}、无闭合括号的截断形态、
     * 以及可能残留的裸 data URI。base64 载荷统一替换为 {@code [image]} 占位符，保留文本语义。
     */
    static String stripBase64Images(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        // 1) 标准 markdown 图片（含闭合括号，跨行/非贪婪到右括号）
        String cleaned = text.replaceAll(
                "!\\[[^\\]]*\\]\\(\\s*data:image/[^;)]*;base64,[^)]*\\)",
                "[image]");
        // 2) 无闭合括号的截断图片（摘录 12K 截断可能切掉右括号）：data: 前缀 + base64 字符表到边界
        cleaned = cleaned.replaceAll(
                "!\\[[^\\]]*\\]\\(\\s*data:image/[^;)]*;base64,[A-Za-z0-9+/=\\s]*",
                "[image]");
        // 3) 裸 data URI（未以 markdown 语法包裹的残留）：
        //    mime 段容忍尾随空格（如 "data:image/png ;base64,"），payload 容忍换行/空白
        cleaned = cleaned.replaceAll(
                "data:image/[A-Za-z0-9.+-]+\\s*;\\s*base64,?[\\sA-Za-z0-9+/=]*",
                "[image]");
        return cleaned;
    }

    /** 统计子串出现次数（诊断用，剥离后仍残留 base64 痕迹时定位） */
    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int from = 0;
        int idx;
        while ((idx = text.indexOf(needle, from)) >= 0) {
            count++;
            from = idx + needle.length();
        }
        return count;
    }

    /** 提取子串所有出现处的前后上下文（诊断用，最多 3 处，每处前 120 字符） */
    private static String excerptContexts(String text, String needle, int radius) {
        StringBuilder sb = new StringBuilder();
        int from = 0;
        int shown = 0;
        int idx;
        while (shown < 3 && (idx = text.indexOf(needle, from)) >= 0) {
            int start = Math.max(0, idx - radius);
            int end = Math.min(text.length(), idx + needle.length() + radius);
            String ctx = text.substring(start, end).replace('\n', '⏎');
            if (sb.length() > 0) {
                sb.append(" ||| ");
            }
            sb.append("[").append(idx).append("] ").append(ctx);
            from = idx + needle.length();
            shown++;
        }
        return sb.length() == 0 ? "<none>" : sb.toString();
    }

    public String extractCategory(String category) {
        if (category == null) {
            return "其他";
        }
        String c = category.trim();
        if (c.contains("技术")) {
            return "技术";
        }
        if (c.contains("商务")) {
            return "商务";
        }
        if (c.contains("实施") || c.contains("服务")) {
            return "实施服务";
        }
        if (c.contains("资质") || c.contains("业绩")) {
            return "资质业绩";
        }
        return c;
    }

    private String extractJson(String response) {
        int startIndex = response.indexOf("{");
        int endIndex = response.lastIndexOf("}");
        if (startIndex >= 0 && endIndex > startIndex) {
            return response.substring(startIndex, endIndex + 1);
        }
        return response;
    }

    @lombok.Data
    public static class AiMatchedSlice {
        private Long draftId;
        private String matchedSnippet;
        private double confidence;
    }
}
