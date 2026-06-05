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
class CaseAiMatcher {

    private final RoutingAiProvider routingAiProvider;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public List<AiMatchedSlice> extractSlicesWithAi(String markdown, List<ProjectScoreDraft> drafts) {
        List<AiMatchedSlice> result = new ArrayList<>();
        AiProviderRuntimeConfig config = null;
        try {
            config = routingAiProvider.resolveActiveConfig();
        } catch (RuntimeException ignored) {
        }

        if (config == null) {
            // Mock 降级
            for (ProjectScoreDraft draft : drafts) {
                AiMatchedSlice slice = new AiMatchedSlice();
                slice.setDraftId(draft.getId());
                slice.setMatchedSnippet(String.format("【Mock证明材料】针对评分项 [%s]，证明王工有5年以上研发管理经验，拥有高级职称，参与过相似项目。", draft.getScoreItemTitle()));
                slice.setConfidence(0.92);
                result.add(slice);
            }
            return result;
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

            ResponseEntity<String> response = restTemplate.exchange(
                    config.baseUrl(),
                    HttpMethod.POST,
                    new HttpEntity<>(requestBody, headers),
                    String.class
            );

            if (response.getBody() != null) {
                String jsonStr = extractJson(response.getBody());
                JsonNode arrayNode = objectMapper.readTree(jsonStr);
                if (arrayNode.isArray()) {
                    for (JsonNode node : arrayNode) {
                        AiMatchedSlice slice = new AiMatchedSlice();
                        slice.setDraftId(node.path("criteriaId").asLong());
                        slice.setMatchedSnippet(node.path("matchedSnippet").asText(""));
                        slice.setConfidence(node.path("confidence").asDouble(0.8));
                        result.add(slice);
                    }
                }
            }
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("AI extraction failed, falling back to mock", e);
            // 降级兜底
            for (ProjectScoreDraft draft : drafts) {
                AiMatchedSlice slice = new AiMatchedSlice();
                slice.setDraftId(draft.getId());
                slice.setMatchedSnippet("AI提取失败兜底：匹配评分项 " + draft.getScoreItemTitle());
                slice.setConfidence(0.8);
                result.add(slice);
            }
        }
        return result;
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
