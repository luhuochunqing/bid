package com.xiyu.bid.scoreparse.domain.grid;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HeuristicTableColumnMapperTest {

    @Test
    void infer_standardFourColumnTable_mappedCorrectly() {
        String markdown = """
                | 序号 | 评分因素 | 评分标准 | 分值 |
                |---|---|---|---|
                | 1 | 资质等级 | 甲级得10分 | 10 |
                | 2 | 业绩要求 | 业绩丰富得20分 | 20 |
                """;
        TableGrid grid = TableGridExtractor.extract(markdown);
        TableColumnMapping mapping = HeuristicTableColumnMapper.infer(grid);

        assertThat(mapping.codeCol()).isEqualTo(0);
        assertThat(mapping.dimCol()).isEqualTo(1);
        assertThat(mapping.detailCol()).isEqualTo(2);
        assertThat(mapping.weightCol()).isEqualTo(3);
        assertThat(mapping.isPercentage()).isFalse();
    }

    @Test
    void infer_threeColumnPercentTable_mappedCorrectly() {
        String markdown = """
                | 评分因素 | 评分细则 | 权重 |
                |---|---|---|
                | 响应完整性 | 无缺项得100% | 4% |
                | 财务状况 | 资产良好 | 6% |
                """;
        TableGrid grid = TableGridExtractor.extract(markdown);
        TableColumnMapping mapping = HeuristicTableColumnMapper.infer(grid);

        assertThat(mapping.codeCol()).isEqualTo(-1);
        assertThat(mapping.dimCol()).isEqualTo(0);
        assertThat(mapping.detailCol()).isEqualTo(1);
        assertThat(mapping.weightCol()).isEqualTo(2);
        assertThat(mapping.isPercentage()).isTrue();
    }

    @Test
    void infer_parentDimensionTable_mappedCorrectly() {
        String markdown = """
                | 评审大类 | 评分项目 | 打分标准 | 满分 |
                |---|---|---|---|
                | 商务部分 | 财务 | 优秀得10分 | 10 |
                """;
        TableGrid grid = TableGridExtractor.extract(markdown);
        TableColumnMapping mapping = HeuristicTableColumnMapper.infer(grid);

        assertThat(mapping.parentDimCol()).isEqualTo(0);
        assertThat(mapping.dimCol()).isEqualTo(1);
        assertThat(mapping.detailCol()).isEqualTo(2);
        assertThat(mapping.weightCol()).isEqualTo(3);
    }

    @Test
    void infer_contractAssessmentTable_rejected() {
        String markdown = """
                | 序号 | 考核项目 | 考核内容 | 分值 |
                |---|---|---|---|
                | 1 | 周期保证 | 满足周期要求 | 25 |
                | 2 | 质量保证 | 满足质量要求 | 25 |
                """;
        TableGrid grid = TableGridExtractor.extract(markdown);
        TableColumnMapping mapping = HeuristicTableColumnMapper.infer(grid);

        assertThat(mapping.headerRows()).isEqualTo(grid.rowCount());
    }

    @Test
    void infer_project1359Table2Schema_mappedCorrectly() {
        String markdown = """
                | 项目 | 评审要素 | 评审内容 | 最低分 | 最高分 |
                |---|---|---|---|---|
                | 一、诚信评价 | 公共信用信息报告（0分） | 不存在失信行为不扣分 | -10 | 0 |
                | 二、财务状况（25分） | 财务综合评价（25） | 优良 | 15 | 25 |
                """;
        TableGrid grid = TableGridExtractor.extract(markdown);
        TableColumnMapping mapping = HeuristicTableColumnMapper.infer(grid);

        assertThat(mapping.parentDimCol()).isEqualTo(0);
        assertThat(mapping.dimCol()).isEqualTo(1);
        assertThat(mapping.detailCol()).isEqualTo(2);
        assertThat(mapping.weightCol()).isEqualTo(4);
    }

    @Test
    void infer_project1359Table3Schema_bracketNotOverwritingExplicitWeight() {
        String markdown = """
                | 评审项目 | 评审内容 | 最低分 | 最高分 |
                |---|---|---|---|
                | 应答人交易平台与履约交易平台对接方案（6分） | 对应答人对接方案进行评价 | 2 | 6 |
                | 供货业绩（22分） | 对应答人供货业绩进行评价 | 3 | 7 |
                """;
        TableGrid grid = TableGridExtractor.extract(markdown);
        TableColumnMapping mapping = HeuristicTableColumnMapper.infer(grid);

        assertThat(mapping.dimCol()).isEqualTo(0);
        assertThat(mapping.detailCol()).isEqualTo(1);
        assertThat(mapping.weightCol()).isEqualTo(3);
    }

    @Test
    void infer_packageLotWithExplicitScore_notRejected() {
        String markdown = """
                | 标段划分 | 评分因素 | 评分标准 | 分值 |
                |---|---|---|---|
                | 包1 | 业绩能力 | 业绩满分得10分 | 10 |
                """;
        TableGrid grid = TableGridExtractor.extract(markdown);
        TableColumnMapping mapping = HeuristicTableColumnMapper.infer(grid);

        assertThat(mapping.headerRows()).isEqualTo(1);
        assertThat(mapping.weightCol()).isEqualTo(3);
    }

    @Test
    void infer_packageLotWithoutExplicitScore_rejected() {
        String markdown = """
                | 标段划分 | 包名称 | 采购范围 |
                |---|---|---|
                | 标包1 | 办公耗材 | 采购A类物资 |
                """;
        TableGrid grid = TableGridExtractor.extract(markdown);
        TableColumnMapping mapping = HeuristicTableColumnMapper.infer(grid);

        assertThat(mapping.headerRows()).isEqualTo(grid.rowCount());
    }

    @Test
    void infer_bannerRowAboveHeader_mapsGenuineHeaderRowCorrectly() {
        String markdown = """
                | 总分100分，其中，商务部分：70分，技术部分：30分 | | | |
                |---|---|---|---|
                | 项目 | 评审因素 | 分值（分） | 评审标准 |
                | 商务部分（70分） | 体系管理 | 8 | 投标人具备有效期内ISO9001认证得2分 |
                """;
        TableGrid grid = TableGridExtractor.extract(markdown);
        TableColumnMapping mapping = HeuristicTableColumnMapper.infer(grid);

        assertThat(mapping.headerRows()).isEqualTo(2);
        assertThat(mapping.parentDimCol()).isEqualTo(0);
        assertThat(mapping.dimCol()).isEqualTo(1);
        assertThat(mapping.weightCol()).isEqualTo(2);
        assertThat(mapping.detailCol()).isEqualTo(3);
    }
}

