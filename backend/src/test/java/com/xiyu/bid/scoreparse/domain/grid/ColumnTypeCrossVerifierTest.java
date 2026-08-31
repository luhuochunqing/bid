package com.xiyu.bid.scoreparse.domain.grid;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ColumnTypeCrossVerifierTest {

    @Test
    void verify_emptyWeightColumnWithRows_rejected() {
        // 空列穿透守卫：数据行存在但权重列全空 → 拒绝（防无效表格进入提取阶段，MRO 场景）
        String markdown = """
                | 序号 | 评分因素 | 评分标准 | 分值 |
                |---|---|---|---|
                | 1 | 资质要求 | 具备甲级资质得5分 | |
                | 2 | 业绩要求 | 近三年同类业绩每份得2分 | |
                """;

        TableGrid grid = TableGridExtractor.extract(markdown);
        TableColumnMapping mapping = new TableColumnMapping(0, -1, 1, 2, 3, 1, false);
        ColumnTypeCrossVerifier.ValidationResult vr = ColumnTypeCrossVerifier.verify(grid, mapping);
        assertThat(vr.valid()).isFalse();
        assertThat(vr.reason()).contains("占比过低");
    }

    @Test
    void verify_weightDensityBelowFloor_rejected() {
        // 密度地板锚定（0.4）：权重列非空 4 格仅 1 格有分值特征（25% < 40%）→ 列映射不可信。
        // 注意非分值文本不能含"分"字（isPlausibleWeight 对 <=10 字符放行含"分"/"%"文本）
        String markdown = """
                | 序号 | 评分因素 | 评分标准 | 分值 |
                |---|---|---|---|
                | 1 | 资质要求 | 具备甲级资质得5分 | 5 |
                | 2 | 业绩要求 | 业绩情况说明细则文本较长 | 甲 |
                | 3 | 财务要求 | 财务状况说明细则文本较长 | 乙 |
                | 4 | 服务要求 | 服务能力说明细则文本较长 | 丙 |
                """;

        TableGrid grid = TableGridExtractor.extract(markdown);
        TableColumnMapping mapping = new TableColumnMapping(0, -1, 1, 2, 3, 1, false);
        ColumnTypeCrossVerifier.ValidationResult vr = ColumnTypeCrossVerifier.verify(grid, mapping);
        assertThat(vr.valid()).isFalse();
        assertThat(vr.reason()).contains("占比过低");
    }

    @Test
    void verify_weightDensity_boundaryAnchors() {
        // 边界锚定（复审 Finding 5）：3/8 = 0.375——0.4 地板下拒绝，若有人改回 0.35 则本用例
        // 通过变红，防止地板被静默下调；反向边界 2/5 = 0.4 不小于地板 → 通过
        String markdown = """
                | 序号 | 评分因素 | 评分标准 | 分值 |
                |---|---|---|---|
                | 1 | 资质要求 | 具备甲级资质得5分 | 5 |
                | 2 | 业绩要求 | 业绩说明文本 | 甲 |
                | 3 | 财务要求 | 财务说明文本 | 乙 |
                | 4 | 服务要求 | 服务说明文本 | 丙 |
                | 5 | 仓储要求 | 仓储说明文本 | 丁 |
                | 6 | 培训要求 | 培训说明文本 | 戊 |
                | 7 | 应急要求 | 应急说明文本 | 己 |
                | 8 | 售后要求 | 售后说明文本 | 庚 |
                """;

        TableGrid grid = TableGridExtractor.extract(markdown);
        TableColumnMapping mapping = new TableColumnMapping(0, -1, 1, 2, 3, 1, false);
        ColumnTypeCrossVerifier.ValidationResult vr = ColumnTypeCrossVerifier.verify(grid, mapping);
        assertThat(vr.valid()).isFalse();
        assertThat(vr.reason()).contains("占比过低");

        // 2/5 = 0.4 恰好触地板 → 通过
        String markdownAtFloor = """
                | 序号 | 评分因素 | 评分标准 | 分值 |
                |---|---|---|---|
                | 1 | 资质要求 | 具备甲级资质得5分 | 5 |
                | 2 | 业绩要求 | 业绩说明文本 | 3 |
                | 3 | 财务要求 | 财务说明文本较长 | 甲 |
                | 4 | 服务要求 | 服务说明文本较长 | 乙 |
                | 5 | 仓储要求 | 仓储说明文本较长 | 丙 |
                """;
        TableGrid gridAtFloor = TableGridExtractor.extract(markdownAtFloor);
        ColumnTypeCrossVerifier.ValidationResult vrAtFloor =
                ColumnTypeCrossVerifier.verify(gridAtFloor, new TableColumnMapping(0, -1, 1, 2, 3, 1, false));
        assertThat(vrAtFloor.valid()).isTrue();
    }

    @Test
    void verify_bracketWeightColumn_passesDensityCheck() {
        // 内嵌括号分值列（MRO 场景）：维度列即权重列，全部行有"（N分）" → 有效
        String markdown = """
                | 序号 | 评分因素 | 评分标准 |
                |---|---|---|
                | 1 | 供应商业绩（7分） | 近三年同类业绩每份得2分 |
                | 2 | 财务状况（3分） | 经营活动现金流量净额连续为正 |
                """;

        TableGrid grid = TableGridExtractor.extract(markdown);
        // dimCol=weightCol=1：括号分值单元格长度 >10 走独立括号分支计入有效
        TableColumnMapping mapping = new TableColumnMapping(0, -1, 1, 2, 1, 1, false);
        ColumnTypeCrossVerifier.ValidationResult vr = ColumnTypeCrossVerifier.verify(grid, mapping);
        assertThat(vr.valid()).isTrue();
    }
}
