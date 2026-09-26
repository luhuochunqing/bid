// Input: TOC 目录行文本（点线尾页 / 右侧页码两种形状）与空 code 兄弟事实
// Output: 目录行永不成为方案标题；空 code 事实身份唯一且资格互相独立
// Pos: scoreparse/domain — 项目 1382 PDF 方案资格门修复（F2/F4/D5）的回归守卫
package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void levelTwoTocLinesNeverBecomeHeadings() {
        var headings = OriginalScoringTextScopePolicy.headings(new String[]{
                "三、评分标准..............................12",
                "(1) 商务部分：15 分..............................15",
                "三、评分标准",
                "(1) 商务部分：15 分"});

        assertEquals(List.of("三、评分标准", "(1) 商务部分：15 分"),
                headings.stream().map(OriginalScoringTextScopePolicy.Heading::title).toList());
        assertTrue(OriginalScoringSchemePolicy.sameScheme(
                "三、评分标准..............................12", "三、评分标准"));
        assertTrue(OriginalScoringSchemePolicy.sameScheme(
                "(1) 商务部分：15 分………15", "(1) 商务部分：15 分"));
    }

    @Test
    void dotLeaderWithoutPageNumberIsTreatedAsTocNotAsHeading() {
        // 有意的偏向：点线结尾的行一律不当标题（宁可漏一个章锚点转人工，也不让目录行成为方案根）。
        // 依据：真实招标文件目录行必带页码；反向误判（目录行成为方案根）会造成文档级方案歧义。
        // 断言具体结果而非「不含点线」：后者在页码要求收紧（\d{0,4} → \d{1,4}）时仍会通过。
        assertTrue(OriginalScoringTextScopePolicy.headings(new String[]{"第三章 评审办法………"}).isEmpty());
    }

    @Test
    void tocLineWithoutDotLeadersNeverBecomesAHeading() {
        var headings = OriginalScoringTextScopePolicy.headings(new String[]{
                "第三章 评审办法 27",
                "第三章 评审办法",
                "第三章 2026 年度评审办法"});

        assertEquals(List.of("第三章 评审办法", "第三章 2026 年度评审办法"),
                headings.stream().map(OriginalScoringTextScopePolicy.Heading::title).toList());
    }

    @Test
    void wideDotLeaderCharactersAreTreatedAsTocLines() {
        var headings = OriginalScoringTextScopePolicy.headings(new String[]{
                "第三章 评审办法⋮⋮⋮27",
                "第三章 评审办法․․․27",
                "第三章 评审办法"});

        assertEquals(List.of("第三章 评审办法"),
                headings.stream().map(OriginalScoringTextScopePolicy.Heading::title).toList());
        assertTrue(OriginalScoringSchemePolicy.sameScheme("第三章 评审办法⋮⋮⋮27", "第三章 评审办法"));
    }

    @Test
    void trailingNumberOnABodyHeadingIsNeverStrippedForComparison() {
        // 剥离只对点线目录行生效：否则「第三章 评审办法 1/2」这类只差尾号的标题会被并成同一方案，
        // 把另一章的评分项并进本方案 — 误判方向，比漏检危险。
        assertFalse(OriginalScoringSchemePolicy.sameScheme("第三章 评审办法 27", "第三章 评审办法"));
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

    @Test
    void blankCodeSiblingsWithDifferentParentsKeepTheirOwnParentLink() {
        // 两行空 code 兄弟同章节、分属不同父项。父图若按「方案+状态+章节+code」建键，后写入的那条
        // 会覆盖前一条：被覆盖的父项失去子项、自己反被当成叶子计入小计 → TABLE_SUBTOTAL_MISMATCH
        // （或被反向放过）。父图必须与其余身份体系一样按 modelFactId 建。
        String text = String.join("\n", "第三章 评审办法", "分值构成（总分 8 分）");
        var facts = List.of(
                parentFact("1.1", "商务部分", "3"),
                childFact("1.1", "CHILD-A", "供应商业绩", "3"),
                parentFact("1.2", "技术部分", "5"),
                childFact("1.2", "CHILD-B", "财务状况", "5"));
        var artifact = subtotalArtifact();
        var context = OriginalScoringContext.resolve(artifact, text);

        var verification = OriginalScoreEvidencePolicy.verifyModel(context, artifact, text, facts,
                context.total().value());

        assertEquals(4, verification.qualifications().size(), () -> verification.qualifications().toString());
        assertTrue(verification.qualifications().stream().allMatch(
                OriginalScoreEvidencePolicy.Qualification::candidateEligible),
                () -> verification.qualifications().toString());
        assertTrue(verification.problems().stream().noneMatch(problem ->
                        "TABLE_SUBTOTAL_MISMATCH".equals(problem.code())),
                () -> verification.problems().toString());
    }

    private static MultimodalScoreFact parentFact(String code, String dimension, String weight) {
        String rule = dimension + "（" + weight + " 分）：最高得 " + weight + " 分。";
        return new MultimodalScoreFact("商务", code, "", dimension, new BigDecimal(weight), rule,
                "OBJECTIVE", 34, "t1",
                List.of(code + "-name", code + "-rule", code + "-weight"),
                rule, 0.9, ChildAggregation.SUM, "", "");
    }

    private static MultimodalScoreFact childFact(String parentCode, String cellKey, String dimension, String weight) {
        String rule = dimension + "（" + weight + " 分）：最高得 " + weight + " 分。";
        return new MultimodalScoreFact("商务", "", parentCode, dimension, new BigDecimal(weight), rule,
                "OBJECTIVE", 34, "t1",
                List.of(cellKey + "-name", cellKey + "-rule", cellKey + "-weight"),
                rule, 0.9, ChildAggregation.SUM, "", "");
    }

    private static DocumentArtifact subtotalArtifact() {
        List<DocumentArtifact.CanonicalTable.CanonicalCellDto> cells = new java.util.ArrayList<>(List.of(
                cell("h-no", 0, 0, "序号"), cell("h-name", 0, 1, "评审内容"),
                cell("h-rule", 0, 2, "评审标准"), cell("h-weight", 0, 3, "评分分值")));
        record(cells, 1, "1.1", "商务部分", "3");
        record(cells, 2, "CHILD-A", "供应商业绩", "3");
        record(cells, 3, "1.2", "技术部分", "5");
        record(cells, 4, "CHILD-B", "财务状况", "5");
        cells.add(cell("total-no", 5, 0, "合计"));
        cells.add(cell("total-weight", 5, 3, "8 分"));
        var table = new DocumentArtifact.CanonicalTable("t1", 34, "第三章 评审办法", 6, 4, List.of(), cells, true);
        return new DocumentArtifact(new DocumentArtifact.ArtifactMeta("subtotal-identity", "sha-subtotal", "pdf", "2.2"),
                List.of(table), List.of());
    }

    private static void record(List<DocumentArtifact.CanonicalTable.CanonicalCellDto> cells, int row, String cellKey,
                               String dimension, String weight) {
        String rule = dimension + "（" + weight + " 分）：最高得 " + weight + " 分。";
        cells.add(cell(cellKey + "-no", row, 0, Integer.toString(row)));
        cells.add(cell(cellKey + "-name", row, 1, dimension));
        cells.add(cell(cellKey + "-rule", row, 2, rule));
        cells.add(cell(cellKey + "-weight", row, 3, weight + " 分"));
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
