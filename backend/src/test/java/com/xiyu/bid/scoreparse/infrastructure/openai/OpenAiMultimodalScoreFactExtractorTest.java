package com.xiyu.bid.scoreparse.infrastructure.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiBidAgentConfigurationResolver;
import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiBidAgentRequestConfig;
import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiStructuredOutputService;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
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
    void recheckIncludesUnmarkedTablesWithoutImages() {
        OpenAiStructuredOutputService output = mock(OpenAiStructuredOutputService.class);
        OpenAiBidAgentConfigurationResolver resolver = mock(OpenAiBidAgentConfigurationResolver.class);
        OpenAiBidAgentRequestConfig config = mock(OpenAiBidAgentRequestConfig.class);
        when(config.model()).thenReturn("test-model");
        when(resolver.resolve(any())).thenReturn(config);
        var response = new OpenAiMultimodalScoreFactExtractor.Output();
        response.declaredTotal = BigDecimal.TEN;
        response.facts = List.of();
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        when(output.request(prompt.capture(), eq(OpenAiMultimodalScoreFactExtractor.Output.class),
                eq(config), any())).thenReturn(response);
        var extractor = new OpenAiMultimodalScoreFactExtractor(output, resolver, new ObjectMapper());

        var result = extractor.recheck(artifact());

        assertThat(result.declaredTotal()).isEqualByComparingTo("10");
        assertThat(result.imageCount()).isZero();
        assertThat(prompt.getValue()).contains("score-cell", "全部正式评分项", "quotedText");
        assertThat(prompt.getValue()).contains("non-score-cell");
        assertThat(prompt.getValue()).doesNotContain("sourceXmlPath");
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
