package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PdfLogicalRowContinuityTest {
    @Test void continuedRuleRemainsWithItsOriginalScoringRow() {
        var cells = new ArrayList<DocumentArtifact.CanonicalTable.CanonicalCellDto>();
        String[][] rows = {{"条款号", "评审因素", "评审标准", "分值"},
                {"1", "履约能力", "第一段规则。", "5"},
                {"", "", "第二段续行规则。", ""},
                {"2", "服务能力", "独立项目规则。", "5"}};
        for (int r = 0; r < rows.length; r++) for (int c = 0; c < 4; c++) {
            String logicalRow = r == 2 ? "t-r1" : "t-r" + r;
            cells.add(new DocumentArtifact.CanonicalTable.CanonicalCellDto("t-r"+r+"-c"+c,
                    r, c, rows[r][c], rows[r][c], 1, 1, false, "pdf:page=14", 14, logicalRow));
        }
        var table = new DocumentArtifact.CanonicalTable("t", 13, "第四章 评审办法", 4, 4, List.of(), cells, true, "PDF");
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("doc", "hash", "PDF", "2.2"), List.of(table), List.of());
        var facts = ScoreFactShadowPipeline.run(artifact, "# 第四章 评审办法\n").tables().getFirst().facts();
        assertThat(facts).hasSize(2);
        assertThat(facts.getFirst().sourceText()).contains("第一段规则。", "第二段续行规则。").doesNotContain("独立项目规则。");
        assertThat(facts.getFirst().sourceCellIds()).contains("t-r2-c2");
    }
    @Test void continuationWithoutPhysicalLocationMustNotBePromoted() {
        var cells = new ArrayList<DocumentArtifact.CanonicalTable.CanonicalCellDto>();
        String[][] rows = {{"条款号", "评审因素", "评审标准", "分值"},
                {"1", "履约能力", "第一段规则。", "5"},
                {"", "", "未定位的续行。", ""},
                {"2", "服务能力", "独立项目规则。", "5"}};
        for (int r = 0; r < rows.length; r++) for (int c = 0; c < 4; c++) {
            String logicalRow = r == 2 ? "t-r1" : "t-r" + r;
            cells.add(new DocumentArtifact.CanonicalTable.CanonicalCellDto("t-r"+r+"-c"+c,
                    r, c, rows[r][c], rows[r][c], 1, 1, false, "", null, logicalRow));
        }
        var table = new DocumentArtifact.CanonicalTable("t", null, "第四章 评审办法", 4, 4, List.of(), cells, true, "PDF");
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("doc", "hash", "PDF", "2.2"), List.of(table), List.of());
        var facts = ScoreFactShadowPipeline.run(artifact, "# 第四章 评审办法\n").tables().getFirst().facts();
        assertThat(facts).hasSize(2);
        assertThat(facts.getFirst().sourceText()).doesNotContain("未定位的续行。");
        assertThat(facts.getFirst().sourceCellIds()).doesNotContain("t-r2-c2");
    }
}
