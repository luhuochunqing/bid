package com.xiyu.bid.scoreparse.infrastructure.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiBidAgentConfigurationResolver;
import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiBidAgentRequestConfig;
import com.xiyu.bid.biddraftagent.infrastructure.openai.MultimodalImage;
import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiStructuredOutputService;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import com.xiyu.bid.scoreparse.domain.DocumentImage;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

class OpenAiMultimodalScoreFactExtractorTest {
    @Test
    void textExtractionSendsFullTextContextWithoutImages() {
        OpenAiStructuredOutputService output = mock(OpenAiStructuredOutputService.class);
        OpenAiBidAgentConfigurationResolver resolver = mock(OpenAiBidAgentConfigurationResolver.class);
        OpenAiBidAgentRequestConfig config = mock(OpenAiBidAgentRequestConfig.class);
        when(config.model()).thenReturn("test-model");
        when(resolver.resolve(any())).thenReturn(config);
        var response = new OpenAiMultimodalScoreFactExtractor.Output();
        response.declaredTotal = BigDecimal.TEN;
        response.facts = List.of();
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<List<MultimodalImage>> images = ArgumentCaptor.forClass(List.class);
        when(output.request(prompt.capture(), images.capture(), eq(OpenAiMultimodalScoreFactExtractor.Output.class),
                eq(config), any())).thenReturn(response);
        var extractor = new OpenAiMultimodalScoreFactExtractor(output, resolver, new ObjectMapper());

        var result = extractor.extractWithText(artifact(), "score-cell 跨页评分规则上下文 marker");

        assertThat(prompt.getValue()).contains("跨页评分规则上下文 marker", "anchor:", "sourceCellIds");
        assertThat(images.getValue()).isEmpty();
        assertThat(result.imageCount()).isZero();
    }

    @Test
    void textExtractionSendsScoringAndContractAuditRangesSeparately() {
        OpenAiStructuredOutputService output = mock(OpenAiStructuredOutputService.class);
        OpenAiBidAgentConfigurationResolver resolver = mock(OpenAiBidAgentConfigurationResolver.class);
        OpenAiBidAgentRequestConfig config = mock(OpenAiBidAgentRequestConfig.class);
        when(config.model()).thenReturn("test-model");
        when(resolver.resolve(any())).thenReturn(config);
        var response = new OpenAiMultimodalScoreFactExtractor.Output();
        response.declaredTotal = BigDecimal.valueOf(100);
        var item = new OpenAiMultimodalScoreFactExtractor.Item();
        item.code = "1";
        item.dimension = "评分项";
        item.weight = BigDecimal.valueOf(5);
        item.ruleText = "原文得5分";
        item.quotedText = "原文得5分";
        item.childAggregation = "SUM";
        response.facts = List.of(item);
        ArgumentCaptor<String> prompts = ArgumentCaptor.forClass(String.class);
        when(output.request(prompts.capture(), any(), eq(OpenAiMultimodalScoreFactExtractor.Output.class),
                eq(config), any())).thenReturn(response);
        var extractor = new OpenAiMultimodalScoreFactExtractor(output, resolver, new ObjectMapper());
        String source = "# 本项目评分办法\n总分100分。\n## 商务评分\n原文得5分。\n"
                + "# 合同履约考核\n服务期每月另计40分。";

        var result = extractor.extractWithText(artifact(), source);

        assertThat(prompts.getAllValues()).hasSize(2);
        assertThat(prompts.getAllValues().get(0)).contains("本项目评分办法", "商务评分")
                .doesNotContain("服务期每月");
        assertThat(prompts.getAllValues().get(1)).contains("合同履约考核", "服务期每月")
                .doesNotContain("商务评分");
        assertThat(result.facts()).extracting(com.xiyu.bid.scoreparse.domain.MultimodalScoreFact::scopeStatus)
                .containsExactly("IN_SCHEME", "OUT_OF_SCHEME");
        assertThat(result.declaredTotal()).isEqualByComparingTo("100");
    }

