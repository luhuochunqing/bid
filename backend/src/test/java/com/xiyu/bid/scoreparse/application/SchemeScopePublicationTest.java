package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.ScoreParseDecision;
import com.xiyu.bid.scoreparse.domain.MultimodalScoreFact;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import com.xiyu.bid.scoreparse.entity.ScoreParseTask;
import com.xiyu.bid.scoreparse.repository.ScoreParseTaskRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SchemeScopePublicationTest {
    @Test void bothModelCallsFailButConfirmedOriginalTotalSurvivesReview() throws Exception {
        var source = summaryAndTwoDetails();
        var artifactExtractor = mock(SidecarDocumentArtifactExtractor.class);
        when(artifactExtractor.extract(any(byte[].class), eq("bid.pdf"))).thenReturn(source);
        MultimodalScoreFactExtractor model = new TextAwareScoreFactExtractorTestAdapter() {
            public ExtractionResult extract(DocumentArtifact ignored,
                    List<com.xiyu.bid.scoreparse.domain.DocumentImage> images) {
                throw new IllegalStateException("first request unavailable");
            }
            public ExtractionResult recheck(DocumentArtifact ignored) {
                throw new IllegalStateException("recheck unavailable");
            }
        };
        var result = MultimodalStructuredExtractionHandler.extract(
                new TenderTextSource("bid.pdf", "obs://bid.pdf", summaryText()),
                new LoadedBidFile.InMemory(new byte[]{1}, "bid.pdf", "application/pdf"),
                artifactExtractor, model, null);
        assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
        assertThat(result.completeness().declaredTotalScore()).isEqualByComparingTo("100");
        assertThat(result.candidates()).isEmpty();
        verify(artifactExtractor, times(1)).extract(any(byte[].class), eq("bid.pdf"));
    }
    private static final String TEXT = "## 本项目评分办法\n总分100分。\n"
            + "商务评分20分，技术评分30分，报价评分50分。";

    @Test void missingUnflaggedPriceTableCannotPublishTwoLocallyCompleteTables() throws Exception {
        DocumentArtifact source = threeDetailTables(false);
        var shadow = ScoreFactShadowPipeline.run(source, TEXT);
        assertThat(shadow.tables()).hasSize(2);
        assertThat(shadow.completeness().decision()).isEqualTo(ScoreParseDecision.AUTO_APPROVED);
        var result = extract(source, TEXT);
        assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
        assertThat(result.originalVerified()).isFalse();
        assertThat(result.completeness().declaredTotalScore()).isEqualByComparingTo("100");
        assertThat(result.completeness().leafWeightSum()).isEqualByComparingTo("50");
        assertThat(result.completeness().defects()).anyMatch(defect -> defect.contains("100") && defect.contains("50"));
        assertNotPublished(result);
    }

    @Test void missingTechnicalTableInModelCannotPublishBusinessOnly() throws Exception {
        var source = summaryAndTwoDetails();
        var result = modelExtract(source, List.of(modelFact("t-business", "1", "履约能力", "20")), "20");
        assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
        assertThat(result.completeness().declaredTotalScore()).isEqualByComparingTo("100");
        assertThat(result.candidates()).isEmpty();
        assertNotPublished(result);
    }

    @Test void emptyModelFactsKeepOriginalHundredAndCannotPublishPartialFallback() throws Exception {
        var result = modelExtract(summaryAndTwoDetails(), List.of(), "100");
        assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
        assertThat(result.completeness().declaredTotalScore()).isEqualByComparingTo("100");
        assertThat(result.candidates()).isEmpty();
    }

    @Test void completeDeterministicFallbackPublishesItsOwnFactsAfterIncompleteModel() throws Exception {
        var result = modelExtract(threeDetailTables(true), TEXT,
                List.of(modelFact("t-business", "1", "履约能力", "20")), "20");
        assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.AUTO_APPROVED);
        assertThat(result.completeness().declaredTotalScore()).isEqualByComparingTo("100");
        assertThat(result.originalVerified()).isTrue();
        assertThat(result.candidates()).extracting(candidate -> candidate.weight())
                .containsExactly(new BigDecimal("20"), new BigDecimal("30"), new BigDecimal("50"));
    }

    @Test void completeModelFactsWithIndependentSummaryPublishBothDetails() throws Exception {
        var source = summaryAndTwoDetails();
        var result = modelExtract(source, List.of(modelFact("t-business", "1", "履约能力", "20"),
                modelFact("t-technical", "2", "实施方案", "80")), "100");
        assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.AUTO_APPROVED);
        assertThat(result.candidates()).extracting(candidate -> candidate.weight())
                .containsExactly(new BigDecimal("20"), new BigDecimal("80"));
    }

    @Test void allThreeLocallyCompleteTablesCanPublishOneHundredPointScheme() throws Exception {
        var result = extract(threeDetailTables(true), TEXT);
        assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.AUTO_APPROVED);
        assertThat(result.completeness().declaredTotalScore()).isEqualByComparingTo("100");
        assertThat(result.originalVerified()).isTrue();
        assertThat(result.candidates()).extracting(candidate -> candidate.weight())
                .containsExactly(new BigDecimal("20"), new BigDecimal("30"), new BigDecimal("50"));
    }

    @Test void publicationProofCannotBeReusedForDifferentCandidateSet() throws Exception {
        var verified = extract(threeDetailTables(true), TEXT);
        assertThat(verified.originalVerified()).isTrue();
        var altered = StructuredTableResult.evidencePipeline(verified.facts(),
                verified.candidates().subList(0, 2), verified.completeness(),
                verified.tableCompletenesses(), verified.originalProofs());
        assertThat(altered.originalVerified()).isFalse();
        assertNotPublished(altered);
    }

    @Test void twoPhysicalTablesWithoutSubtotalsCloseAgainstBodySchemeTotal() throws Exception {
        DocumentArtifact source = new DocumentArtifact(meta(), List.of(
                detailTable("t-business", "本项目评分办法 / 商务", "履约能力", "20", false, true),
                detailTable("t-technical", "本项目评分办法 / 技术", "实施方案", "80", false, true)), List.of());
        var result = extract(source, "## 本项目评分办法\n总分100分。\n商务和技术评分明细如下。");
        assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
        assertThat(result.completeness().declaredTotalScore()).isNull();
        assertThat(result.originalVerified()).isFalse();
        assertThat(result.candidates()).isEmpty();
    }

    static void assertNotPublished(StructuredTableResult result) {
        var task = new ScoreParseTask();
        var tasks = mock(ScoreParseTaskRepository.class);
        var state = mock(ScoreParseTaskStateService.class);
        var progress = mock(ScoreParseProgressService.class);
        var items = mock(ScoreItemPersistenceService.class);
        var estimates = mock(EstimatedScoreService.class);
        StructuredTableExtractionHandler.markEvidencePipelineCompleted(
                task, "task-scheme", result, tasks, state, progress, items, estimates);
        verify(items, never()).persistItems(any(), any(), anyList());
        verify(estimates, never()).estimateForProject(any());
        verify(items).persistFactsAndEvidence(any(), any(), anyList());
    }

    static StructuredTableResult extract(DocumentArtifact source, String text) throws Exception {
        var extractor = mock(SidecarDocumentArtifactExtractor.class);
        when(extractor.extract(any(byte[].class), eq("bid.pdf"))).thenReturn(source);
        return StructuredTableExtractionHandler.extract(
                new TenderTextSource("bid.pdf", "obs://bid.pdf", text),
                new LoadedBidFile.InMemory(new byte[]{1}, "bid.pdf", "application/pdf"),
                extractor, (SidecarTableExtractor) null);
    }

    static StructuredTableResult modelExtract(DocumentArtifact source, List<MultimodalScoreFact> facts,
                                                       String declaredTotal) throws Exception {
        return modelExtract(source, summaryText(), facts, declaredTotal);
    }

    static StructuredTableResult modelExtract(DocumentArtifact source, String text,
                                                       List<MultimodalScoreFact> facts,
                                                       String declaredTotal) throws Exception {
        var extractor = mock(SidecarDocumentArtifactExtractor.class);
        when(extractor.extract(any(byte[].class), eq("bid.pdf"))).thenReturn(source);
        MultimodalScoreFactExtractor model = new TextAwareScoreFactExtractorTestAdapter() {
            public ExtractionResult extract(DocumentArtifact input,
                    List<com.xiyu.bid.scoreparse.domain.DocumentImage> images) {
                assertThat(input).isSameAs(source);
                return new ExtractionResult(new BigDecimal(declaredTotal), facts, "first", 0);
            }
            public ExtractionResult recheck(DocumentArtifact input) {
                assertThat(input).isSameAs(source);
                return new ExtractionResult(new BigDecimal(declaredTotal), facts, "recheck", 0);
            }
        };
        return MultimodalStructuredExtractionHandler.extract(
                new TenderTextSource("bid.pdf", "obs://bid.pdf", text),
                new LoadedBidFile.InMemory(new byte[]{1}, "bid.pdf", "application/pdf"),
                extractor, model, null);
    }

    static MultimodalScoreFact modelFact(String tableId, String code, String name, String score) {
        String rule = name + "按原文要求评议，最高得" + score + "分。";
        return new MultimodalScoreFact("本项目评分办法", code, "", name, new BigDecimal(score),
                rule, "OBJECTIVE", 1, tableId, List.of(tableId + "-r1-c2"), rule, 0.9);
    }

    private static String summaryText() {
        return "# 本项目评分办法\n评分方案汇总表\n| 汇总项 | 分值 |\n| 合计 | 满分100分 |\n"
                + "商务明细表\n| 序号 | 评分因素 | 评分规则 | 分值 |\n"
                + "| 1 | 履约能力 | 履约能力按原文要求评议，最高得20分。 | 20 |\n| 合计 | | | 20 |\n"
                + "技术明细表\n| 序号 | 评分因素 | 评分规则 | 分值 |\n"
                + "| 2 | 实施方案 | 实施方案按原文要求评议，最高得80分。 | 80 |\n| 合计 | | | 80 |";
    }

    static DocumentArtifact summaryAndTwoDetails() {
        List<DocumentArtifact.CanonicalTable.CanonicalCellDto> cells = List.of(
                new DocumentArtifact.CanonicalTable.CanonicalCellDto("s-h1", 0, 0, "汇总项", "汇总项", 1, 1, false, ""),
                new DocumentArtifact.CanonicalTable.CanonicalCellDto("s-h2", 0, 1, "分值", "分值", 1, 1, false, ""),
                new DocumentArtifact.CanonicalTable.CanonicalCellDto("s-total", 1, 0, "合计", "合计", 1, 1, false, ""),
                new DocumentArtifact.CanonicalTable.CanonicalCellDto("s-score", 1, 1, "满分100分", "满分100分", 1, 1, false, ""));
        return new DocumentArtifact(meta(), List.of(new DocumentArtifact.CanonicalTable("t-summary", 1,
                "本项目评分办法 / 评分方案汇总表", 2, 2, List.of(), cells, false),
                detailTable("t-business", "本项目评分办法 / 商务明细表", "履约能力", "20", true, true),
                detailTable("t-technical", "本项目评分办法 / 技术明细表", "实施方案", "80", true, false)), List.of());
    }

    static DocumentArtifact threeDetailTables(boolean priceFlagged) {
        return new DocumentArtifact(meta(), List.of(
                detailTable("t-business", "本项目评分办法 / 商务", "履约能力", "20", true, true),
                detailTable("t-technical", "本项目评分办法 / 技术", "实施方案", "30", true, true),
                detailTable("t-price", "本项目评分办法 / 报价", "报价评议", "50", true, priceFlagged)),
                List.of());
    }

    static DocumentArtifact itemMaximumDetails() {
        var first = detailTable("t-business", "本项目评分办法 / 评分明细", "销售额", "10", false, true);
        List<DocumentArtifact.CanonicalTable.CanonicalCellDto> cells = new ArrayList<>();
        first.cells().forEach(c -> cells.add("t-business-r1-c2".equals(c.cellId())
                ? new DocumentArtifact.CanonicalTable.CanonicalCellDto(c.cellId(), c.row(), c.column(),
                        "满分10分，销售额达到1亿元得10分。", "满分10分，销售额达到1亿元得10分。",
                        1, 1, false, "pdf:page=1;table=t-business") : c));
        for (int col = 0; col < 4; col++) {
            String value = List.of("2", "技术方案", "技术方案按完整性与可行性评议，最高得90分。", "90").get(col);
            cells.add(new DocumentArtifact.CanonicalTable.CanonicalCellDto("t-business-r2-c" + col,
                    2, col, value, value, 1, 1, false, "pdf:page=1;table=t-business"));
        }
        var table = new DocumentArtifact.CanonicalTable("t-business", 1,
                "本项目评分办法 / 评分明细", 3, 4, List.of(), cells, true);
        return new DocumentArtifact(meta(), List.of(table), List.of());
    }

    static MultimodalScoreFact itemMaximumFact() {
        String rule = "满分10分，销售额达到1亿元得10分。";
        return new MultimodalScoreFact("本项目评分办法", "1", "", "销售额", new BigDecimal("10"),
                rule, "OBJECTIVE", 1, "t-business", List.of("t-business-r1-c2"), rule, 0.9);
    }

    static MultimodalScoreFact itemMaximumTechnicalFact() {
        String rule = "技术方案按完整性与可行性评议，最高得90分。";
        return new MultimodalScoreFact("本项目评分办法", "2", "", "技术方案", new BigDecimal("90"),
                rule, "SUBJECTIVE", 1, "t-business", List.of("t-business-r2-c2"), rule, 0.9);
    }

    static DocumentArtifact categoryDetails(String businessSubtotal, String technicalSubtotal) {
        return categoryDetails(businessSubtotal, technicalSubtotal, "商务", "技术");
    }

    static DocumentArtifact categoryDetails(String businessSubtotal, String technicalSubtotal,
                                                    String businessSection, String technicalSection) {
        return new DocumentArtifact(meta(), List.of(
                withSubtotal(detailTable("t-business", "本项目评分办法 / " + businessSection,
                        "履约能力", "20", true, true), businessSubtotal),
                withSubtotal(detailTable("t-technical", "本项目评分办法 / " + technicalSection,
                        "实施方案", "80", true, true), technicalSubtotal)), List.of());
    }

    private static DocumentArtifact.CanonicalTable withSubtotal(DocumentArtifact.CanonicalTable table, String score) {
        var cells = table.cells().stream().map(c -> c.cellId().equals(table.tableId() + "-r2-c3")
                ? new DocumentArtifact.CanonicalTable.CanonicalCellDto(c.cellId(), c.row(), c.column(),
                        score, score, 1, 1, false, "pdf:page=1;table=" + table.tableId()) : c).toList();
        return new DocumentArtifact.CanonicalTable(table.tableId(), table.sourcePage(), table.sourceSection(),
                table.rows(), table.columns(), table.mergedRegions(), cells, table.isScoreTable());
    }

    static List<MultimodalScoreFact> categoryFacts() {
        return List.of(modelFact("t-business", "1", "履约能力", "20"),
                modelFact("t-technical", "2", "实施方案", "80"));
    }

    static String categoryText() {
        return "# 本项目评分办法\n总分100分。\n商务与技术评分明细见下表。";
    }

    private static DocumentArtifact.ArtifactMeta meta() {
        return new DocumentArtifact.ArtifactMeta("bid.pdf", "sha-scheme", "pdf", "2.0");
    }

    private static DocumentArtifact.CanonicalTable detailTable(String id, String section, String name,
                                                                 String score, boolean subtotal, boolean scoreTable) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("序号", "评分因素", "评分规则", "分值"));
        rows.add(List.of("1", name, name + "按原文要求评议，最高得" + score + "分。", score));
        if (subtotal) rows.add(List.of("合计", "", "", score));
        List<DocumentArtifact.CanonicalTable.CanonicalCellDto> cells = new ArrayList<>();
        for (int row = 0; row < rows.size(); row++) {
            for (int col = 0; col < 4; col++) {
                String value = rows.get(row).get(col);
                cells.add(new DocumentArtifact.CanonicalTable.CanonicalCellDto(id + "-r" + row + "-c" + col,
                        row, col, value, value, 1, 1, false, "pdf:page=1;table=" + id));
            }
        }
        return new DocumentArtifact.CanonicalTable(id, 1, section, rows.size(), 4, List.of(), cells, scoreTable);
    }
}
