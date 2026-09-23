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

        var result = extractor.extractWithText(artifact(), "跨页评分规则上下文 marker");

        assertThat(prompt.getValue()).contains("跨页评分规则上下文 marker");
        assertThat(images.getValue()).isEmpty();
        assertThat(result.imageCount()).isZero();
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
        String text = "BEGIN_MARKER" + "甲".repeat(30_000) + "MIDDLE_MARKER"
                + "乙".repeat(30_000) + "END_MARKER";
        AtomicInteger extractedHeartbeats = new AtomicInteger();

        var extracted = extractor.extractWithTextAndProgress(artifact(), text, extractedHeartbeats::incrementAndGet);

        assertThat(prompts.getAllValues()).hasSizeGreaterThan(2);
        assertThat(prompts.getAllValues()).allSatisfy(prompt -> assertThat(prompt.length()).isLessThanOrEqualTo(24_000));
        assertThat(String.join("\n", prompts.getAllValues()))
                .contains("BEGIN_MARKER", "MIDDLE_MARKER", "END_MARKER");
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
        assertThat(recheckImages.getAllValues()).allSatisfy(List::isEmpty);
        assertThat(recheckHeartbeats).hasValue(recheckPrompts.getAllValues().size());
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
