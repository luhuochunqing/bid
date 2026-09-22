package com.xiyu.bid.scoreparse.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.scoreparse.domain.MultimodalScoreFact;
import com.xiyu.bid.scoreparse.domain.ScoreParseDecision;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MultimodalStructuredExtractionHandlerTest {

    @Test
    void modelFailureRechecksWithModelBeforeShowingIncompleteFallback() throws Exception {
        SidecarDocumentArtifactExtractor artifactExtractor = mock(SidecarDocumentArtifactExtractor.class);
        when(artifactExtractor.extract(any(byte[].class), eq("bid.pdf"))).thenReturn(artifact());
        int[] rechecks = {0};
        MultimodalScoreFactExtractor model = new MultimodalScoreFactExtractor() {
            @Override
            public ExtractionResult extract(DocumentArtifact input, List<com.xiyu.bid.scoreparse.domain.DocumentImage> images) {
                throw new IllegalStateException("Request failed");
            }

            @Override
            public ExtractionResult recheck(DocumentArtifact input) {
                rechecks[0]++;
                return new ExtractionResult(BigDecimal.TEN, List.of(fact()), "recheck-model", 0);
            }
        };

        StructuredTableResult result = extract(artifactExtractor, model);

        assertThat(rechecks[0]).isEqualTo(1);
        assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.AUTO_APPROVED);
        assertThat(result.candidates()).hasSize(1);
    }

    @Test
    void incompleteModelRecallUsesCompleteRecheckResult() throws Exception {
        SidecarDocumentArtifactExtractor artifactExtractor = mock(SidecarDocumentArtifactExtractor.class);
        DocumentArtifact source = originalHundredPointArtifact(false);
        when(artifactExtractor.extract(any(byte[].class), eq("bid.pdf"))).thenReturn(source);
        int[] rechecks = {0};
        MultimodalScoreFactExtractor model = new MultimodalScoreFactExtractor() {
            @Override
            public ExtractionResult extract(DocumentArtifact input, List<com.xiyu.bid.scoreparse.domain.DocumentImage> images) {
                assertThat(input).isSameAs(source);
                return new ExtractionResult(new BigDecimal("100"), List.of(sourceFact("1", "10")), "first", 0);
            }

            @Override
            public ExtractionResult recheck(DocumentArtifact input) {
                assertThat(input).isSameAs(source);
                rechecks[0]++;
                return new ExtractionResult(new BigDecimal("100"),
                        List.of(sourceFact("1", "10"), sourceFact("2", "90")), "recheck", 0);
            }
        };

        StructuredTableResult result = MultimodalStructuredExtractionHandler.extract(
                new TenderTextSource("bid.pdf", "obs://bid.pdf", "本项目评分总分：100分"),
                new LoadedBidFile.InMemory(new byte[]{1}, "bid.pdf", "application/pdf"),
                artifactExtractor, model, null);

        assertThat(rechecks[0]).isEqualTo(1);
        assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.AUTO_APPROVED);
        assertThat(result.candidates()).hasSize(2);
        assertThat(result.candidates()).extracting(candidate -> candidate.weight())
                .containsExactly(new BigDecimal("10"), new BigDecimal("90"));
        assertThat(result.candidates()).extracting(candidate -> candidate.dim())
                .containsExactly("销售额", "技术方案");
    }

    @Test
    void shrinkingRecheckTotalCannotPublishMissingNinetyPointItem() throws Exception {
        SidecarDocumentArtifactExtractor artifactExtractor = mock(SidecarDocumentArtifactExtractor.class);
        DocumentArtifact source = originalHundredPointArtifact(false);
        when(artifactExtractor.extract(any(byte[].class), eq("bid.pdf"))).thenReturn(source);
        int[] calls = {0};
        MultimodalScoreFactExtractor model = new MultimodalScoreFactExtractor() {
            @Override
            public ExtractionResult extract(DocumentArtifact input, List<com.xiyu.bid.scoreparse.domain.DocumentImage> images) {
                assertThat(input).isSameAs(source);
                return new ExtractionResult(new BigDecimal("100"), List.of(sourceFact("1", "10")), "first", 0);
            }

            @Override
            public ExtractionResult recheck(DocumentArtifact input) {
                assertThat(input).isSameAs(source);
                calls[0]++;
                return new ExtractionResult(new BigDecimal("10"), List.of(sourceFact("1", "10")), "recheck", 0);
            }
        };

        StructuredTableResult result = MultimodalStructuredExtractionHandler.extract(
                new TenderTextSource("bid.pdf", "obs://bid.pdf", "本项目评分总分：100分"),
                new LoadedBidFile.InMemory(new byte[]{1}, "bid.pdf", "application/pdf"),
                artifactExtractor, model, null);

        assertThat(calls[0]).isEqualTo(1);
        assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
        assertThat(result.candidates()).isEmpty();
        assertThat(result.facts()).hasSize(1);
        assertThat(result.facts().get(0).weightEvidence().explanation()).contains("MODEL_TOTAL_SOURCE_MISMATCH");
    }

    @Test
    void wrongItemWeightAfterRecheckCannotPublishEvenWhenTotalMatches() throws Exception {
        var source = originalHundredPointArtifact(false);
        var model = new MultimodalScoreFactExtractor() {
            public ExtractionResult extract(DocumentArtifact input, List<com.xiyu.bid.scoreparse.domain.DocumentImage> images) {
                return new ExtractionResult(new BigDecimal("100"), List.of(sourceFact("1", "10")), "first", 0);
            }
            public ExtractionResult recheck(DocumentArtifact input) {
                return new ExtractionResult(new BigDecimal("100"), List.of(sourceFact("1", "100")), "recheck", 0);
            }
        };
        var result = extractSource(source, "本项目评分总分100分", model);
        assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
        assertThat(result.candidates()).isEmpty();
        assertThat(result.facts().get(0).weightEvidence().explanation()).contains("ITEM_WEIGHT_SOURCE_MISMATCH");
    }

    @Test
    void balancedWrongItemWeightsStillCannotPublish() throws Exception {
        var source = originalHundredPointArtifact(false);
        var model = new MultimodalScoreFactExtractor() {
            public ExtractionResult extract(DocumentArtifact input, List<com.xiyu.bid.scoreparse.domain.DocumentImage> images) {
                return new ExtractionResult(new BigDecimal("100"), List.of(sourceFact("1", "10")), "first", 0);
            }
            public ExtractionResult recheck(DocumentArtifact input) {
                return new ExtractionResult(new BigDecimal("100"),
                        List.of(sourceFact("1", "20"), sourceFact("2", "80")), "recheck", 0);
            }
        };
        var result = extractSource(source, "本项目评分总分100分", model);
        assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
        assertThat(result.candidates()).isEmpty();
        assertThat(result.facts()).allSatisfy(f -> assertThat(f.weightEvidence().explanation())
                .contains("ITEM_WEIGHT_SOURCE_MISMATCH"));
    }

    @Test
    void incompleteFirstAndFailedRecheckStayReviewRequired() throws Exception {
        var source = originalHundredPointArtifact(false);
        int[] calls = {0};
        var model = new MultimodalScoreFactExtractor() {
            public ExtractionResult extract(DocumentArtifact input, List<com.xiyu.bid.scoreparse.domain.DocumentImage> images) {
                return new ExtractionResult(new BigDecimal("100"), List.of(sourceFact("1", "10")), "first", 0);
            }
            public ExtractionResult recheck(DocumentArtifact input) {
                calls[0]++;
                throw new IllegalStateException("model unavailable");
            }
        };
        var result = extractSource(source, "本项目评分总分100分", model);
        assertThat(calls[0]).isEqualTo(1);
        assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
        assertThat(result.candidates()).isEmpty();
    }

    @Test
    void missingOriginalTotalNeverFallsBackToDefaultHundred() throws Exception {
        var full = loadCompleteArtifact();
        var table = full.tables().get(0);
        var noSummary = new DocumentArtifact(full.artifact(),
                List.of(new DocumentArtifact.CanonicalTable(table.tableId(), table.sourcePage(),
                        table.sourceSection(), table.rows() - 1, table.columns(), table.mergedRegions(),
                        table.cells().stream().filter(c -> c.row() < 4).toList(), true)), full.warnings());
        assertThat(ScoreFactShadowPipeline.run(noSummary, "").completeness().decision())
                .isEqualTo(ScoreParseDecision.AUTO_APPROVED);
        var model = new MultimodalScoreFactExtractor() {
            public ExtractionResult extract(DocumentArtifact input, List<com.xiyu.bid.scoreparse.domain.DocumentImage> images) {
                throw new IllegalStateException("first failed");
            }
            public ExtractionResult recheck(DocumentArtifact input) { return new ExtractionResult(
                    new BigDecimal("100"), List.of(), "recheck", 0); }
        };
        var result = extractSource(noSummary, "", model);
        assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
        assertThat(result.candidates()).isEmpty();
    }

    @Test
    void eightyPointOriginalWithThirtyAndFiftyAutoPublishesWithoutRecheck() throws Exception {
        var cells = List.of(
                sourceCell("e1-name", 0, 0, "商务服务"), sourceCell("e1-rule", 0, 1, "售后保障最高得30分"),
                sourceCell("e1-score", 0, 2, "30分"), sourceCell("e2-name", 1, 0, "技术方案"),
                sourceCell("e2-rule", 1, 1, "技术方案最高得50分"), sourceCell("e2-score", 1, 2, "50分"));
        var source = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("bid.pdf", "sha", "pdf", "2.0"),
                List.of(new DocumentArtifact.CanonicalTable("score-t1", 3, "本项目评分办法", 2, 3,
                        List.of(), cells, false)), List.of());
        int[] rechecks = {0};
        var model = new MultimodalScoreFactExtractor() {
            public ExtractionResult extract(DocumentArtifact input, List<com.xiyu.bid.scoreparse.domain.DocumentImage> images) {
                return new ExtractionResult(new BigDecimal("80"), List.of(
                        new MultimodalScoreFact("本项目评分办法", "1", "", "商务服务", new BigDecimal("30"),
                                "售后保障最高得30分", "OBJECTIVE", 3, "score-t1", List.of("e1-rule"),
                                "售后保障最高得30分", 0.9),
                        new MultimodalScoreFact("本项目评分办法", "2", "", "技术方案", new BigDecimal("50"),
                                "技术方案最高得50分", "SUBJECTIVE", 3, "score-t1", List.of("e2-rule"),
                                "技术方案最高得50分", 0.9)), "first", 0);
            }
            public ExtractionResult recheck(DocumentArtifact input) {
                rechecks[0]++;
                throw new AssertionError("valid first extraction must not recheck");
            }
        };
        var result = extractSource(source, "本项目评分总分80分", model);
        assertThat(rechecks[0]).isZero();
        assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.AUTO_APPROVED);
        assertThat(result.originalVerified()).isTrue();
        assertThat(result.candidates()).extracting(candidate -> candidate.weight())
                .containsExactly(new BigDecimal("30"), new BigDecimal("50"));
    }

    @Test
    void deterministicFallbackWithConflictingItemWeightCannotPublish() throws Exception {
        var full = loadCompleteArtifact();
        var table = full.tables().get(0);
        var cells = table.cells().stream().map(c -> {
            String replacement = switch (c.cellId()) {
                case "t-sanitized-r1-c2" -> "整体方案最高得90分";
                case "t-sanitized-r1-c3" -> "100";
                case "t-sanitized-r4-c3" -> "110";
                default -> c.rawText();
            };
            return new DocumentArtifact.CanonicalTable.CanonicalCellDto(c.cellId(), c.row(), c.column(),
                    replacement, replacement, c.rowspan(), c.colspan(), c.isMergedContinue(), c.sourceXmlPath());
        }).toList();
        var source = new DocumentArtifact(full.artifact(), List.of(new DocumentArtifact.CanonicalTable(
                table.tableId(), table.sourcePage(), table.sourceSection(), table.rows(), table.columns(),
                table.mergedRegions(), cells, true)), full.warnings());
        assertThat(ScoreFactShadowPipeline.run(source, "评分总分110分").completeness().decision())
                .isEqualTo(ScoreParseDecision.AUTO_APPROVED);
        var model = new MultimodalScoreFactExtractor() {
            public ExtractionResult extract(DocumentArtifact input, List<com.xiyu.bid.scoreparse.domain.DocumentImage> images) {
                throw new IllegalStateException("first failed");
            }
            public ExtractionResult recheck(DocumentArtifact input) {
                return new ExtractionResult(new BigDecimal("110"), List.of(), "recheck", 0);
            }
        };
        var result = extractSource(source, "评分总分110分", model);
        assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
        assertThat(result.candidates()).isEmpty();
    }

    private static StructuredTableResult extractSource(DocumentArtifact source, String tenderText,
                                                       MultimodalScoreFactExtractor model) throws Exception {
        SidecarDocumentArtifactExtractor artifactExtractor = mock(SidecarDocumentArtifactExtractor.class);
        when(artifactExtractor.extract(any(byte[].class), eq("bid.pdf"))).thenReturn(source);
        return MultimodalStructuredExtractionHandler.extract(
                new TenderTextSource("bid.pdf", "obs://bid.pdf", tenderText),
                new LoadedBidFile.InMemory(new byte[]{1}, "bid.pdf", "application/pdf"),
                artifactExtractor, model, null);
    }

    private static MultimodalScoreFact sourceFact(String code, String weight) {
        String rule = "1".equals(code) ? "销售额达到1亿元得10分" : "技术方案按完整性与可行性综合评议，最高得90分";
        String name = "1".equals(code) ? "销售额" : "技术方案";
        return new MultimodalScoreFact("本项目评分办法", code, "", name, new BigDecimal(weight), rule,
                "OBJECTIVE", 3, "score-t1", List.of("c" + code + "-rule"), rule, 0.9);
    }

    private static DocumentArtifact originalHundredPointArtifact(boolean fallbackAvailable) {
        var cells = List.of(
                sourceCell("total", 0, 0, "本项目评分总分：100分"),
                sourceCell("c1-name", 1, 0, "销售额"),
                sourceCell("c1-rule", 1, 1, "销售额达到1亿元得10分"),
                sourceCell("c1-weight", 1, 2, "10分"),
                sourceCell("c2-name", 2, 0, "技术方案"),
                sourceCell("c2-rule", 2, 1, "技术方案按完整性与可行性综合评议，最高得90分"),
                sourceCell("c2-weight", 2, 2, "90分"));
        return new DocumentArtifact(new DocumentArtifact.ArtifactMeta("bid.pdf", "sha", "pdf", "2.0"),
                List.of(new DocumentArtifact.CanonicalTable("score-t1", 3, "本项目评分办法", 3, 3,
                        List.of(), cells, fallbackAvailable)), List.of());
    }

    private static DocumentArtifact.CanonicalTable.CanonicalCellDto sourceCell(
            String id, int row, int column, String text) {
        return new DocumentArtifact.CanonicalTable.CanonicalCellDto(id, row, column,
                text, text, 1, 1, false, "");
    }

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

    @Test
    void incompleteModelRecallFallsBackToCompleteDeterministicTableFacts() throws Exception {
        SidecarDocumentArtifactExtractor artifactExtractor = mock(SidecarDocumentArtifactExtractor.class);
        DocumentArtifact artifact = loadCompleteArtifact();
        when(artifactExtractor.extract(any(byte[].class), eq("bid.pdf"))).thenReturn(artifact);
        MultimodalScoreFactExtractor model = (input, images) -> new MultimodalScoreFactExtractor.ExtractionResult(
                new BigDecimal("100"), List.of(new MultimodalScoreFact(
                        "方案能力", "A1", "", "方案能力", new BigDecimal("90"),
                        "整体方案完整可行", "SUBJECTIVE", null, "t-sanitized-merged-origin",
                        List.of("t-sanitized-r1-c2"), "整体方案完整可行", 0.99)),
                "test-model", images.size());

        StructuredTableResult result = MultimodalStructuredExtractionHandler.extract(
                new TenderTextSource("bid.pdf", "obs://bid.pdf", "评分总分100分"),
                new LoadedBidFile.InMemory(new byte[]{1}, "bid.pdf", "application/pdf"),
                artifactExtractor, model, null);

        assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.AUTO_APPROVED);
        assertThat(result.candidates()).extracting(candidate -> candidate.weight())
                .containsExactly(new BigDecimal("90"), new BigDecimal("5"), new BigDecimal("5"));
    }

    @Test
    void multiTableModelTotalCannotBeUsedAsDocumentWideDefault() throws Exception {
        DocumentArtifact source = new DocumentArtifact(
                new DocumentArtifact.ArtifactMeta("bid.pdf", "sha", "pdf", "2.0"),
                List.of(
                        new DocumentArtifact.CanonicalTable("t1", 1, "商务", 1, 2, List.of(),
                                List.of(sourceCell("t1-rule", 0, 1, "商务服务最高得50分")), true),
                        new DocumentArtifact.CanonicalTable("t2", 2, "技术", 1, 2, List.of(),
                                List.of(sourceCell("t2-rule", 0, 1, "技术方案最高得50分")), true)),
                List.of());
        SidecarDocumentArtifactExtractor artifactExtractor = mock(SidecarDocumentArtifactExtractor.class);
        when(artifactExtractor.extract(any(byte[].class), eq("bid.pdf"))).thenReturn(source);
        MultimodalScoreFactExtractor model = (input, images) -> new MultimodalScoreFactExtractor.ExtractionResult(
                new BigDecimal("100"), List.of(
                        new MultimodalScoreFact("商务", "1", "", "商务服务", new BigDecimal("50"),
                                "商务服务最高得50分", "OBJECTIVE", 1, "t1", List.of("t1-rule"),
                                "商务服务最高得50分", 0.9),
                        new MultimodalScoreFact("技术", "1", "", "技术方案", new BigDecimal("50"),
                                "技术方案最高得50分", "SUBJECTIVE", 2, "t2", List.of("t2-rule"),
                                "技术方案最高得50分", 0.9)), "model", 0);

        StructuredTableResult result = MultimodalStructuredExtractionHandler.extract(
                new TenderTextSource("bid.pdf", "obs://bid.pdf", "评分办法"),
                new LoadedBidFile.InMemory(new byte[]{1}, "bid.pdf", "application/pdf"),
                artifactExtractor, model, null);

        assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
        assertThat(result.completeness().declaredTotalScore()).isNull();
        assertThat(result.completeness().declaredTotalSource())
                .isEqualTo(com.xiyu.bid.scoreparse.domain.ScoreCompletenessPolicy.DeclaredTotalSource.UNKNOWN);
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
        var total = new DocumentArtifact.CanonicalTable.CanonicalCellDto(
                "total", 0, 0, "本项目评分总分：10分", "本项目评分总分：10分", 1, 1, false, "");
        var cell = new DocumentArtifact.CanonicalTable.CanonicalCellDto(
                "c1", 1, 1, "销售额达到1亿元得10分", "销售额达到1亿元得10分", 1, 1, false, "");
        var table = new DocumentArtifact.CanonicalTable(
                "t1", 1, "商务", 2, 2, List.of(), List.of(total, cell), true);
        return new DocumentArtifact(new DocumentArtifact.ArtifactMeta("bid.pdf", "sha", "pdf", "2.0"),
                List.of(table), List.of());
    }

    private static DocumentArtifact loadCompleteArtifact() throws Exception {
        try (InputStream input = MultimodalStructuredExtractionHandlerTest.class.getResourceAsStream(
                "/scoreparse-golden/merged-parent-first-leaf-table-v2.json")) {
            return new ObjectMapper().readValue(input, DocumentArtifact.class);
        }
    }
}
