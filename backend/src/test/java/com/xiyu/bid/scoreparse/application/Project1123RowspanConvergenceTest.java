package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.*;
import com.xiyu.bid.scoreparse.domain.grid.*;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable.CanonicalCellDto;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable.MergedRegionDto;
import com.xiyu.bid.scoreparse.infrastructure.baidu.BaiduTableArtifactAdapter;
import com.xiyu.bid.scoreparse.infrastructure.baidu.BaiduTableModels.BaiduTableCell;
import com.xiyu.bid.scoreparse.infrastructure.baidu.BaiduTableModels.BaiduTableItem;
import com.xiyu.bid.scoreparse.infrastructure.baidu.BaiduTableModels.BaiduTableResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 针对线上 Project 1123（大跨度 Rowspan + 平级数字项）解析失真的根因与收敛测试：
 * 1. OCR 网格跨行继承下沉（向下穿透父级名称与 Cell ID），杜绝 SOURCE_QUOTE_NOT_VERIFIED 误杀 24 分；
 * 2. 跨行合并分值聚合与虚构大项剪枝（Prune Synthetic Parent），杜绝 19分、13分沦为方案外杂音；
 * 3. 补漏恢复（Row Recovery）幂等与去重门禁，杜绝 6/7 项重复追加导致 108 分。
 */
class Project1123RowspanConvergenceTest {

    private static CanonicalCellDto cell(String id, int r, int c, String text) {
        return new CanonicalCellDto(id, r, c, text, text, 1, 1, false, "pdf:page=1;row=" + r + ";col=" + c);
    }

    private static CanonicalCellDto mergedCell(String id, int r, int c, String text, int rowspan, int colspan) {
        return new CanonicalCellDto(id, r, c, text, text, rowspan, colspan, false, "pdf:page=1;row=" + r + ";col=" + c);
    }

    private static CanonicalCellDto continueCell(String id, int r, int c) {
        return new CanonicalCellDto(id, r, c, "", "", 1, 1, true, "pdf:page=1;row=" + r + ";col=" + c);
    }

    private static BaiduTableCell bCell(int rStart, int rEnd, int cStart, int cEnd, String words) {
        return new BaiduTableCell(rStart, rEnd, cStart, cEnd, words, List.of());
    }

    @Test
    @DisplayName("收敛点 1A: BaiduTableArtifactAdapter 构建的跨行续行必须继承父级单元格文本")
    void shouldInheritTextInBaiduTableGridBuilderContinuationCells() {
        List<BaiduTableCell> body = List.of(
                // Header (Row 0)
                bCell(0, 0, 0, 0, "序号"),
                bCell(0, 0, 1, 1, "评审项目及分值"),
                bCell(0, 0, 2, 2, "评分标准"),
                bCell(0, 0, 3, 3, "分值"),
                // Row 1 (Item 5 origin, rowspan=4)
                bCell(1, 1, 0, 0, "5"),
                bCell(1, 4, 1, 1, "物流保障与配送服务能力 19分"),
                bCell(1, 1, 2, 2, "所有订单免费配送"),
                bCell(1, 1, 3, 3, "4"),
                // Row 2 (Item 6 continuation)
                bCell(2, 2, 0, 0, "6"),
                bCell(2, 2, 2, 2, "根据提供全国配送能力"),
                bCell(2, 2, 3, 3, "4"),
                // Row 3 (Item 7 continuation)
                bCell(3, 3, 0, 0, "7"),
                bCell(3, 3, 2, 2, "提供送货上门"),
                bCell(3, 3, 3, 3, "4"),
                // Row 4 (Item 8 continuation)
                bCell(4, 4, 0, 0, "8"),
                bCell(4, 4, 2, 2, "提供应急配送"),
                bCell(4, 4, 3, 3, "7")
        );

        BaiduTableItem tableItem = new BaiduTableItem(List.of(), List.of(), body, List.of());
        BaiduTableResponse response = new BaiduTableResponse(1, List.of(tableItem), 0, null, 123456L);

        List<CanonicalTable> tables = BaiduTableArtifactAdapter.toCanonicalTables(response, 1, "第三章 评审办法");
        assertThat(tables).hasSize(1);
        CanonicalTable table = tables.get(0);

        // 检查 Row 2, Col 1 的续行单元格
        CanonicalCellDto r2c1 = table.cells().stream()
                .filter(c -> c.row() == 2 && c.column() == 1)
                .findFirst().orElseThrow();

        assertThat(r2c1.isMergedContinue()).isTrue();
        assertThat(r2c1.rawText()).isEqualTo("物流保障与配送服务能力 19分");
        assertThat(r2c1.normalizedText()).isEqualTo("物流保障与配送服务能力 19分");
    }

