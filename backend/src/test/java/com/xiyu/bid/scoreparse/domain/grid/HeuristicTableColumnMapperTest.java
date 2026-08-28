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
}
