package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable.CanonicalCellDto;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GroupedCriteriaScoreTableTest {
    @Test void splitsSourceNumberedRulesAndRetainsCrossPageContinuation() {
        var extracted = GroupedCriteriaScoreTable.extract(table("25")).orElseThrow();
        assertThat(extracted.total()).isEqualByComparingTo("100");
        assertThat(extracted.facts()).extracting(ScoreFact::dimension)
                .containsExactly("报价", "价格优势", "供货业绩", "服务能力");
        assertThat(extracted.facts()).extracting(ScoreFact::weight).containsExactly(
                new BigDecimal("35"), new BigDecimal("12"), new BigDecimal("28"), new BigDecimal("25"));
        assertThat(extracted.facts().get(1).sourceText()).contains("须附清单");
        assertThat(extracted.facts().stream().map(fact -> fact.toCandidate().orElseThrow().scoreTypeGuess()))
                .containsExactly("OBJECTIVE", "SUBJECTIVE", "OBJECTIVE", "SUBJECTIVE");
        assertThat(extracted.facts()).allSatisfy(fact -> assertThat(fact.weightEvidence().isLocatable()).isTrue());
        assertThat(extracted.locator()).contains("row1-col0", "row4-col0", "row5-col0");
    }

    @Test void rejectsGroupThatDoesNotCloseAgainstOriginalMaximum() {
        assertThat(GroupedCriteriaScoreTable.extract(table("26"))).isEmpty();
    }

    private static CanonicalTable table(String serviceMaximum) {
        return fromRows(new String[][]{
                {"评分内容", "", "属性", "评分细则"},
                {"价格分（47分）", "投标价格（满分47分）", "客观分", "1.报价（满分35分）\n按公式计算。"},
                {"", "", "主观分", "2.价格优势（满分12分）\n根据清单打分。"},
                {"", "", "", "须附清单。"},
                {"商务分（28分）", "供货业绩（满分28分）", "客观分", "满足全部条件得28分。"},
                {"技术分（25分）", "服务能力（满分" + serviceMaximum + "分）",
                        "主观分", "按服务方案评分。"},
                {"总分＝价格分＋商务分＋技术分", "", "", ""}
        });
    }

    private static CanonicalTable fromRows(String[][] rows) {
        var cells = new ArrayList<CanonicalCellDto>();
        for (int row = 0; row < rows.length; row++) {
            for (int col = 0; col < rows[row].length; col++) {
                String id = "row" + row + "-col" + col;
                String text = rows[row][col];
                cells.add(new CanonicalCellDto(id, row, col, text, text, 1, 1,
                        false, "", row < 3 ? 4 : 5, "row" + row));
            }
        }
        return new CanonicalTable("criteria", 4, "第四章 评标方法及评分标准", rows.length, 4,
                List.of(), cells, true, "PDF");
    }
}