    @Test
    @DisplayName("收敛点 1B: CanonicalTableGridFactory 对续行单元格自动向下渗透父级文本与 Cell ID 关联")
    void shouldInheritTextInCanonicalTableGridFactory() {
        CanonicalTable table = new CanonicalTable("t1", 1, "第三章 评审办法", 5, 4,
                List.of(new MergedRegionDto("t1_r1_c1", 4, 1)),
                List.of(
                        cell("t1_r0_c0", 0, 0, "序号"), cell("t1_r0_c1", 0, 1, "评审项目及分值"),
                        cell("t1_r0_c2", 0, 2, "评分标准"), cell("t1_r0_c3", 0, 3, "分值"),

                        cell("t1_r1_c0", 1, 0, "5"),
                        mergedCell("t1_r1_c1", 1, 1, "物流保障与配送服务能力 19分", 4, 1),
                        cell("t1_r1_c2", 1, 2, "所有订单免费配送"), cell("t1_r1_c3", 1, 3, "4"),

                        cell("t1_r2_c0", 2, 0, "6"),
                        continueCell("t1_r2_c1", 2, 1),
                        cell("t1_r2_c2", 2, 2, "根据提供全国配送能力"), cell("t1_r2_c3", 2, 3, "4"),

                        cell("t1_r3_c0", 3, 0, "7"),
                        continueCell("t1_r3_c1", 3, 1),
                        cell("t1_r3_c2", 3, 2, "提供送货上门"), cell("t1_r3_c3", 3, 3, "4"),

                        cell("t1_r4_c0", 4, 0, "8"),
                        continueCell("t1_r4_c1", 4, 1),
                        cell("t1_r4_c2", 4, 2, "提供应急配送"), cell("t1_r4_c3", 4, 3, "7")
                ),
                true
        );

        CanonicalTableGrid grid = CanonicalTableGridFactory.fromSidecarTable(table);
        assertThat(grid.isStructurallyValid()).isTrue();

        CanonicalCell c21 = grid.findCell("t1_r2_c1").orElseThrow();
        assertThat(c21.rawText()).isEqualTo("物流保障与配送服务能力 19分");

        // 验证确定性事实提取器中续行事实必须继承 originCellId 和父级名称
        TableColumnMapping mapping = new TableColumnMapping(0, -1, 1, 2, 3, 1, false);
        List<ScoreFact> facts = DeterministicScoreFactExtractor.extract(grid, mapping);

        ScoreFact item6 = facts.stream().filter(f -> "6".equals(f.code())).findFirst().orElseThrow();
        assertThat(item6.sourceCellIds()).contains("t1_r1_c1");
        assertThat(item6.dimension()).contains("物流保障与配送服务能力");
    }

