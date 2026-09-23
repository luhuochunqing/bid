package com.xiyu.bid.scoreparse.infrastructure.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiBidAgentConfigurationResolver;
import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiBidAgentRequestConfig;
import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiStructuredOutputService;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import com.xiyu.bid.scoreparse.domain.DocumentImage;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenAiMultimodalScoreFactExtractorTest {
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
