package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.MultimodalScoreFact;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PdfOrdinalContaminationEvidenceTest {
    @Test void restoresUniqueOriginalRowWithContaminatedNameQuote() {
        var bound = TextPrimaryEvidenceBinder.bind(List.of(fact("5", "资产运营情况（5\n5 分）", 5)), artifact(false, 5)).getFirst();
        assertThat(bound.tableId()).isEqualTo("t");
        assertThat(bound.sourceCellIds()).containsExactly("t-r1-c1", "t-r1-c2");
        assertThat(bound.quotedText()).isEqualTo("资产运营情况（5\n分）");
        assertThat(bound.dimension()).contains("资产运营情况");
        var validation = new com.xiyu.bid.scoreparse.domain.MultimodalScoreFactValidator()
                .validate(List.of(bound), artifact(false, 5), BigDecimal.valueOf(5));
        assertThat(validation.sourceVerification().qualifications()).singleElement()
                .satisfies(q -> assertThat(q.candidateEligible()).isTrue());
        assertThat(TextPrimaryScoreFactMapper.map(bound, artifact(false, 5), validation).sourceText())
                .contains("提供完整财务报告");
    }

    @Test void restoresQuoteWithOrdinalInsideFoldedName() throws Exception {
        var bound = TextPrimaryEvidenceBinder.bind(List.of(fact("5", goldenQuote("modelQuotationFolded"), 5)), artifact(false, 5)).getFirst();
        assertThat(bound.tableId()).isEqualTo("t");
        assertThat(bound.sourceCellIds()).contains("t-r1-c1", "t-r1-c2");
        assertThat(bound.quotedText()).isEqualTo("资产运营情况（5\n分）");
        var validation = new com.xiyu.bid.scoreparse.domain.MultimodalScoreFactValidator()
                .validate(List.of(bound), artifact(false, 5), BigDecimal.valueOf(5));
        assertThat(validation.sourceVerification().qualifications()).singleElement()
                .satisfies(q -> assertThat(q.candidateEligible()).isTrue());
        assertThat(validation.sourceVerification().problems()).noneMatch(p -> p.code().equals("SOURCE_SCORE_ROW_UNCOVERED"));
        assertThat(TextPrimaryScoreFactMapper.map(bound, artifact(false, 5), validation).sourceText())
                .contains("提供完整财务报告");
    }

    @Test void exactNameWithCompleteStandaloneRuleCoversSourceRow() {
        var source = artifact(false, 5);
        var bound = TextPrimaryEvidenceBinder.bind(List.of(fact("5", "资产运营情况（5\n分）", 5)), source).getFirst();
        assertThat(bound.sourceCellIds()).containsExactly("t-r1-c1", "t-r1-c2");
        var validation = new com.xiyu.bid.scoreparse.domain.MultimodalScoreFactValidator()
                .validate(List.of(bound), source, BigDecimal.valueOf(5));
        assertThat(validation.sourceVerification().problems()).noneMatch(p -> p.code().equals("SOURCE_SCORE_ROW_UNCOVERED"));
        assertThat(TextPrimaryScoreFactMapper.map(bound, source, validation).sourceText()).contains("提供完整财务报告");
    }

    @Test void exactNameWithChildDoesNotClaimCompleteStandaloneRule() {
        var source = artifact(false, 5);
        var child = new MultimodalScoreFact("第四章 评审办法", "5.1", "5", "商务", BigDecimal.valueOf(5),
                "评估财务状况", "OBJECTIVE", 13, "", List.of(), "资产运营情况（5\n分）", .9);
        var bound = TextPrimaryEvidenceBinder.bind(List.of(fact("5", "资产运营情况（5\n分）", 5), child), source);
        assertThat(bound.getFirst().sourceCellIds()).doesNotContain("t-r1-c2");
    }

    @Test void sameSourceRowWithTwoPeerFactsMustNotClaimStandaloneCoverage() {
        var source = artifact(false, 5);
        var peer = fact("6", "资产运营情况（5\n分）", 5);
        var bound = TextPrimaryEvidenceBinder.bind(List.of(fact("5", "资产运营情况（5\n分）", 5), peer), source);
        assertThat(bound.getFirst().sourceCellIds()).doesNotContain("t-r1-c2");
        assertThat(bound.getLast().sourceCellIds()).doesNotContain("t-r1-c2");
    }

    @Test void knownCorrectTableIdDoesNotPreventUniqueOrdinalBinding() {
        var original = fact("5", "资产运5营情况（5分）", 5);
        var withTable = new MultimodalScoreFact(original.sectionTitle(), original.code(), original.parentCode(),
                original.dimension(), original.weight(), original.ruleText(), original.scoreType(),
                original.pageNumber(), "t", List.of(), original.quotedText(), original.confidence());
        var bound = TextPrimaryEvidenceBinder.bind(List.of(withTable), artifact(false, 5)).getFirst();
        assertThat(bound.sourceCellIds()).containsExactly("t-r1-c1", "t-r1-c2");
    }

    @Test void wrongTableIdMustNotBindAnotherTable() {
        var original = fact("5", "资产运5营情况（5分）", 5);
        var withTable = new MultimodalScoreFact(original.sectionTitle(), original.code(), original.parentCode(),
                original.dimension(), original.weight(), original.ruleText(), original.scoreType(),
                original.pageNumber(), "other", List.of(), original.quotedText(), original.confidence());
        assertThat(TextPrimaryEvidenceBinder.bind(List.of(withTable), artifact(false, 5)).getFirst().sourceCellIds()).isEmpty();
    }

    @Test void wrongOrdinalInsideNameMustRemainUnbound() throws Exception {
        var bound = TextPrimaryEvidenceBinder.bind(List.of(fact("5", goldenQuote("modelQuotationWrongOrdinal"), 5)), artifact(false, 5)).getFirst();
        assertThat(bound.sourceCellIds()).isEmpty();
    }

    @Test void alteredOriginalNameMustRemainUnbound() {
        var bound = TextPrimaryEvidenceBinder.bind(List.of(fact("5", "资产运5营情况（9分）", 5)), artifact(false, 5)).getFirst();
        assertThat(bound.sourceCellIds()).isEmpty();
    }

    @Test void restoresZeroPointDeductionWhenOrdinalIsInsideName() {
        var bound = TextPrimaryEvidenceBinder.bind(List.of(fact("10", "质量扣分与处\n10 理说明", 0)), zeroArtifact()).getFirst();
        assertThat(bound.tableId()).isEqualTo("t");
        assertThat(bound.sourceCellIds()).containsExactly("t-r1-c1", "t-r1-c2");
        assertThat(bound.quotedText()).isEqualTo("质量扣分与处理说明");
        var validation = new com.xiyu.bid.scoreparse.domain.MultimodalScoreFactValidator()
                .validate(List.of(bound), zeroArtifact(), BigDecimal.ZERO);
        assertThat(TextPrimaryScoreFactMapper.map(bound, zeroArtifact(), validation).sourceText())
                .contains("根据验收结果扣分");
    }

    @Test void ambiguousRowsMustRemainUnbound() {
        var bound = TextPrimaryEvidenceBinder.bind(List.of(fact("5", "资产运营情况（5\n5 分）", 5)), artifact(true, 5)).getFirst();
        assertThat(bound.sourceCellIds()).isEmpty();
    }

    @Test void incorrectWeightMustRemainUnbound() {
        var bound = TextPrimaryEvidenceBinder.bind(List.of(fact("5", "资产运营情况（5\n5 分）", 5)), artifact(false, 6)).getFirst();
        assertThat(bound.sourceCellIds()).isEmpty();
    }

    @Test void nonMatchingOrdinalMustRemainUnbound() {
        var bound = TextPrimaryEvidenceBinder.bind(List.of(fact("6", "资产运营情况（5\n5 分）", 5)), artifact(false, 5)).getFirst();
        assertThat(bound.sourceCellIds()).isEmpty();
    }

    @Test void restoresNameFromUniqueNameColumnForAnchoredGenericDimension() {
        var source = artifact(false, 5);
        var original = new MultimodalScoreFact("第四章 评审办法", "5", "", "详细评审", BigDecimal.valueOf(5),
                "评估财务状况", "OBJECTIVE", 14, "t", List.of("t-r1-c1", "t-r1-c2"),
                "资产运营情况（5\n分）", .9);
        var bound = TextPrimaryEvidenceBinder.bind(List.of(original), source).getFirst();
        assertThat(bound.dimension()).isEqualTo("资产运营情况");
        var validation = new com.xiyu.bid.scoreparse.domain.MultimodalScoreFactValidator()
                .validate(List.of(bound), source, BigDecimal.valueOf(5));
        assertThat(TextPrimaryScoreFactMapper.map(bound, source, validation).sourceText())
                .isEqualTo("提供完整财务报告得5分，无报告不得分。");
    }

    private static String goldenQuote(String field) throws Exception {
        try (var input = PdfOrdinalContaminationEvidenceTest.class.getResourceAsStream(
                "/scoreparse-golden/case-pdf-ordinal-contamination.expected.json")) {
            return new com.fasterxml.jackson.databind.ObjectMapper().readTree(input).path(field).asText();
        }
    }
    private static MultimodalScoreFact fact(String code, String quote, int weight) {
        return new MultimodalScoreFact("第四章 评审办法", code, "", "商务", BigDecimal.valueOf(weight),
                "评估财务状况", "OBJECTIVE", 13, "", List.of(), quote, .9);
    }
    private static DocumentArtifact zeroArtifact() {
        var cells = new ArrayList<DocumentArtifact.CanonicalTable.CanonicalCellDto>();
        String[][] rows = {{"条款号", "评审因素", "评审标准", "分值"},
                {"10", "质量扣分与处理说明", "根据验收结果扣分，提供完整记录。", "0"}};
        for (int r=0; r<rows.length; r++) for (int c=0; c<4; c++) cells.add(cell(r,c,rows[r][c]));
        var table = new DocumentArtifact.CanonicalTable("t", 13, "第四章 评审办法", 2, 4, List.of(), cells, true, "PDF");
        return new DocumentArtifact(new DocumentArtifact.ArtifactMeta("doc", "hash", "PDF", "2.2"), List.of(table), List.of());
    }
    private static DocumentArtifact artifact(boolean duplicate, int score) {
        var cells = new ArrayList<DocumentArtifact.CanonicalTable.CanonicalCellDto>();
        String[][] rows = {{"条款号", "评审因素", "评审标准", "分值"},
                {"5", "资产运营情况（5\n分）", "提供完整财务报告得5分，无报告不得分。", Integer.toString(score)}};
        for (int r=0; r<rows.length; r++) for (int c=0; c<4; c++) cells.add(cell(r,c,rows[r][c]));
        if (duplicate) for (int c=0; c<4; c++) cells.add(cell(2,c,rows[1][c]));
        var table = new DocumentArtifact.CanonicalTable("t", 13, "第四章 评审办法", duplicate ? 3 : 2, 4, List.of(), cells, true, "PDF");
        return new DocumentArtifact(new DocumentArtifact.ArtifactMeta("doc", "hash", "PDF", "2.2"), List.of(table), List.of());
    }
    private static DocumentArtifact.CanonicalTable.CanonicalCellDto cell(int row, int col, String text) {
        return new DocumentArtifact.CanonicalTable.CanonicalCellDto("t-r"+row+"-c"+col, row, col, text, text, 1, 1, false, "pdf:page=14", 14, "t-r"+row);
    }
}
