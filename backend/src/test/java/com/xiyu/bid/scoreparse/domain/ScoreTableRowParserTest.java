// Input: ScoreTableRowParser（parse 方法）
// Output: 表格行→候选解析行为验证（spec 055 US2 / FR-5）
// Pos: Test/scoreparse/domain

package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreTableRowParserTest {

    @Test
    void parse_rowWithPureWeightColumn_extractsDetailAndWeight() {
        Optional<ScoreCandidate> result = ScoreTableRowParser.parse(
                "| 不平衡报价评审 | 评标委员会针对各投标人分项报价中的单项折扣率进行评价，报价合理得4-5分 | 5 |");
        assertThat(result).isPresent();
        ScoreCandidate candidate = result.get();
        assertThat(candidate.code()).isEmpty();
        assertThat(candidate.detail()).contains("单项折扣率").doesNotContain("|");
        assertThat(candidate.weight()).isEqualByComparingTo(new BigDecimal("5"));
        assertThat(candidate.sourceText()).isEqualTo(
                "| 不平衡报价评审 | 评标委员会针对各投标人分项报价中的单项折扣率进行评价，报价合理得4-5分 | 5 |");
    }

    @Test
    void parse_rowWithLeadingCodeColumn_extractsCode() {
        Optional<ScoreCandidate> result = ScoreTableRowParser.parse(
                "| 2.2.1 | 自营商城订单销量 | 横向比较各供应商自营商城近三年销售金额情况 | 6 |");
        assertThat(result).isPresent();
        assertThat(result.get().code()).isEqualTo("2.2.1");
        assertThat(result.get().detail()).contains("近三年销售金额");
        assertThat(result.get().weight()).isEqualByComparingTo(new BigDecimal("6"));
    }

    @Test
    void parse_rowWithoutPureWeightColumn_takesMaxGradeScore() {
        // 无纯分值列，评审标准内含档位给分："优秀得4分，良好得2-3分" → 满分取最大 4
        Optional<ScoreCandidate> result = ScoreTableRowParser.parse(
                "| 上架商品价格管控方案 | 重点阐述商品定价策略、价格动态监控。优秀得4分，良好得2-3分，一般得1分，未提供不得分 |");
        assertThat(result).isPresent();
        assertThat(result.get().weight()).isEqualByComparingTo(new BigDecimal("4"));
        assertThat(result.get().detail()).contains("商品定价策略");
    }

    @Test
    void parse_rowWithCategoryColumn_extractsDim() {
        // 合并单元格产生的短大类名列："技术方案"是 dim 的最佳来源，非 detail 的一部分
        Optional<ScoreCandidate> result = ScoreTableRowParser.parse(
                "| 技术方案 |  | 方案完整可行得10分 |  |");
        assertThat(result).isPresent();
        assertThat(result.get().dim()).isEqualTo("技术方案");
        assertThat(result.get().detail()).contains("方案完整可行得10分");
        assertThat(result.get().weight()).isEqualByComparingTo(new BigDecimal("10"));
    }

    @Test
    void parse_singleColumnRow_fallsBackToWholeRow() {
        // 单列行（无可识别列语义）→ 整行原文降级（不丢行、不编造列语义）
        Optional<ScoreCandidate> result = ScoreTableRowParser.parse(
                "| 技术方案（含方案完整可行得10分） |");
        assertThat(result).isPresent();
        assertThat(result.get().detail()).isEqualTo("技术方案（含方案完整可行得10分）");
        assertThat(result.get().weight()).isEqualByComparingTo(new BigDecimal("10"));
    }

    @Test
    void parse_rowWithItemNameColumn_extractsDim() {
        // "不平衡报价评审"是评分项名称列 → dim
        Optional<ScoreCandidate> result = ScoreTableRowParser.parse(
                "| 不平衡报价评审 | 评标委员会针对各投标人分项报价中的单项折扣率进行评价，报价合理得4-5分 | 5 |");
        assertThat(result).isPresent();
        assertThat(result.get().dim()).isEqualTo("不平衡报价评审");
    }

    @Test
    void parse_separatorOrBlankRow_skipped() {
        assertThat(ScoreTableRowParser.parse("| --- | --- | --- |")).isEmpty();
        assertThat(ScoreTableRowParser.parse("|   |   |")).isEmpty();
        assertThat(ScoreTableRowParser.parse(null)).isEmpty();
        assertThat(ScoreTableRowParser.parse("")).isEmpty();
    }

    @Test
    void parse_rowWithoutAnyScoreNumber_weightNullButRowKept() {
        Optional<ScoreCandidate> result = ScoreTableRowParser.parse(
                "| 评分项说明 | 投标人须自行提供相关承诺 |");
        assertThat(result).isPresent();
        assertThat(result.get().weight()).isNull();
        assertThat(result.get().detail()).contains("相关承诺");
    }
}
