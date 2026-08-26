// Input: ScoreTableBlockLocator（locate 方法）
// Output: 评分语义表格块定位行为验证（spec 055 US2 / FR-5 召回五前置）
// Pos: Test/scoreparse/domain

package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreTableBlockLocatorTest {

    private static final String SCORE_TABLE = """
            | 评分项 | 评审内容 | 分值 |
            | --- | --- | --- |
            | 不平衡报价评审 | 评标委员会针对各投标人分项报价中的单项折扣率进行评价，报价合理得4-5分 | 5 |
            | 上架商品价格管控方案 | 重点阐述商品定价策略、价格动态监控，优秀得4分，未提供不得分 | 4 |
            | 供货商无息账期 | 投标人承诺每增加15天得2分，最多得4分 | 4 |
            """;

    private static final String PLAIN_TABLE = """
            | 联系人 | 电话 |
            | --- | --- |
            | 王占俊 | 13800000000 |
            """;

    @Test
    void locate_scoreTableBlock_returnsDataRowsWithoutSeparator() {
        List<ScoreTableBlockLocator.TableBlock> blocks =
                ScoreTableBlockLocator.locate("前言文本\n" + SCORE_TABLE + "\n后记文本");
        assertThat(blocks).hasSize(1);
        List<String> rows = blocks.get(0).rows();
        assertThat(rows).hasSize(3);
        assertThat(rows.get(0)).contains("不平衡报价评审");
        assertThat(rows).noneMatch(row -> row.replace(" ", "").matches("^\\|[-:|\\s]+\\|$"));
    }

    @Test
    void locate_plainTableWithoutScoreSemantics_skipped() {
        List<ScoreTableBlockLocator.TableBlock> blocks =
                ScoreTableBlockLocator.locate("联系表\n" + PLAIN_TABLE + "\n");
        assertThat(blocks).isEmpty();
    }

    @Test
    void locate_twoScoreTables_bothReturned() {
        String second = """
                | 评审标准 | 得分 |
                | --- | --- |
                | 方案完整性优得3分 | 3 |
                """;
        List<ScoreTableBlockLocator.TableBlock> blocks =
                ScoreTableBlockLocator.locate(SCORE_TABLE + "\n中间段落\n" + second);
        assertThat(blocks).hasSize(2);
        assertThat(blocks.get(0).rows()).hasSize(3);
        assertThat(blocks.get(1).rows()).hasSize(1);
    }

    @Test
    void locate_noTable_returnsEmpty() {
        assertThat(ScoreTableBlockLocator.locate("纯文本没有表格")).isEmpty();
        assertThat(ScoreTableBlockLocator.locate(null)).isEmpty();
        assertThat(ScoreTableBlockLocator.locate("")).isEmpty();
    }

    @Test
    void locate_scoreKeywordOnlyInHeader_stillMatched() {
        String headerOnly = """
                | 评分项 | 分值 |
                | --- | --- |
                | 货物质量保障 | 10 |
                """;
        List<ScoreTableBlockLocator.TableBlock> blocks =
                ScoreTableBlockLocator.locate(headerOnly);
        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0).rows()).hasSize(1);
    }
}
