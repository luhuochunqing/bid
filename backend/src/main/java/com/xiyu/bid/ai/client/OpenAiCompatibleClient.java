package com.xiyu.bid.ai.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.ai.dto.AiAnalysisResponse;
import com.xiyu.bid.ai.dto.DimensionScore;
import com.xiyu.bid.entity.Tender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class OpenAiCompatibleClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleClient(RestTemplateBuilder restTemplateBuilder, ObjectMapper pObjectMapper) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(TIMEOUT)
                .setReadTimeout(TIMEOUT)
                .build();
        this.objectMapper = pObjectMapper;
    }

    public AiAnalysisResponse analyzeTender(
            AiProviderRuntimeConfig config,
            String content,
            Map<String, Object> context
    ) {
        String prompt = buildTenderAnalysisPrompt(content, context);
        return parseAnalysisResponse(callChatCompletion(config, prompt, 2000));
    }

    public AiAnalysisResponse analyzeProject(
            AiProviderRuntimeConfig config,
            Long projectId,
            Map<String, Object> context
    ) {
        String prompt = buildProjectAnalysisPrompt(projectId, context);
        return parseAnalysisResponse(callChatCompletion(config, prompt, 2000));
    }

    public void testConnection(AiProviderRuntimeConfig config) {
        callChatCompletion(config, "Return only the word OK.", 16);
    }

    private String callChatCompletion(AiProviderRuntimeConfig config, String prompt, int maxTokens) {
        if (config.apiKey() == null || config.apiKey().isBlank()) {
            throw new IllegalStateException("AI API key is not configured");
        }
        if (config.baseUrl() == null || config.baseUrl().isBlank()) {
            throw new IllegalStateException("AI base URL is not configured");
        }
        if (config.model() == null || config.model().isBlank()) {
            throw new IllegalStateException("AI model is not configured");
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.model());
        requestBody.put("messages", List.of(
                Map.of("role", "system", "content", "You are an expert bidding consultant analyzing tender opportunities and projects."),
                Map.of("role", "user", "content", prompt)
        ));
        requestBody.put("temperature", 0.2);
        requestBody.put("max_tokens", maxTokens);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(config.apiKey());

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    config.baseUrl(),
                    HttpMethod.POST,
                    new HttpEntity<>(requestBody, headers),
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return extractContentFromResponse(response.getBody());
            }
            throw new RuntimeException("AI API request failed with status: " + response.getStatusCode());
        } catch (HttpStatusCodeException exception) {
            String message = buildProviderErrorMessage(config.providerCode(), exception);
            log.warn("AI provider {} request failed: {}", config.providerCode(), message);
            throw new RuntimeException(message, exception);
        } catch (RuntimeException exception) {
            String message = "调用 AI 厂商 " + providerDisplayName(config.providerCode()) + " 失败：" + safeMessage(exception);
            log.warn("AI provider {} request failed: {}", config.providerCode(), safeMessage(exception));
            throw new RuntimeException(message, exception);
        }
    }

    private String buildProviderErrorMessage(String providerCode, HttpStatusCodeException exception) {
        String providerName = providerDisplayName(providerCode);
        String providerMessage = extractProviderErrorMessage(exception.getResponseBodyAsString());
        String statusText = exception.getStatusCode().value() + " " + exception.getStatusText();

        if (exception.getStatusCode().value() == 402 && containsIgnoreCase(providerMessage, "insufficient balance")) {
            return providerName + " API 余额不足，请在 " + providerName + " 控制台充值，或更换有余额的 API Key 后再测试。";
        }
        if (exception.getStatusCode().value() == 401 || exception.getStatusCode().value() == 403) {
            return providerName + " API Key 无效或无权限，请检查后台配置的 API Key。";
        }
        if (exception.getStatusCode().value() == 429) {
            return providerName + " API 请求过于频繁或额度受限，请稍后重试或检查厂商限额。";
        }

        if (providerMessage != null && !providerMessage.isBlank()) {
            return providerName + " API 请求失败（" + statusText + "）：" + providerMessage;
        }
        return providerName + " API 请求失败（" + statusText + "）。";
    }

    private String extractProviderErrorMessage(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode error = root.path("error");
            if (error.isObject()) {
                String message = error.path("message").asText("");
                String code = error.path("code").asText("");
                if (!message.isBlank() && !code.isBlank()) {
                    return message + " (" + code + ")";
                }
                if (!message.isBlank()) {
                    return message;
                }
            }
            return root.path("message").asText("");
        } catch (JsonProcessingException ignored) {
            return responseBody.length() > 300 ? responseBody.substring(0, 300) : responseBody;
        }
    }

    private boolean containsIgnoreCase(String value, String expected) {
        return value != null && value.toLowerCase().contains(expected.toLowerCase());
    }

    private String providerDisplayName(String providerCode) {
        return switch (providerCode == null ? "" : providerCode.trim().toLowerCase()) {
            case "openai" -> "OpenAI";
            case "deepseek" -> "DeepSeek";
            case "qwen" -> "通义千问";
            case "doubao" -> "豆包";
            default -> providerCode == null || providerCode.isBlank() ? "AI" : providerCode;
        };
    }

    private String safeMessage(Throwable exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? "未知错误"
                : exception.getMessage();
    }

    private String buildTenderAnalysisPrompt(String content, Map<String, Object> context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Analyze the following tender opportunity and provide a comprehensive assessment.\n\n");

        if (content != null && !content.isEmpty()) {
            prompt.append("TENDER CONTENT:\n").append(content).append("\n\n");
        }

        if (context != null && !context.isEmpty()) {
            prompt.append("ADDITIONAL CONTEXT:\n");
            context.forEach((key, value) -> prompt.append("- ").append(key).append(": ").append(value).append("\n"));
            prompt.append("\n");
        }

        prompt.append("""
                Please provide your analysis in JSON format with the following structure:
                {
                  "score": <integer 0-100>,
                  "riskLevel": <"LOW", "MEDIUM", or "HIGH">,
                  "strengths": ["<strength 1>", "<strength 2>", ...],
                  "weaknesses": ["<weakness 1>", "<weakness 2>", ...],
                  "recommendations": ["<recommendation 1>", "<recommendation 2>", ...],
                  "dimensionScores": [
                    {"dimension": "Technical", "score": <0-100>, "details": "<explanation>"},
                    {"dimension": "Financial", "score": <0-100>, "details": "<explanation>"},
                    {"dimension": "Timing", "score": <0-100>, "details": "<explanation>"}
                  ]
                }
                """);

        return prompt.toString();
    }

    private String buildProjectAnalysisPrompt(Long projectId, Map<String, Object> context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Analyze the following project and provide a comprehensive assessment.\n\n");
        prompt.append("PROJECT ID: ").append(projectId).append("\n\n");

        if (context != null && !context.isEmpty()) {
            prompt.append("PROJECT CONTEXT:\n");
            context.forEach((key, value) -> prompt.append("- ").append(key).append(": ").append(value).append("\n"));
            prompt.append("\n");
        }

        prompt.append("""
                Please provide your analysis in JSON format with the following structure:
                {
                  "score": <integer 0-100>,
                  "riskLevel": <"LOW", "MEDIUM", or "HIGH">,
                  "strengths": ["<strength 1>", "<strength 2>", ...],
                  "weaknesses": ["<weakness 1>", "<weakness 2>", ...],
                  "recommendations": ["<recommendation 1>", "<recommendation 2>", ...],
                  "dimensionScores": [
                    {"dimension": "Team", "score": <0-100>, "details": "<explanation>"},
                    {"dimension": "Resources", "score": <0-100>, "details": "<explanation>"},
                    {"dimension": "Risk", "score": <0-100>, "details": "<explanation>"}
                  ]
                }
                """);

        return prompt.toString();
    }

    private String extractContentFromResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                return choices.get(0).path("message").path("content").asText();
            }
            throw new RuntimeException("Invalid AI response format");
        } catch (JsonProcessingException exception) {
            throw new RuntimeException("Failed to parse AI response", exception);
        }
    }

    private AiAnalysisResponse parseAnalysisResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(extractJson(response));
            int score = Math.max(0, Math.min(100, root.path("score").asInt()));
            Tender.RiskLevel riskLevel = Tender.RiskLevel.valueOf(root.path("riskLevel").asText("MEDIUM"));

            return AiAnalysisResponse.builder()
                    .score(score)
                    .riskLevel(riskLevel)
                    .strengths(parseStringList(root.path("strengths")))
                    .weaknesses(parseStringList(root.path("weaknesses")))
                    .recommendations(parseStringList(root.path("recommendations")))
                    .dimensionScores(parseDimensionScores(root.path("dimensionScores")))
                    .build();
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new RuntimeException("Failed to parse AI analysis response", exception);
        }
    }

    private String extractJson(String response) {
        int startIndex = response.indexOf("{");
        int endIndex = response.lastIndexOf("}");
        if (startIndex >= 0 && endIndex > startIndex) {
            return response.substring(startIndex, endIndex + 1);
        }
        return response;
    }

    private List<String> parseStringList(JsonNode node) {
        List<String> result = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                result.add(item.asText());
            }
        }
        return result;
    }

    private List<DimensionScore> parseDimensionScores(JsonNode node) {
        List<DimensionScore> result = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                result.add(DimensionScore.builder()
                        .dimension(item.path("dimension").asText())
                        .score(Math.max(0, Math.min(100, item.path("score").asInt())))
                        .details(item.path("details").asText())
                        .build());
            }
        }
        return result;
    }
}
