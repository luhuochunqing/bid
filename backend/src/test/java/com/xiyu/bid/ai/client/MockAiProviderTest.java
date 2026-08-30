package com.xiyu.bid.ai.client;

import com.xiyu.bid.ai.dto.AiAnalysisResponse;
import com.xiyu.bid.ai.dto.BidDocumentQualityAiPreviewDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MockAiProviderTest {

    private MockAiProvider provider;

    @BeforeEach
    void setUp() {
        provider = new MockAiProvider();
    }

    @Test
    @DisplayName("chatCompletionJsonObject: 当 prompt 包含 scoringItems 时返回空结构 JSON")
    void testChatCompletionJsonObject_WhenPromptContainsScoringItems() {
        String result = provider.chatCompletionJsonObject("Extract scoringItems from tender", 1024);

        assertThat(result).contains("scoringItems");
        assertThat(result).contains("mandatoryQualifications");
    }

    @Test
    @DisplayName("chatCompletionJsonObject: 普通语义裁决 prompt 返回 PASS 结构化 JSON")
    void testChatCompletionJsonObject_WhenStandardAdjudicationPrompt() {
        String result = provider.chatCompletionJsonObject("请核查资质是否齐备", 1024);

        assertThat(result).contains("\"status\":\"PASS\"");
        assertThat(result).contains("模拟语义比对通过");
    }

    @Test
    @DisplayName("chatCompletion: 返回合法但全空的结构化 JSON")
    void testChatCompletion() {
        String result = provider.chatCompletion("Extract requirements", 1024);

        assertThat(result).contains("scoringItems");
        assertThat(result).contains("mandatoryQualifications");
    }

    @Test
    @DisplayName("embed: 返回 1024 维 float 向量")
    void testEmbed() {
        float[] vector = provider.embed("sample text");

        assertThat(vector).hasSize(1024);
    }

    @Test
    @DisplayName("analyzeTender: 返回模拟分析响应")
    void testAnalyzeTender() {
        AiAnalysisResponse response = provider.analyzeTender("标书正文内容".repeat(100), Map.of("budget", 2000000));

        assertThat(response).isNotNull();
        assertThat(response.getScore()).isBetween(0, 100);
        assertThat(response.getRiskLevel()).isNotNull();
        assertThat(response.getDimensionScores()).hasSize(3);
    }

    @Test
    @DisplayName("analyzeProject: 返回项目模拟分析响应")
    void testAnalyzeProject() {
        AiAnalysisResponse response = provider.analyzeProject(10L, Map.of("teamSize", 5));

        assertThat(response).isNotNull();
        assertThat(response.getScore()).isBetween(0, 100);
        assertThat(response.getRiskLevel()).isNotNull();
        assertThat(response.getDimensionScores()).hasSize(3);
    }

    @Test
    @DisplayName("previewBidDocumentQuality: 有正文时返回良好评估")
    void testPreviewBidDocumentQuality_WithContent() {
        BidDocumentQualityAiPreviewDTO preview = provider.previewBidDocumentQuality("标书正文", "招标要求");

        assertThat(preview.getOverallAssessment()).contains("标书整体质量良好");
        assertThat(preview.getKeyRisks()).isNotEmpty();
    }

    @Test
    @DisplayName("previewBidDocumentQuality: 正文为空时返回缺失警告")
    void testPreviewBidDocumentQuality_EmptyContent() {
        BidDocumentQualityAiPreviewDTO preview = provider.previewBidDocumentQuality("", "");

        assertThat(preview.getOverallAssessment()).contains("标书内容严重缺失");
    }
}