    @Test
    @DisplayName("收敛点 1C: 确定性管道能够通过 OriginalSourcePublicationGuard 核验 19分大类及其 4 个平级子项 (4+4+4+7=19)")
    void shouldPassOriginalSourcePublicationGuardForProject1123Pattern() {
        CanonicalTable table = new CanonicalTable("t1", 1, "第三章 评审办法", 5, 4,
                List.of(new MergedRegionDto("t1_r1_c1", 4, 1)),
                List.of(
                        cell("t1_r0_c0", 0, 0, "序号"), cell("t1_r0_c1", 0, 1, "评审项目及分值"),
                        cell("t1_r0_c2", 0, 2, "评分标准"), cell("t1_r0_c3", 0, 3, "分值"),

                        cell("t1_r1_c0", 1, 0, "5"),
                        mergedCell("t1_r1_c1", 1, 1, "物流保障与配送服务能力 19分", 4, 1),
                        cell("t1_r1_c2", 1, 2, "所有订单免费配送"), cell("t1_r1_c3", 1, 3, "4"),

                        cell("t1_r2_c0", 2, 0, "6"),
                        continueCell("t1_r2_c1", 2, 1),
                        cell("t1_r2_c2", 2, 2, "根据提供全国配送能力"), cell("t1_r2_c3", 2, 3, "4"),

                        cell("t1_r3_c0", 3, 0, "7"),
                        continueCell("t1_r3_c1", 3, 1),
                        cell("t1_r3_c2", 3, 2, "提供送货上门"), cell("t1_r3_c3", 3, 3, "4"),

                        cell("t1_r4_c0", 4, 0, "8"),
                        continueCell("t1_r4_c1", 4, 1),
                        cell("t1_r4_c2", 4, 2, "提供应急配送"), cell("t1_r4_c3", 4, 3, "7")
                ),
                true
        );

        var artifact = new DocumentArtifact(
                new DocumentArtifact.ArtifactMeta("test.pdf", "sha", "pdf", "2.0"),
                List.of(table),
                List.of()
        );

        CanonicalTableGrid grid = CanonicalTableGridFactory.fromSidecarTable(table);
        TableColumnMapping mapping = new TableColumnMapping(0, -1, 1, 2, 3, 1, false);
        List<ScoreFact> facts = DeterministicScoreFactExtractor.extract(grid, mapping);

        OriginalScoringContext context = OriginalScoringContext.resolve(artifact, "本项目综合评估法评分总分19分。");
        var verification = OriginalScoreEvidencePolicy.verifyFacts(context, artifact, facts);

        // 验证续行事实（6, 7, 8 项）不触发 SOURCE_QUOTE_NOT_VERIFIED
        assertThat(verification.problems())
                .noneMatch(p -> "SOURCE_QUOTE_NOT_VERIFIED".equals(p.code()));

        // 验证 PublicationGuard
        var check = OriginalSourcePublicationGuard.verify(context, artifact, facts);
        assertThat(check.defects()).isEmpty();
        assertThat(check.leafTotal()).isEqualByComparingTo(new BigDecimal("19"));
    }

    @Test
    @DisplayName("收敛点 2: 虚构大项剪枝：当存在无单元格的大项（19分、13分）且平级子项和精确等于大项时，自动剪枝或容器化")
    void shouldPruneSyntheticParentWhenChildrenSumMatchesParentWeight() {
        // 模型提取出的事实包含：19分大项（无 tableId 无单元格） + 4个4分/7分子项
        MultimodalScoreFact parent19 = new MultimodalScoreFact(
                "第三章 评审办法", "物流保障与配送服务能力", "", "物流保障与配送服务能力", new BigDecimal("19"),
                "", "SUBJECTIVE", 1, "", List.of(), "物流保障与配送服务能力", 0.95
        );

        MultimodalScoreFact c5 = new MultimodalScoreFact(
                "第三章 评审办法", "5", "", "所有订单免费配送", new BigDecimal("4"),
                "所有订单免费配送得4分", "SUBJECTIVE", 1, "t1", List.of("t1_r1_c2"), "所有订单免费配送", 0.95
        );
        MultimodalScoreFact c6 = new MultimodalScoreFact(
                "第三章 评审办法", "6", "", "全国配送能力", new BigDecimal("4"),
                "全国配送能力得4分", "SUBJECTIVE", 1, "t1", List.of("t1_r2_c2"), "全国配送能力", 0.95
        );
        MultimodalScoreFact c7 = new MultimodalScoreFact(
                "第三章 评审办法", "7", "", "送货上门服务", new BigDecimal("4"),
                "送货上门服务得4分", "SUBJECTIVE", 1, "t1", List.of("t1_r3_c2"), "送货上门服务", 0.95
        );
        MultimodalScoreFact c8 = new MultimodalScoreFact(
                "第三章 评审办法", "8", "", "应急配送时效", new BigDecimal("7"),
                "应急配送时效得7分", "SUBJECTIVE", 1, "t1", List.of("t1_r4_c2"), "应急配送时效", 0.95
        );

        List<MultimodalScoreFact> facts = List.of(parent19, c5, c6, c7, c8);
        List<MultimodalScoreFact> pruned = MultimodalExtractionReviewPolicy.pruneSyntheticParents(facts);

        // 19分虚构大项必须被剪枝，不应出现在事实列表中成为方案外杂音
        assertThat(pruned).doesNotContain(parent19);
        assertThat(pruned).containsExactly(c5, c6, c7, c8);
    }

