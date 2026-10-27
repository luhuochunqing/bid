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
        assertThat(bound.sourceCellIds()).containsExactly("t-r1-c1");
        assertThat(bound.quotedText()).isEqualTo("资产运营情况（5\n分）");
        assertThat(bound.dimension()).contains("资产运营情况");
        var validation = new com.xiyu.bid.scoreparse.domain.MultimodalScoreFactValidator()
                .validate(List.of(bound), artifact(false, 5), BigDecimal.valueOf(5));
        assertThat(validation.sourceVerification().qualifications()).singleElement()
                .satisfies(q -> assertThat(q.candidateEligible()).isTrue());
        assertThat(TextPrimaryScoreFactMapper.map(bound, artifact(false, 5), validation).sourceText())
                .contains("提供完整财务报告");
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

    private static MultimodalScoreFact fact(String code, String quote, int weight) {
        return new MultimodalScoreFact("第四章 评审办法", code, "", "商务", BigDecimal.valueOf(weight),
                "评估财务状况", "OBJECTIVE", 13, "", List.of(), quote, .9);
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