    @Test
    void longTextIsFullyCoveredByBoundedOverlappingTextOnlyRequestsForExtractionAndRecheck() {
        OpenAiStructuredOutputService output = mock(OpenAiStructuredOutputService.class);
        OpenAiBidAgentConfigurationResolver resolver = mock(OpenAiBidAgentConfigurationResolver.class);
        OpenAiBidAgentRequestConfig config = mock(OpenAiBidAgentRequestConfig.class);
        when(config.model()).thenReturn("test-model");
        when(resolver.resolve(any())).thenReturn(config);
        var response = new OpenAiMultimodalScoreFactExtractor.Output();
        response.declaredTotal = BigDecimal.TEN;
        var item = new OpenAiMultimodalScoreFactExtractor.Item();
        item.code = "1";
        item.weight = BigDecimal.TEN;
        item.tableId = "table";
        item.sourceCellIds = List.of("c1");
        item.quotedText = "逐字规则";
        item.ruleText = "逐字规则";
        item.childAggregation = "SUM";
        response.facts = List.of(item);
        ArgumentCaptor<String> prompts = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<List<MultimodalImage>> images = ArgumentCaptor.forClass(List.class);
        when(output.request(prompts.capture(), images.capture(), eq(OpenAiMultimodalScoreFactExtractor.Output.class),
                eq(config), any())).thenReturn(response);
        var extractor = new OpenAiMultimodalScoreFactExtractor(output, resolver, new ObjectMapper());
        String text = "BEGIN_MARKER" + java.util.stream.IntStream.range(0, 3_000)
                .mapToObj(i -> "甲段" + i + "固定文本\n").collect(java.util.stream.Collectors.joining())
                + "MIDDLE_MARKER" + java.util.stream.IntStream.range(3_000, 6_000)
                .mapToObj(i -> "乙段" + i + "固定文本\n").collect(java.util.stream.Collectors.joining()) + "END_MARKER";
        AtomicInteger extractedHeartbeats = new AtomicInteger();

        var extracted = extractor.extractWithTextAndProgress(artifact(), text, extractedHeartbeats::incrementAndGet);

        assertThat(prompts.getAllValues()).hasSizeGreaterThan(2);
        assertThat(prompts.getAllValues()).allSatisfy(prompt -> assertThat(prompt.length()).isLessThanOrEqualTo(24_000));
        assertThat(String.join("\n", prompts.getAllValues()))
                .contains("BEGIN_MARKER", "MIDDLE_MARKER", "END_MARKER");
        assertFullBoundedCoverage(text, prompts.getAllValues());
        assertThat(images.getAllValues()).allSatisfy(List::isEmpty);
        assertThat(extracted.facts()).hasSize(1);
        assertThat(extracted.imageCount()).isZero();
        assertThat(extractedHeartbeats).hasValue(prompts.getAllValues().size());

        reset(output);
        ArgumentCaptor<String> recheckPrompts = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<List<MultimodalImage>> recheckImages = ArgumentCaptor.forClass(List.class);
        AtomicInteger recheckHeartbeats = new AtomicInteger();
        when(output.request(recheckPrompts.capture(), recheckImages.capture(),
                eq(OpenAiMultimodalScoreFactExtractor.Output.class), eq(config), any())).thenReturn(response);
        extractor.recheckWithTextAndProgress(artifact(), text, recheckHeartbeats::incrementAndGet);
        assertThat(recheckPrompts.getAllValues()).hasSize(prompts.getAllValues().size());
        assertThat(String.join("\n", recheckPrompts.getAllValues()))
                .contains("BEGIN_MARKER", "MIDDLE_MARKER", "END_MARKER");
        assertFullBoundedCoverage(text, recheckPrompts.getAllValues());
        assertThat(recheckImages.getAllValues()).allSatisfy(List::isEmpty);
        assertThat(recheckHeartbeats).hasValue(recheckPrompts.getAllValues().size());
    }

