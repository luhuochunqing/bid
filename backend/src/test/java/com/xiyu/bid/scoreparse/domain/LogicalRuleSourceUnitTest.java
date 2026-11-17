package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.ArtifactMeta;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable.CanonicalCellDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LogicalRuleSourceUnitTest {

    @Test
    @DisplayName("4列表格中评审标准列跨行折行（序号和分值列为空），应识别为续行并合并进同一逻辑行")
    void shouldRecognizeMiddleColumnContinuationRowInTable() {
        // Table with 4 columns: 序号, 评审内容, 评审标准, 评分\n分值
        var r0c0 = new CanonicalCellDto("t1-r0-c0", 0, 0, "序号", "序号", 1, 1, false, "pdf:p24", 24, "t1-r0");
        var r0c1 = new CanonicalCellDto("t1-r0-c1", 0, 1, "评审内容", "评审内容", 1, 1, false, "pdf:p24", 24, "t1-r0");
        var r0c2 = new CanonicalCellDto("t1-r0-c2", 0, 2, "评审标准", "评审标准", 1, 1, false, "pdf:p24", 24, "t1-r0");
        var r0c3 = new CanonicalCellDto("t1-r0-c3", 0, 3, "评分\n分值", "评分分值", 1, 1, false, "pdf:p24", 24, "t1-r0");

        // Row 1: Item 1 (业绩)
        var r1c0 = new CanonicalCellDto("t1-r1-c0", 1, 0, "1", "1", 1, 1, false, "pdf:p24", 24, "t1-r1");
        var r1c1 = new CanonicalCellDto("t1-r1-c1", 1, 1, "业绩", "业绩", 1, 1, false, "pdf:p24", 24, "t1-r1");
        var r1c2 = new CanonicalCellDto("t1-r1-c2", 1, 2, "供应商近3年具备央企电子商城运营业绩得5分。", "供应商近3年具备央企电子商城运营业绩得5分。", 1, 1, false, "pdf:p24", 24, "t1-r1");
        var r1c3 = new CanonicalCellDto("t1-r1-c3", 1, 3, "5", "5", 1, 1, false, "pdf:p24", 24, "t1-r1");

        // Row 2: Continuation row (col 0, 1, 3 are blank; col 2 has remaining rule text)
        var r2c0 = new CanonicalCellDto("t1-r2-c0", 2, 0, "", "", 1, 1, false, "pdf:p24", 24, "t1-r2");
        var r2c1 = new CanonicalCellDto("t1-r2-c1", 2, 1, "", "", 1, 1, false, "pdf:p24", 24, "t1-r2");
        var r2c2 = new CanonicalCellDto("t1-r2-c2", 2, 2, "其中有效业绩30个及以上得5分，25-29个得4分，14个以下不得分。", "其中有效业绩30个及以上得5分，25-29个得4分，14个以下不得分。", 1, 1, false, "pdf:p24", 24, "t1-r2");
        var r2c3 = new CanonicalCellDto("t1-r2-c3", 2, 3, "", "", 1, 1, false, "pdf:p24", 24, "t1-r2");

        // Row 3: Item 2 (仓储能力)
        var r3c0 = new CanonicalCellDto("t1-r3-c0", 3, 0, "2", "2", 1, 1, false, "pdf:p24", 24, "t1-r3");
        var r3c1 = new CanonicalCellDto("t1-r3-c1", 3, 1, "仓储能力", "仓储能力", 1, 1, false, "pdf:p24", 24, "t1-r3");
        var r3c2 = new CanonicalCellDto("t1-r3-c2", 3, 2, "仓库资源匹配得10分。", "仓库资源匹配得10分。", 1, 1, false, "pdf:p24", 24, "t1-r3");
        var r3c3 = new CanonicalCellDto("t1-r3-c3", 3, 3, "10", "10", 1, 1, false, "pdf:p24", 24, "t1-r3");

        var table = new CanonicalTable("t1", 24, "第三章 评审标准", 4, 4, List.of(),
                List.of(r0c0, r0c1, r0c2, r0c3, r1c0, r1c1, r1c2, r1c3, r2c0, r2c1, r2c2, r2c3, r3c0, r3c1, r3c2, r3c3),
                true, "PDF");
        var artifact = new DocumentArtifact(new ArtifactMeta("doc.pdf", "hash", "PDF", "2.2"), List.of(table), List.of());

        var fact = new MultimodalScoreFact("第三章 评审标准", "shangwu_shili_yeji", null, "商务",
                new BigDecimal("5.00"), "供应商近3年具备央企电子商城运营业绩得5分。", "OBJECTIVE", 24, "t1",
                List.of("t1-r1-c2"), "供应商近3年具备央企电子商城运营业绩得5分。", 1.0, ChildAggregation.UNKNOWN,
                "scheme-1", "IN_SCHEME");

        var resolution = LogicalRuleSourceUnit.resolve(artifact, fact);

        assertThat(resolution.applicable()).isTrue();
        assertThat(resolution.closed()).isTrue();
        // Spans should contain both r1-c2 and the continuation r2-c2
        List<String> spanIds = resolution.spans().stream().map(SourceSpan::sourceId).toList();
        assertThat(spanIds).contains("t1-r1-c2", "t1-r2-c2");
    }

    @Test
    @DisplayName("DOCX评审表中条款号列纵向合并且末行跨章时，子评分项均应判定闭合且规则不混入一级维度合并列")
    void shouldCloseDocxVerticallyMergedRowsAndCrossChapterEnd() {
        // Table 1 (第三章 评标办法): 5 columns
        // Row 0: 表头
        var r0c0 = new CanonicalCellDto("t1-r0-c0", 0, 0, "条款号及名称", "条款号及名称", 1, 1, false, "word/document.xml", null, "t1-r0");
        var r0c1 = new CanonicalCellDto("t1-r0-c1", 0, 1, "", "", 1, 1, false, "word/document.xml", null, "t1-r0");
        var r0c2 = new CanonicalCellDto("t1-r0-c2", 0, 2, "评审因素", "评审因素", 1, 1, false, "word/document.xml", null, "t1-r0");
        var r0c3 = new CanonicalCellDto("t1-r0-c3", 0, 3, "评审标准", "评审标准", 1, 1, false, "word/document.xml", null, "t1-r0");
        var r0c4 = new CanonicalCellDto("t1-r0-c4", 0, 4, "", "", 1, 1, false, "word/document.xml", null, "t1-r0");

        // Row 1: 3.3(1) 项1 财务状况及抗风险能力（10分）
        var r1c0 = new CanonicalCellDto("t1-r1-c0", 1, 0, "3.3(1)", "3.3(1)", 1, 1, false, "word/document.xml", null, "t1-r1");
        var r1c1 = new CanonicalCellDto("t1-r1-c1", 1, 1, "商务评分标准（56分）", "商务评分标准（56分）", 1, 1, false, "word/document.xml", null, "t1-r1");
        var r1c2 = new CanonicalCellDto("t1-r1-c2", 1, 2, "财务状况及抗风险能力（10分）", "财务状况及抗风险能力（10分）", 1, 1, false, "word/document.xml", null, "t1-r1");
        var r1c3 = new CanonicalCellDto("t1-r1-c3", 1, 3, "提供2023-2025年度经会计事务所审计的财务报告。", "提供2023-2025年度经会计事务所审计的财务报告。", 1, 1, false, "word/document.xml", null, "t1-r1");
        var r1c4 = new CanonicalCellDto("t1-r1-c4", 1, 4, "审计意见连续3年无保留意见，得10分。", "审计意见连续3年无保留意见，得10分。", 1, 1, false, "word/document.xml", null, "t1-r1");

        // Row 2: 3.3(1) 项2 运营能力及业绩（20分）- c0 and c1 are vertically merged from row 1 (isMergedContinue = true)
        var r2c0 = new CanonicalCellDto("t1-r2-c0", 2, 0, "", "", 1, 1, true, "word/document.xml", null, "t1-r2");
        var r2c1 = new CanonicalCellDto("t1-r2-c1", 2, 1, "", "", 1, 1, true, "word/document.xml", null, "t1-r2");
        var r2c2 = new CanonicalCellDto("t1-r2-c2", 2, 2, "运营能力及业绩（20分）", "运营能力及业绩（20分）", 1, 1, false, "word/document.xml", null, "t1-r2");
        var r2c3 = new CanonicalCellDto("t1-r2-c3", 2, 3, "提供同类项目业绩证明。", "提供同类项目业绩证明。", 1, 1, false, "word/document.xml", null, "t1-r2");
        var r2c4 = new CanonicalCellDto("t1-r2-c4", 2, 4, "每提供1份有效业绩得5分，最高20分。", "每提供1份有效业绩得5分，最高20分。", 1, 1, false, "word/document.xml", null, "t1-r2");

        // Row 3: 3.3(2) 技术方案（20分）- 末行，随后进入第四章
        var r3c0 = new CanonicalCellDto("t1-r3-c0", 3, 0, "3.3(2)", "3.3(2)", 1, 1, false, "word/document.xml", null, "t1-r3");
        var r3c1 = new CanonicalCellDto("t1-r3-c1", 3, 1, "技术评分标准（44分）", "技术评分标准（44分）", 1, 1, false, "word/document.xml", null, "t1-r3");
        var r3c2 = new CanonicalCellDto("t1-r3-c2", 3, 2, "技术方案（20分）", "技术方案（20分）", 1, 1, false, "word/document.xml", null, "t1-r3");
        var r3c3 = new CanonicalCellDto("t1-r3-c3", 3, 3, "提供实施方案编制说明。", "提供实施方案编制说明。", 1, 1, false, "word/document.xml", null, "t1-r3");
        var r3c4 = new CanonicalCellDto("t1-r3-c4", 3, 4, "方案完善得20分。", "方案完善得20分。", 1, 1, false, "word/document.xml", null, "t1-r3");

        var table1 = new CanonicalTable("t1", 0, "第三章 评标办法/3. 评标标准", 4, 5, List.of(),
                List.of(r0c0, r0c1, r0c2, r0c3, r0c4,
                        r1c0, r1c1, r1c2, r1c3, r1c4,
                        r2c0, r2c1, r2c2, r2c3, r2c4,
                        r3c0, r3c1, r3c2, r3c3, r3c4),
                true, "DOCX");

        var artifact = new DocumentArtifact(new ArtifactMeta("doc.docx", "hash", "DOCX", "2.2"),
                List.of(table1), List.of());

        // Test 1: Row 1 (下一行是纵向合并行 r2，c0 为 isMergedContinue)
        var fact1 = new MultimodalScoreFact("第三章 评标办法/3. 评标标准", "3.3(1)_1", null, "商务",
                new BigDecimal("10.00"), "财务状况及抗风险能力（10分）", "OBJECTIVE", null, "t1",
                List.of("t1-r1-c2"), "财务状况及抗风险能力（10分）", 1.0, ChildAggregation.UNKNOWN,
                "scheme-1", "IN_SCHEME");

        var res1 = LogicalRuleSourceUnit.resolve(artifact, fact1);
        assertThat(res1.applicable()).isTrue();
        assertThat(res1.closed())
                .as("下一行虽然 c0 纵向合并，但 c2 为独立子项名称，应判定当前行已闭合")
                .isTrue();

        // 规则不应包含 c1 (一级维度合并列)，应包含 c3 与 c4
        List<String> spans1 = res1.spans().stream().map(SourceSpan::sourceId).toList();
        assertThat(spans1).contains("t1-r1-c3", "t1-r1-c4");
        assertThat(spans1).doesNotContain("t1-r1-c1");

        // Test 2: Row 2 (下一行同样是纵向合并行 r3)
        var fact2 = new MultimodalScoreFact("第三章 评标办法/3. 评标标准", "3.3(1)_2", null, "商务",
                new BigDecimal("20.00"), "运营能力及业绩（20分）", "OBJECTIVE", null, "t1",
                List.of("t1-r2-c2"), "运营能力及业绩（20分）", 1.0, ChildAggregation.UNKNOWN,
                "scheme-1", "IN_SCHEME");

        var res2 = LogicalRuleSourceUnit.resolve(artifact, fact2);
        assertThat(res2.applicable()).isTrue();
        assertThat(res2.closed())
                .as("下一行同样为纵向合并子项行，当前行应判定已闭合")
                .isTrue();

        // Test 3: Row 3 (末行，表末自然闭合)
        var fact3 = new MultimodalScoreFact("第三章 评标办法/3. 评标标准", "3.3(2)", null, "技术",
                new BigDecimal("20.00"), "技术方案（20分）", "OBJECTIVE", null, "t1",
                List.of("t1-r3-c2"), "技术方案（20分）", 1.0, ChildAggregation.UNKNOWN,
                "scheme-1", "IN_SCHEME");

        var res3 = LogicalRuleSourceUnit.resolve(artifact, fact3);
        assertThat(res3.applicable()).isTrue();
        assertThat(res3.closed())
                .as("表末行无后续行或跨表未冲突，应判定已闭合")
                .isTrue();
    }
    @Test
    void project1133SummaryWithBlankOuterCellsDoesNotContinueWarehouseRule() {
        var cells = new java.util.ArrayList<CanonicalCellDto>();
        String[][] rows = {{"分项", "评分因素", "分值", "说明"},
                {"", "仓储情况", "5", "按仓储中心布点排名，最多得5分，每名递减1分。"},
                {"", "合计", "100", ""}};
        for (int r = 0; r < rows.length; r++) for (int c = 0; c < 4; c++) {
            cells.add(new CanonicalCellDto("t-r" + r + "-c" + c, r, c, rows[r][c], rows[r][c],
                    1, 1, false, "pdf:p57", 57, "t-r" + r));
        }
        var table = new CanonicalTable("t", 57, "第七章 评分标准", 3, 4, List.of(), cells, true, "PDF");
        var artifact = new DocumentArtifact(new ArtifactMeta("synthetic.pdf", "sha", "PDF", "2.2"), List.of(table), List.of());
        var fact = new MultimodalScoreFact("第七章 评分标准", "warehouse", "", "仓储情况", new BigDecimal("5"),
                "按仓储中心布点排名，最多得5分，每名递减1分。", "OBJECTIVE", 57, "t", List.of("t-r1-c1"),
                "仓储情况", 1.0);
        var resolution = LogicalRuleSourceUnit.resolve(artifact, fact);
        assertThat(resolution.closed()).isTrue();
        assertThat(resolution.spans()).extracting(SourceSpan::sourceId).doesNotContain("t-r2-c1", "t-r2-c2");
        assertThat(OriginalRuleRestoration.restore(fact, artifact, List.of(fact)).text()).doesNotContain("合计", "100");
    }

}
