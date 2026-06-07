package com.xiyu.bid.ai.client;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.StructuredResponse;
import com.openai.models.responses.StructuredResponseCreateParams;
import com.xiyu.bid.ai.dto.AiAnalysisResponse;
import com.xiyu.bid.ai.dto.DimensionScore;
import com.xiyu.bid.entity.Tender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@ConditionalOnProperty(name = "ai.provider", havingValue = "openai")
@Slf4j
public class OpenAiProvider implements AiProvider {

    private final OpenAIClient client;
    private final String model;

    public OpenAiProvider(
        @Value("${ai.openai.api-key:}") String apiKey,
        @Value("${ai.openai.base-url:https://api.openai.com/v1}") String baseUrl,
        @Value("${ai.openai.model:gpt-5.2}") String pModel,
        @Value("${ai.openai.timeout:PT30S}") Duration timeout
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("ai.openai.api-key must be configured when ai.provider=openai");
        }
        this.client = OpenAIOkHttpClient.builder()
            .apiKey(apiKey)
            .baseUrl(baseUrl)
            .timeout(timeout)
            .build();
        this.model = pModel;
    }

    @Override
    public AiAnalysisResponse analyzeTender(String content, Map<String, Object> context) {
        log.debug("OpenAI analyzing tender content length: {}", content != null ? content.length() : 0);
        return toResponse(requestAnalysis(buildTenderPrompt(content, context), AnalysisOutput.class));
    }

    @Override
    public AiAnalysisResponse analyzeProject(Long projectId, Map<String, Object> context) {
        log.debug("OpenAI analyzing project id: {}", projectId);
        return toResponse(requestAnalysis(buildProjectPrompt(projectId, context), AnalysisOutput.class));
    }

    private <T extends AnalysisOutput> T requestAnalysis(String prompt, Class<T> responseType) {
        StructuredResponseCreateParams<T> params = ResponseCreateParams.builder()
            .input(prompt)
            .model(model)
            .text(responseType)
            .build();

        StructuredResponse<T> response = client.responses().create(params);
        return extractPayload(response)
            .orElseThrow(() -> new IllegalStateException("OpenAI structured response did not include output text"));
    }

    private <T> Optional<T> extractPayload(StructuredResponse<T> response) {
        return response.output().stream()
            .map(item -> item.message())
            .flatMap(Optional::stream)
            .flatMap(message -> message.content().stream())
            .map(content -> content.outputText())
            .flatMap(Optional::stream)
            .findFirst();
    }

    private String buildTenderPrompt(String content, Map<String, Object> context) {
        StringBuilder prompt = new StringBuilder("Analyze the following tender opportunity and provide a comprehensive assessment.\n\n");
        if (content != null && !content.isBlank()) {
            prompt.append("TENDER CONTENT:\n").append(content).append("\n\n");
        }
        if (context != null && !context.isEmpty()) {
            prompt.append("ADDITIONAL CONTEXT:\n");
            context.forEach((key, value) -> prompt.append("- ").append(key).append(": ").append(value).append("\n"));
            prompt.append("\n");
        }
        prompt.append("""
            Focus on tender bid suitability. Return:
            - score: integer 0-100
            - riskLevel: LOW, MEDIUM, or HIGH
            - strengths, weaknesses, recommendations: non-empty lists when possible
            - dimensionScores: Technical, Financial, Timing
            """);
        return prompt.toString();
    }

    private String buildProjectPrompt(Long projectId, Map<String, Object> context) {
        StringBuilder prompt = new StringBuilder("Analyze the following project and provide a comprehensive assessment.\n\n");
        prompt.append("PROJECT ID: ").append(projectId).append("\n\n");
        if (context != null && !context.isEmpty()) {
            prompt.append("PROJECT CONTEXT:\n");
            context.forEach((key, value) -> prompt.append("- ").append(key).append(": ").append(value).append("\n"));
            prompt.append("\n");
        }
        prompt.append("""
            Focus on project delivery readiness. Return:
            - score: integer 0-100
            - riskLevel: LOW, MEDIUM, or HIGH
            - strengths, weaknesses, recommendations: non-empty lists when possible
            - dimensionScores: Team, Resources, Risk
            """);
        return prompt.toString();
    }

    private AiAnalysisResponse toResponse(AnalysisOutput output) {
        return AiAnalysisResponse.builder()
            .score(output.score)
            .riskLevel(parseRiskLevel(output.riskLevel))
            .strengths(output.strengths == null ? List.of() : output.strengths)
            .weaknesses(output.weaknesses == null ? List.of() : output.weaknesses)
            .recommendations(output.recommendations == null ? List.of() : output.recommendations)
            .dimensionScores(output.dimensionScores == null ? List.of() : output.dimensionScores.stream()
                .map(item -> DimensionScore.builder()
                    .dimension(item.dimension)
                    .score(item.score)
                    .details(item.details)
                    .build())
                .toList())
            .build();
    }

    private Tender.RiskLevel parseRiskLevel(String riskLevel) {
        if (riskLevel == null || riskLevel.isBlank()) {
            return Tender.RiskLevel.MEDIUM;
        }
        try {
            return Tender.RiskLevel.valueOf(riskLevel.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            log.warn("OpenAI returned unsupported risk level '{}', defaulting to MEDIUM", riskLevel);
            return Tender.RiskLevel.MEDIUM;
        }
    }

    public static class AnalysisOutput {
        public Integer score;
        public String riskLevel;
        public List<String> strengths;
        public List<String> weaknesses;
        public List<String> recommendations;
        public List<DimensionOutput> dimensionScores;
    }

    public static final class DimensionOutput {
        public String dimension;
        public Integer score;
        public String details;
    }
}