    private static void assertFullBoundedCoverage(String source, List<String> prompts) {
        String marker = "边界重叠处允许重复：\n";
        String suffix = "\n当前分片结束；它只是全文的一部分。";
        List<String> chunks = prompts.stream().map(prompt -> {
            int start = prompt.indexOf(marker) + marker.length();
            return prompt.substring(start, prompt.indexOf(suffix, start));
        }).toList();
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.length()).isBetween(1, 6_000));
        StringBuilder restored = new StringBuilder(chunks.getFirst());
        for (int i = 1; i < chunks.size(); i++) {
            String previous = chunks.get(i - 1), current = chunks.get(i);
            int overlap = Math.min(previous.length(), current.length());
            while (overlap > 0 && !previous.endsWith(current.substring(0, overlap))) overlap--;
            assertThat(overlap).isPositive();
            restored.append(current.substring(overlap));
        }
        assertThat(restored.toString()).isEqualTo(source);
    }

    @Test
    void textExtractionDoesNotPutTheFullStructuredArtifactIntoEveryTextChunk() {
        OpenAiStructuredOutputService output = mock(OpenAiStructuredOutputService.class);
        OpenAiBidAgentConfigurationResolver resolver = mock(OpenAiBidAgentConfigurationResolver.class);
        OpenAiBidAgentRequestConfig config = mock(OpenAiBidAgentRequestConfig.class);
        when(config.model()).thenReturn("test-model");
        when(resolver.resolve(any())).thenReturn(config);
        var response = new OpenAiMultimodalScoreFactExtractor.Output();
        response.facts = List.of();
        ArgumentCaptor<String> prompts = ArgumentCaptor.forClass(String.class);
        when(output.request(prompts.capture(), any(), eq(OpenAiMultimodalScoreFactExtractor.Output.class),
                eq(config), any())).thenReturn(response);

        var hugeCell = new DocumentArtifact.CanonicalTable.CanonicalCellDto(
                "huge", 1, 1, "证据".repeat(20_000), "", 1, 1, false, "xml");
        var hugeArtifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("d", "sha", "pdf", "2.0"),
                List.of(new DocumentArtifact.CanonicalTable("score", 1, "评分办法", 1, 1,
                        List.of(), List.of(hugeCell), true)), List.of());

        var extractor = new OpenAiMultimodalScoreFactExtractor(output, resolver, new ObjectMapper());
        extractor.extractWithText(hugeArtifact, "正文评分规则 marker");

        assertThat(prompts.getAllValues()).isNotEmpty();
        assertThat(prompts.getAllValues()).allSatisfy(prompt -> assertThat(prompt).doesNotContain("证据".repeat(1000)));
        assertThat(prompts.getAllValues()).allSatisfy(prompt -> assertThat(prompt).contains("正文评分规则 marker"));
    }

    @Test
    void omittedModelAggregationBecomesUnknownAndExplicitValueIsPreserved() {
        var omitted = new OpenAiMultimodalScoreFactExtractor.Item();
        omitted.childAggregation = null;
        var explicit = new OpenAiMultimodalScoreFactExtractor.Item();
        explicit.childAggregation = "SHARED_CAP";

        assertThat(omitted.toDomain().childAggregation()).isEqualTo(com.xiyu.bid.scoreparse.domain.ChildAggregation.UNKNOWN);
        assertThat(explicit.toDomain().childAggregation()).isEqualTo(com.xiyu.bid.scoreparse.domain.ChildAggregation.SHARED_CAP);
    }

    @Test
    void recheckIncludesScoringPageImagesAndCellPageAttribution() {
        OpenAiStructuredOutputService output = mock(OpenAiStructuredOutputService.class);
        OpenAiBidAgentConfigurationResolver resolver = mock(OpenAiBidAgentConfigurationResolver.class);
        OpenAiBidAgentRequestConfig config = mock(OpenAiBidAgentRequestConfig.class);
        when(config.model()).thenReturn("test-model");
        when(resolver.resolve(any())).thenReturn(config);
        var response = new OpenAiMultimodalScoreFactExtractor.Output();
        response.declaredTotal = BigDecimal.TEN;
        response.facts = List.of();
        ArgumentCaptor<String> imagePrompt = ArgumentCaptor.forClass(String.class);
        when(output.request(imagePrompt.capture(), any(), eq(OpenAiMultimodalScoreFactExtractor.Output.class), eq(config), any()))
                .thenReturn(response);
        var extractor = new OpenAiMultimodalScoreFactExtractor(output, resolver, new ObjectMapper());

        var source = artifact();
        var images = List.of(
                new DocumentImage(0, "image/png", null, 1, 10, 10, "page-one"),
                new DocumentImage(1, "image/png", null, 3, 10, 10, "page-three"));
        var initial = extractor.extract(source, images);
        var result = extractor.recheck(source, images);

        assertThat(result.declaredTotal()).isEqualByComparingTo("10");
        assertThat(initial.imageCount()).isEqualTo(2);
        assertThat(result.imageCount()).isEqualTo(2);
        assertThat(imagePrompt.getAllValues()).hasSize(2);
        assertThat(imagePrompt.getAllValues().get(0)).contains("score-cell", "sourcePage", "sourceXmlPath");
        assertThat(imagePrompt.getAllValues().get(1)).contains("score-cell", "全部正式评分项", "quotedText")
                .contains("sourcePage").doesNotContain("sourceXmlPath").contains("non-score-cell");
    }

    private static DocumentArtifact artifact() {
        var scoreCell = new DocumentArtifact.CanonicalTable.CanonicalCellDto(
                "c1", 1, 1, "score-cell", "normalized", 1, 1, false, "sourceXmlPath");
        var otherCell = new DocumentArtifact.CanonicalTable.CanonicalCellDto(
                "c2", 1, 1, "non-score-cell", "normalized", 1, 1, false, "sourceXmlPath");
        var score = new DocumentArtifact.CanonicalTable("score", 3, "评分办法", 1, 1,
                List.of(), List.of(scoreCell), true);
        var other = new DocumentArtifact.CanonicalTable("other", 1, "资格审查", 1, 1,
                List.of(), List.of(otherCell), false);
        return new DocumentArtifact(new DocumentArtifact.ArtifactMeta("bid.pdf", "sha", "pdf", "2.0"),
                List.of(other, score), List.of());
    }
}
