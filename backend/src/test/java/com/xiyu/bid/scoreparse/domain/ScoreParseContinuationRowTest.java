package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.CanonicalTableGrid;
import com.xiyu.bid.scoreparse.domain.grid.CanonicalTableGridFactory;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable.CanonicalCellDto;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable.MergedRegionDto;
import com.xiyu.bid.scoreparse.domain.grid.HeuristicTableColumnMapper;
import com.xiyu.bid.scoreparse.domain.grid.TableColumnMapping;
import com.xiyu.bid.scoreparse.domain.grid.TableGrid;
import com.xiyu.bid.scoreparse.domain.grid.TableGridExtractor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 针对项目 33（湖北中烟）与项目 1117（招商积余）真实生产缺陷的根因测试：
 * 1. 跨页续表残行不能通过 Title Weight Fallback 劫持跨行大类单元格的 30 分总分；
 * 2. 无显式表头表格根据纯数值密度推断分值列，严禁无脑 fallback 到最后一列备注列。
 */
class ScoreParseContinuationRowTest {

    private static CanonicalCellDto cell(String id, int row, int col, String text) {
        return new CanonicalCellDto(id, row, col, text, text, 1, 1, false, "/xpath");
    }

    private static CanonicalCellDto cellWithMerge(String id, int row, int col, String text, int rowspan, boolean isMergedContinue) {
        return new CanonicalCellDto(id, row, col, text, text, rowspan, 1, isMergedContinue, "/xpath");
    }

    @Test
    @DisplayName("项目 33 根因：跨行大类（技术部分 30分）在显式分值列为空的续表残行上，绝对不能被赋为 30 分叶子项")
    void testContinuationRowDoesNotStealParentCategoryScore() {
        // 模拟第 40 页表格 p40-t0019：
        // r0: 表头（项目 | 评审因素 | 分值 | 评审标准）
        // r1: 大类跨行首行（技术部分（30分）），但此行右侧是上一页总体服务方案的续表细则，分值列为空！
        // r2: 物流仓储配送方案（8分）
        // r3: 商品质量内控方案（6分）
        // r4: 售前售后服务方案（6分）
        List<CanonicalCellDto> cells = List.of(
                // Header (r0)
                cell("r0-c0", 0, 0, "项目"),
                cell("r0-c1", 0, 1, "评审因素"),
                cell("r0-c2", 0, 2, "分值"),
                cell("r0-c3", 0, 3, "评审标准"),
                // Row 1: 跨页续表残行，c0 是技术部分（30分）跨行大类（rowspan=4），c2(分值列)为空！
                cellWithMerge("r1-c0", 1, 0, "技术部分（30分）", 4, false),
                cell("r1-c1", 1, 1, ""),
                cell("r1-c2", 1, 2, ""), // 显式分值为空！
                cell("r1-c3", 1, 3, "品上架时间保障方案；③服务团队配置。1、方案合理得8-10分"),
                // Row 2: 物流方案 8分
                cellWithMerge("r2-c0", 2, 0, "", 1, true),
                cell("r2-c1", 2, 1, "物流仓储配送方案"),
                cell("r2-c2", 2, 2, "8"),
                cell("r2-c3", 2, 3, "方案完整得6-8分"),
                // Row 3: 商品质量 6分
                cellWithMerge("r3-c0", 3, 0, "", 1, true),
                cell("r3-c1", 3, 1, "商品质量内控方案"),
                cell("r3-c2", 3, 2, "6"),
                cell("r3-c3", 3, 3, "方案好得4-6分"),
                // Row 4: 售前售后 6分
                cellWithMerge("r4-c0", 4, 0, "", 1, true),
                cell("r4-c1", 4, 1, "售前售后服务方案"),
                cell("r4-c2", 4, 2, "6"),
                cell("r4-c3", 4, 3, "方案好得4-6分")
        );

        List<MergedRegionDto> regions = List.of(
                new MergedRegionDto("r1-c0", 4, 1)
        );

        CanonicalTable table = new CanonicalTable("p40-t0019", null, "评分标准", 5, 4, regions, cells, true);
        CanonicalTableGrid grid = CanonicalTableGridFactory.fromSidecarTable(table);
        // 生产真实映射：c0为dimCol，c2为weightCol，c3为detailCol
        TableColumnMapping mapping = new TableColumnMapping(-1, -1, 0, 3, 2, 1, false);

        List<ScoreFact> facts = DeterministicScoreFactExtractor.extract(grid, mapping);

        // 核心断言：绝对不能生成权重为 30.00 的叶子事实！
        assertThat(facts).noneMatch(f -> f.weight() != null && f.weight().compareTo(BigDecimal.valueOf(30)) == 0);

        // 叶子项必须只有 8分、6分、6分，不能包含虚假的 30 分！
        List<ScoreFact> leafFacts = facts.stream().filter(f -> f.weight() != null).toList();
        BigDecimal total = leafFacts.stream().map(ScoreFact::weight).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(total).isEqualByComparingTo("20.0"); // 8 + 6 + 6 = 20分
    }

    @Test
    @DisplayName("项目 1117 根因：5列无表头表格，分值列必须推断为数值密度最高的第 3 列，而非第 4 列备注列")
    void testNumericWeightColumnDensityInference() {
        String markdown = """
                | 3 | 综合商务方案 | 详细要求与标准1... | 25.0 | 备注说明 |
                | 4 | 服务团队方案 | 详细要求与标准2... | 5.0 | 备注说明 |
                """;
        TableGrid grid = TableGridExtractor.extract(markdown);
        TableColumnMapping mapping = HeuristicTableColumnMapper.infer(grid);

        // 第 0 列是序号，第 1 列是因素，第 2 列是细则，第 3 列是 25.0/5.0 分值列，第 4 列是备注
        assertThat(mapping.weightCol()).isEqualTo(3);
        assertThat(mapping.detailCol()).isEqualTo(2);
    }
}