    @Test
    @DisplayName("收敛点 3: 补漏恢复（Row Recovery）幂等去重：已有同编号/分值模型事实时严禁重复追加")
    void shouldNotRecoverDuplicateRowWhenModelAlreadyHasIdenticalItem() {
        CanonicalTable table = new CanonicalTable("t1", 1, "第三章 评审办法", 3, 4,
                List.of(),
                List.of(
                        cell("t1_r0_c0", 0, 0, "序号"), cell("t1_r0_c1", 0, 1, "评审项目及分值"),
                        cell("t1_r0_c2", 0, 2, "评分标准"), cell("t1_r0_c3", 0, 3, "分值"),

                        cell("t1_r1_c0", 1, 0, "5"), cell("t1_r1_c1", 1, 1, "免费配送"),
                        cell("t1_r1_c2", 1, 2, "所有订单免费配送得4分"), cell("t1_r1_c3", 1, 3, "4"),

                        cell("t1_r2_c0", 2, 0, "6"), cell("t1_r2_c1", 2, 1, "全国配送"),
                        cell("t1_r2_c2", 2, 2, "全国配送得4分"), cell("t1_r2_c3", 2, 3, "4")
                ),
                true
        );

        var artifact = new DocumentArtifact(
                new DocumentArtifact.ArtifactMeta("test.pdf", "sha", "pdf", "2.0"),
                List.of(table),
                List.of()
        );

        OriginalScoringContext context = OriginalScoringContext.resolve(artifact, "评分总分8分。");

        // 模型已抽出了 5 项(4分) 和 6 项(4分)
        MultimodalScoreFact m5 = new MultimodalScoreFact(
                "第三章 评审办法", "5", "", "免费配送", new BigDecimal("4"),
                "所有订单免费配送得4分", "SUBJECTIVE", 1, "t1", List.of("t1_r1_c2"), "所有订单免费配送", 0.95
        );
        MultimodalScoreFact m6 = new MultimodalScoreFact(
                "第三章 评审办法", "6", "", "全国配送", new BigDecimal("4"),
                "全国配送得4分", "SUBJECTIVE", 1, "t1", List.of("t1_r2_c2"), "全国配送", 0.95
        );

        // 构造一个模拟包含 SOURCE_SCORE_ROW_UNCOVERED 违规的报告（假设 row 2 因某种原因误报未覆盖）
        var violations = List.of(
                new MultimodalScoreFactValidator.Violation("f6", "sourceRow", "SOURCE_SCORE_ROW_UNCOVERED",
                        "未覆盖行", "6", "t1:t1-r2", "t1:t1-r2")
        );
        var sourceVerification = OriginalScoreEvidencePolicy.verifyModel(context, artifact, "评分总分8分。", List.of(m5, m6), new BigDecimal("8"));
        var report = new MultimodalScoreFactValidator.ValidationReport(
                List.of(m5, m6), new BigDecimal("8"), new BigDecimal("8"), new BigDecimal("8"),
                false, violations, sourceVerification
        );

        SourceReviewRowRecovery.Result recovery = SourceReviewRowRecovery.recover(artifact, context, "评分总分8分。", report);

        // 严禁二次追加已有第 6 项
        assertThat(recovery.facts()).noneMatch(f -> "6".equals(f.code()) && new BigDecimal("4").equals(f.weight()));
    }
}
