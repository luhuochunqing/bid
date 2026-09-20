package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.MultimodalScoreFact;
import com.xiyu.bid.scoreparse.domain.ScoreParseDecision;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MultimodalStructuredExtractionHandlerTest {

    @Test
    void validModelFactsAreAutoApproved() throws Exception {
        SidecarDocumentArtifactExtractor artifactExtractor = mock(SidecarDocumentArtifactExtractor.class);
        when(artifactExtractor.extract(any(byte[].class), eq("bid.pdf"))).thenReturn(artifact());
        MultimodalScoreFactExtractor model = (artifact, images) -> new MultimodalScoreFactExtractor.ExtractionResult(
                BigDecimal.TEN, List.of(fact()), "test-model", images.size());

        StructuredTableResult result = extract(artifactExtractor, model);

        assertThat(result.evidencePipeline()).isTrue();
        assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.AUTO_APPROVED);
        assertThat(result.candidates()).hasSize(1);
    }

    @Test
    void missingModelProducesReviewFactsAndNeverAutoApproves() throws Exception {
        SidecarDocumentArtifactExtractor artifactExtractor = mock(SidecarDocumentArtifactExtractor.class);
        when(artifactExtractor.extract(any(byte[].class), eq("bid.pdf"))).thenReturn(artifact());

        StructuredTableResult result = extract(artifactExtractor, null);

        assertThat(result.evidencePipeline()).isTrue();
        assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
        assertThat(result.candidates()).isEmpty();
    }

    @Test
    void modelFailureProducesReviewFactsAndNeverAutoApproves() throws Exception {
        SidecarDocumentArtifactExtractor artifactExtractor = mock(SidecarDocumentArtifactExtractor.class);
        when(artifactExtractor.extract(any(byte[].class), eq("bid.pdf"))).thenReturn(artifact());
        MultimodalScoreFactExtractor model = (artifact, images) -> { throw new IllegalStateException("boom"); };

        StructuredTableResult result = extract(artifactExtractor, model);

        assertThat(result.evidencePipeline()).isTrue();
        assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
        assertThat(result.candidates()).isEmpty();
    }

    private static StructuredTableResult extract(SidecarDocumentArtifactExtractor artifactExtractor,
                                                  MultimodalScoreFactExtractor model) {
        return MultimodalStructuredExtractionHandler.extract(
                new TenderTextSource("bid.pdf", "obs://bid.pdf", "评分办法，总分10分"),
                new LoadedBidFile.InMemory(new byte[]{1}, "bid.pdf", "application/pdf"),
                artifactExtractor, model, null);
    }

    private static MultimodalScoreFact fact() {
        return new MultimodalScoreFact("商务", "1", "", "销售额", BigDecimal.TEN,
                "销售额达到1亿元得10分", "OBJECTIVE", 1, "t1", List.of("c1"),
                "销售额达到1亿元得10分", 0.99);
    }

    private static DocumentArtifact artifact() {
        var cell = new DocumentArtifact.CanonicalTable.CanonicalCellDto(
                "c1", 1, 1, "销售额达到1亿元得10分", "销售额达到1亿元得10分", 1, 1, false, "");
        var table = new DocumentArtifact.CanonicalTable(
                "t1", 1, "商务", 2, 2, List.of(), List.of(cell), true);
        return new DocumentArtifact(new DocumentArtifact.ArtifactMeta("bid.pdf", "sha", "pdf", "2.0"),
                List.of(table), List.of());
    }
}
