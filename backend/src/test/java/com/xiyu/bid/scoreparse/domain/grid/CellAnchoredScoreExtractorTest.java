package com.xiyu.bid.scoreparse.domain.grid;

import com.xiyu.bid.scoreparse.domain.ScoreCandidate;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CellAnchoredScoreExtractorTest {

    @Test
    void extract_standardGridTable_extractsVerbatimAndNaturalOrder() {
        String markdown = """
                | 序号 | 评分因素 | 评分标准 | 权重 |
                |---|---|---|---|
                | 1 | 资质要求 | 具备甲级资质得5分，乙级得3分 | 5分 |
                | 2 | 业绩要求 | 近三年同类业绩每份得2分，满分10分 | 10分 |
                """;

        TableGrid grid = TableGridExtractor.extract(markdown);
        assertThat(grid.rowCount()).isEqualTo(3);
        assertThat(grid.colCount()).isEqualTo(4);

        TableColumnMapping mapping = new TableColumnMapping(0, -1, 1, 2, 3, 1, false);
        ColumnTypeCrossVerifier.ValidationResult vr = ColumnTypeCrossVerifier.verify(grid, mapping);
        assertThat(vr.valid()).isTrue();

        List<ScoreCandidate> candidates = CellAnchoredScoreExtractor.extract(grid, mapping);
        assertThat(candidates).hasSize(2);

        // 第 1 项
        ScoreCandidate item1 = candidates.get(0);
        assertThat(item1.code()).isEqualTo("1");
        assertThat(item1.dim()).isEqualTo("资质要求");
        assertThat(item1.detail()).isEqualTo("具备甲级资质得5分，乙级得3分");
        assertThat(item1.weight()).isEqualByComparingTo(new BigDecimal("5"));

        // 第 2 项
        ScoreCandidate item2 = candidates.get(1);
        assertThat(item2.code()).isEqualTo("2");
        assertThat(item2.dim()).isEqualTo("业绩要求");
        assertThat(item2.detail()).isEqualTo("近三年同类业绩每份得2分，满分10分");
        assertThat(item2.weight()).isEqualByComparingTo(new BigDecimal("10"));
    }

    @Test
    void extract_project1347_compoundCodeAndPercentWeight() {
        // 模拟 Project 1347 的 3 列表格（评分因素 | 评分标准 | 权重%）
        String markdown = """
                | 评分因素 | 评分标准 | 权重 |
                |---|---|---|
                | 响应文件的完整性 | (1) 存在漏项扣10分；(2) 未提供word文档扣20分；满分100分，扣完为止。 | 4% |
                | 财务状况 | (1) 速动比率>=1得50分；(2) 营业收现比率>=1得50分。本项满分100分。 | 6% |
                """;

        TableGrid grid = TableGridExtractor.extract(markdown);
        assertThat(grid.rowCount()).isEqualTo(3);

        TableColumnMapping mapping = new TableColumnMapping(-1, -1, 0, 1, 2, 1, true);
        ColumnTypeCrossVerifier.ValidationResult vr = ColumnTypeCrossVerifier.verify(grid, mapping);
        assertThat(vr.valid()).isTrue();

        List<ScoreCandidate> candidates = CellAnchoredScoreExtractor.extract(grid, mapping);
        assertThat(candidates).hasSize(2);

        // 验证 100% 逐字保留、分值准确识别为 4 与 6
        assertThat(candidates.get(0).dim()).isEqualTo("响应文件的完整性");
        assertThat(candidates.get(0).weight()).isEqualByComparingTo(new BigDecimal("4"));
        assertThat(candidates.get(0).detail()).contains("未提供word文档扣20分");

        assertThat(candidates.get(1).dim()).isEqualTo("财务状况");
        assertThat(candidates.get(1).weight()).isEqualByComparingTo(new BigDecimal("6"));
        assertThat(candidates.get(1).detail()).contains("速动比率>=1得50分");
    }

    @Test
    void extract_verticalMergedCategories_fillDownSuccessfully() {
        // 模拟 4 列表格包含纵向合并大类（大类 | 细项 | 细则 | 分值）
        String markdown = """
                | 评审大类 | 评分项 | 评分细则 | 分值 |
                |---|---|---|---|
                | 商务部分 (30分) | 财务状况 | 资产负债率合理得10分 | 10 |
                | | 商业信誉 | 无不良记录得10分 | 10 |
                | | 履约业绩 | 优质业绩每份5分 | 10 |
                | 技术部分 (70分) | 实施方案 | 方案完善得40分 | 40 |
                """;

        TableGrid grid = TableGridExtractor.extract(markdown);
        TableColumnMapping mapping = new TableColumnMapping(-1, 0, 1, 2, 3, 1, false);

        List<ScoreCandidate> candidates = CellAnchoredScoreExtractor.extract(grid, mapping);
        assertThat(candidates).hasSize(4);

        // 验证纵向合并 Fill-down 效果
        assertThat(candidates.get(0).dim()).isEqualTo("商务部分/财务状况");
        assertThat(candidates.get(1).dim()).isEqualTo("商务部分/商业信誉");
        assertThat(candidates.get(2).dim()).isEqualTo("商务部分/履约业绩");
        assertThat(candidates.get(3).dim()).isEqualTo("技术部分/实施方案");
    }

    @Test
    void verify_invalidMapping_catchesWrongColumnIndex() {
        String markdown = """
                | 序号 | 评分因素 | 评分标准 | 权重 |
                |---|---|---|---|
                | 1 | 资质要求 | 具备甲级资质得5分 | 5分 |
                """;

        TableGrid grid = TableGridExtractor.extract(markdown);
        // 故意将 weightCol 指向 detail 列 (列2)
        TableColumnMapping wrongMapping = new TableColumnMapping(0, -1, 1, 3, 2, 1, false);
        ColumnTypeCrossVerifier.ValidationResult vr = ColumnTypeCrossVerifier.verify(grid, wrongMapping);
        assertThat(vr.valid()).isFalse();
        assertThat(vr.reason()).contains("细则列平均长度过短");
    }

    @Test
    void extract_priceSubItems_splitsOnlyWhenSumMatchesParentWeight() {
        // 加法型价格分（MRO 场景）：40 = 京东 28 + 自有商城 12，子项之和==父行分值 → 拆分
        String markdown = """
                | 评分因素 | 评分标准 | 分值 |
                |---|---|---|
                | 价格分 | 1、上架折扣率（京东工业品）（28分）。供应商在响应文件中填报上架折扣率；2、上架折扣率（自有商城）（12分）。供应商在响应文件中填报上架折扣率 | 40 |
                """;

        TableGrid grid = TableGridExtractor.extract(markdown);
        TableColumnMapping mapping = new TableColumnMapping(-1, -1, 0, 1, 2, 1, false);
        List<ScoreCandidate> candidates = CellAnchoredScoreExtractor.extract(grid, mapping);

        assertThat(candidates).hasSize(2);
        assertThat(candidates.get(0).dim()).contains("京东工业品");
        assertThat(candidates.get(0).weight()).isEqualByComparingTo(new BigDecimal("28"));
        assertThat(candidates.get(1).dim()).contains("自有商城");
        assertThat(candidates.get(1).weight()).isEqualByComparingTo(new BigDecimal("12"));
    }

    @Test
    void extract_priceSubItems_takesHighestSemantics_keepsParentRow() {
        // "取最高"型价格分：子项之和 40+35 != 父行 40 → 拆分会虚增总权重，必须保留父行整项
        String markdown = """
                | 评分因素 | 评分标准 | 分值 |
                |---|---|---|
                | 价格分 | 1、平台A上架折扣率（40分）；2、平台B上架折扣率（35分）。就高原则取最高者计分 | 40 |
                """;

        TableGrid grid = TableGridExtractor.extract(markdown);
        TableColumnMapping mapping = new TableColumnMapping(-1, -1, 0, 1, 2, 1, false);
        List<ScoreCandidate> candidates = CellAnchoredScoreExtractor.extract(grid, mapping);
        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).weight()).isEqualByComparingTo(new BigDecimal("40"));
    }

    @Test
    void extract_creditEvaluationWithDiscreditRulesWithoutFenChar_preserved() {
        String markdown = """
                | 评审项目 | 评审内容 | 分值 |
                |---|---|---|
                | 诚信评价 | 不存在重大违法记录及失信行为的不扣，近三年存在失信行为的每起扣减2 | 0 |
                """;

        TableGrid grid = TableGridExtractor.extract(markdown);
        TableColumnMapping mapping = new TableColumnMapping(-1, -1, 0, 1, 2, 1, false);
        List<ScoreCandidate> candidates = CellAnchoredScoreExtractor.extract(grid, mapping);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).dim()).isEqualTo("诚信评价");
        assertThat(candidates.get(0).weight()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void extract_creditRescueWithoutAnyExplicitKeyword_preserved() {
        // 纯 isCredit 救援回归锁：无"不扣/扣减/扣除/得X分/扣X分/括号分值"。
        // 删掉救援即变红；收窄语义（旧宽口径同样保留本例）由反向用例锁定。
        String markdown = """
                | 评审项目 | 评审内容 | 分值 |
                |---|---|---|
                | 诚信评价 | 供应商存在失信行为记录的按信用评价办法处理 | 0 |
                """;

        TableGrid grid = TableGridExtractor.extract(markdown);
        TableColumnMapping mapping = new TableColumnMapping(-1, -1, 0, 1, 2, 1, false);
        List<ScoreCandidate> candidates = CellAnchoredScoreExtractor.extract(grid, mapping);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).dim()).isEqualTo("诚信评价");
    }

    @Test
    void extract_bareCreditProceduralRow_filtered() {
        // 反向 FP 锁：含信用词但无失信语义的真程序性行必须被滤除
        String markdown = """
                | 评审项目 | 评审内容 | 分值 |
                |---|---|---|
                | 信用评价程序 | 推荐成交候选供应商三名信用评价程序按规定执行 | 5 |
                """;

        TableGrid grid = TableGridExtractor.extract(markdown);
        TableColumnMapping mapping = new TableColumnMapping(-1, -1, 0, 1, 2, 1, false);
        List<ScoreCandidate> candidates = CellAnchoredScoreExtractor.extract(grid, mapping);

        assertThat(candidates).isEmpty();
    }
}
