package com.xiyu.bid.ai.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.ai.dto.AiAnalysisResponse;
import com.xiyu.bid.ai.dto.BidDocumentQualityAiPreviewDTO;
import com.xiyu.bid.ai.dto.DimensionScore;
import com.xiyu.bid.entity.Tender;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class AiResponseParser {

    private final ObjectMapper objectMapper;

    public AiResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String extractContentFromResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && choices.size() > 0)
                return choices.get(0).path("message").path("content").asText();
            throw new RuntimeException("Invalid AI response format");
        } catch (JsonProcessingException exception) {
            throw new RuntimeException("Failed to parse AI response", exception);
        }
    }

    public AiAnalysisResponse parseAnalysisResponse(String response) {
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

    public BidDocumentQualityAiPreviewDTO parseBidPreview(String response) {
        try {
            JsonNode root = objectMapper.readTree(extractJson(response));
            return BidDocumentQualityAiPreviewDTO.builder()
                    .overallAssessment(root.path("overallAssessment").asText(null))
                    .keyRisks(parseStringList(root.path("keyRisks")))
                    .build();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse bid preview", e);
        }
    }

    private String extractJson(String response) {
        int start = response.indexOf("{");
        int end = response.lastIndexOf("}");
        if (start >= 0 && end > start) return response.substring(start, end + 1);
        return response;
    }

    private List<String> parseStringList(JsonNode node) {
        List<String> result = new ArrayList<>();
        if (node.isArray()) for (JsonNode item : node) result.add(item.asText());
        return result;
    }

    private List<DimensionScore> parseDimensionScores(JsonNode node) {
        List<DimensionScore> result = new ArrayList<>();
        if (node.isArray()) for (JsonNode item : node) {
            result.add(DimensionScore.builder()
                    .dimension(item.path("dimension").asText())
                    .score(Math.max(0, Math.min(100, item.path("score").asInt())))
                    .details(item.path("details").asText())
                    .build());
        }
        return result;
    }
}
