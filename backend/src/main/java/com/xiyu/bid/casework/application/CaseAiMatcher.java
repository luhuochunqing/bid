package com.xiyu.bid.casework.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.ai.client.AiProviderRuntimeConfig;
import com.xiyu.bid.ai.client.RoutingAiProvider;
import com.xiyu.bid.projectworkflow.entity.ProjectScoreDraft;
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

    private final RoutingAiProvider routingAiProvider;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public List<AiMatchedSlice> extractSlicesWithAi(String markdown, List<ProjectScoreDraft> drafts) {
        if (drafts == null || drafts.isEmpty()) {
            // 没有评分项时无需 AI 提取；前置条件层（Listener）已对外承诺 0 cases 也算成功。
            return List.of();
        }
        log.info("AI 案例匹配开始：{} 个评分项，标书文本长度 {} 字符", drafts.size(),
                markdown != null ? markdown.length() : 0);
        List<AiMatchedSlice> result = new ArrayList<>();
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

        // 调用大模型
        try {
            List<Map<String, Object>> criteriaList = new ArrayList<>();
            for (ProjectScoreDraft draft : drafts) {
                criteriaList.add(Map.of(
                        "id", draft.getId(),
                        "title", draft.getScoreItemTitle(),
                        "rule", draft.getScoreRuleText()
                ));
            }

            String userPrompt = "TENDER DOCUMENT MARKDOWN:\n" + markdown + "\n\nCRITERIA:\n" +
                    objectMapper.writeValueAsString(criteriaList) + "\n\n" +
                    "Extract proof snippets from the markdown for each criterion. Return JSON array format:\n" +
                    "[ {\"criteriaId\": 1, \"matchedSnippet\": \"...\", \"confidence\": 0.95} ]";

            if (log.isDebugEnabled()) {
                log.debug("AI 案例匹配 Prompt（model={}）：\n{}", config.model(), userPrompt);
            }

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", config.model());
            requestBody.put("messages", List.of(
                    Map.of("role", "system", "content", "You are a professional bidding consultant. Answer only in valid JSON format. Extract exact proof text or snippets from the document that satisfy each criterion."),
                    Map.of("role", "user", "content", userPrompt)
            ));
            requestBody.put("temperature", 0.2);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(config.apiKey());

            log.info("AI 案例匹配：调用大模型 {} (baseUrl={})", config.model(), config.baseUrl());
            ResponseEntity<String> response = restTemplate.exchange(
                    config.baseUrl(),
                    HttpMethod.POST,
                    new HttpEntity<>(requestBody, headers),
                    String.class
            );

            String rawBody = response.getBody();
            if (log.isDebugEnabled()) {
                log.debug("AI 案例匹配：大模型原始返回（HTTP {}）：\n{}",
                        response.getStatusCode().value(),
                        rawBody != null ? truncateForLog(rawBody, 4000) : "<null>");
            }

            if (rawBody != null) {
                String jsonStr = extractJson(rawBody);
                JsonNode arrayNode = objectMapper.readTree(jsonStr);
                if (arrayNode.isArray()) {
                    for (JsonNode node : arrayNode) {
                        AiMatchedSlice slice = new AiMatchedSlice();
                        slice.setDraftId(node.path("criteriaId").asLong());
                        slice.setMatchedSnippet(node.path("matchedSnippet").asText(""));
                        slice.setConfidence(node.path("confidence").asDouble(0.8));
                        result.add(slice);
                    }
                    log.info("AI 案例匹配完成：解析到 {} 个匹配片段", result.size());
                    for (AiMatchedSlice slice : result) {
                        log.info("  - draftId={}, confidence={}, snippet={} (长度 {})",
                                slice.getDraftId(),
                                slice.getConfidence(),
                                truncateForLog(slice.getMatchedSnippet(), 200),
                                slice.getMatchedSnippet() != null ? slice.getMatchedSnippet().length() : 0);
                    }
                } else {
                    log.warn("AI 案例匹配：大模型返回非 JSON 数组，解析到 0 个片段。原始 JSON: {}",
                            truncateForLog(jsonStr, 1000));
                }
            } else {
                log.warn("AI 案例匹配：大模型返回 body 为 null");
            }
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("AI 案例匹配异常：大模型调用或解析失败", e);
            // AI 真实调用失败时直接抛错；Listener 统一发"AI 沉淀失败"通知给触发者，禁止伪造证明片段入库。
            throw new IllegalStateException("AI 案例沉淀失败：大模型调用失败，原因：" + e.getMessage(), e);
        }
        return result;
    }

    private static String truncateForLog(String text, int maxLen) {
        if (text == null) return "<null>";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...(截断，共 " + text.length() + " 字符)";
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
        int startIndex = response.indexOf("[");
        int endIndex = response.lastIndexOf("]");
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
