// Input: TOC 点线目录行文本与空 code 兄弟事实
// Output: 目录行永不成为方案标题；空 code 事实身份唯一且资格互相独立
// Pos: scoreparse/domain — 项目 1382 PDF 方案资格门修复（F2/F4/D5）的回归守卫
package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OriginalScoreFactIdentityTest {

    @Test
    void tocDotLeaderLinesNeverBecomeHeadings() {
        var headings = OriginalScoringTextScopePolicy.headings(new String[]{
                "第三章 评审办法..............................27",
                "第三章 评审办法",
                "三、评分标准 3.2.3",
                "(1) 商务部分：15 分"});

        assertEquals(List.of("第三章 评审办法", "三、评分标准 3.2.3", "(1) 商务部分：15 分"),
                headings.stream().map(OriginalScoringTextScopePolicy.Heading::title).toList());
    }

    @Test
    void tocTrailerTitlesStillCompareAsSameScheme() {
        assertTrue(OriginalScoringSchemePolicy.sameScheme(
                "第三章 评审办法..............................27", "第三章 评审办法"));
        assertTrue(OriginalScoringSchemePolicy.sameScheme("第三章 评审办法………27", "第三章 评审办法"));
    }

    @Test
    void blankCodeSiblingFactsGetDistinctModelFactIds() {
        var first = tableFact("供应商业绩", "7", "A");
        var second = tableFact("财务状况", "3", "B");

        assertNotEquals(OriginalScoreEvidencePolicy.modelFactId(first),
                OriginalScoreEvidencePolicy.modelFactId(second));
    }

    @Test
    void blankCodeSiblingEligibilityIsIndependent() {
        String text = String.join("\n",
                "第三章 评审办法",
                "分值构成（总分 100 分）",
                "第四章 合同履约考核",
                "4.2.13 乙方须在 2 日内完成回复与响应，回复率须达到 100%。");
        var eligible = tableFact("供应商业绩", "7", "A");
        var audit = new MultimodalScoreFact("商务", "", "", "合同履约响应率", new BigDecimal("100"),
                "回复率须达到 100%", "OBJECTIVE", null, "", List.of("text:line[3]"),
                "回复率须达到 100%", 0.9, ChildAggregation.SUM, "", "");

        var verification = OriginalScoreEvidencePolicy.verifyModel(artifact(), text,
                List.of(eligible, audit), new BigDecimal("100"));
        var eligibleFacts = OriginalScoreEvidencePolicy.eligibleModelFacts(
                List.of(eligible, audit), verification);

        assertEquals(1, eligibleFacts.size(), () -> verification.qualifications().toString());
        assertEquals("供应商业绩", eligibleFacts.get(0).dimension());
        assertEquals("OUT_OF_SCHEME", verification.qualifications().stream()
                .filter(qualification -> !qualification.candidateEligible())
                .findFirst().orElseThrow().scopeStatus());
    }

    @Test
    void blankCodeSiblingsDoNotTriggerDuplicateCode() {
        String text = String.join("\n", "第三章 评审办法", "分值构成（总分 100 分）");

        var report = new MultimodalScoreFactValidator().validate(
                List.of(tableFact("供应商业绩", "7", "A"), tableFact("财务状况", "3", "B")),
                artifact(), text, new BigDecimal("100"));

        assertTrue(report.violations().stream().noneMatch(violation ->
                        "DUPLICATE_CODE".equals(violation.code())),
                () -> report.violations().toString());
    }

    private static MultimodalScoreFact tableFact(String dimension, String weight, String codePrefix) {
        String rule = dimension + "（" + weight + " 分）：最高得 " + weight + " 分。";
        return new MultimodalScoreFact("商务", "", "", dimension, new BigDecimal(weight), rule,
                "OBJECTIVE", 34, "t1",
                List.of(codePrefix + "-name", codePrefix + "-rule", codePrefix + "-weight"),
                rule, 0.9, ChildAggregation.SUM, "", "");
    }

    private static DocumentArtifact artifact() {
        List<DocumentArtifact.CanonicalTable.CanonicalCellDto> cells = List.of(
                cell("h-no", 0, 0, "序号"), cell("h-name", 0, 1, "评审内容"),
                cell("h-rule", 0, 2, "评审标准"), cell("h-weight", 0, 3, "评分分值"),
                cell("A-no", 1, 0, "1"), cell("A-name", 1, 1, "供应商业绩"),
                cell("A-rule", 1, 2, "供应商业绩（7 分）：最高得 7 分。"), cell("A-weight", 1, 3, "7 分"),
                cell("B-no", 2, 0, "2"), cell("B-name", 2, 1, "财务状况"),
                cell("B-rule", 2, 2, "财务状况（3 分）：最高得 3 分。"), cell("B-weight", 2, 3, "3 分"));
        var table = new DocumentArtifact.CanonicalTable("t1", 34, "第三章 评审办法", 3, 4,
                List.of(), cells, true);
        return new DocumentArtifact(new DocumentArtifact.ArtifactMeta("fact-identity", "sha-fact", "pdf", "2.2"),
                List.of(table), List.of());
    }

    private static DocumentArtifact.CanonicalTable.CanonicalCellDto cell(String id, int row, int column,
                                                                         String text) {
        return new DocumentArtifact.CanonicalTable.CanonicalCellDto(id, row, column, text, text,
                1, 1, false, "fixture:" + id);
    }
}
