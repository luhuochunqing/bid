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
    void parse_isolatedShortCellRow_skipped() {
        // m5：孤立纯数字/超短单元格非评分条款，防假候选穿透守卫
        assertThat(ScoreTableRowParser.parse("| 10 |")).isEmpty();
        assertThat(ScoreTableRowParser.parse("| 5分 |")).isEmpty();
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

    @Test
    void parse_col3BracketWeight_extractsMainWeight() {
        // 2026-08-28 修复：列3包含括号分值（主项分值），应优先提取
        // 山焦物资案例：| 3.3(1) | 商务评分标准（56分） | 财务状况及抗风险能力（10分） | 提供2023-2025... | 1、审计意见...得4分... |
        Optional<ScoreCandidate> result = ScoreTableRowParser.parse(
                "| 3.3(1) | 商务评分标准（56分） | 财务状况及抗风险能力（10分） | 提供2023-2025年度经会计事务所... | 1、审计意见连续3年无保留意见，得4分；得3分；得2分 |");
        assertThat(result).isPresent();
        ScoreCandidate candidate = result.get();
        
        // 核心验证：weight应该是10（列3括号），而不是4（列5的maxGradeScore）
        assertThat(candidate.weight()).isEqualByComparingTo(new BigDecimal("10"));
        
        // dim应该是列3去掉括号后的文本
        assertThat(candidate.dim()).isEqualTo("财务状况及抗风险能力");
        
        // detail应该是列4+列5拼接
        assertThat(candidate.detail()).contains("提供2023-2025年度").contains("审计意见连续3年");
        
        // code应该正确提取
        assertThat(candidate.code()).isEqualTo("3.3(1)");
    }

    @Test
    void parse_col3BracketWeight_multipleRows() {
        // 测试多个不同的评分项
        
        // 运营能力及业绩（20分）
        Optional<ScoreCandidate> result1 = ScoreTableRowParser.parse(
                "| 3.3(1) | 商务评分标准（56分） | 运营能力及业绩（20分） | 投标人须提供自营电子商城运营方案... | 1、平台运营年限... |");
        assertThat(result1).isPresent();
        assertThat(result1.get().weight()).isEqualByComparingTo(new BigDecimal("20"));
        assertThat(result1.get().dim()).isEqualTo("运营能力及业绩");
        
        // 赊销额度能力（2分）
        Optional<ScoreCandidate> result2 = ScoreTableRowParser.parse(
                "| 3.3(1) | 商务评分标准（56分） | 赊销额度能力（2分） | 本项目采取账期结算方式... | 1.提供赊销额度不低于2000万元，得2分 |");
        assertThat(result2).isPresent();
        assertThat(result2.get().weight()).isEqualByComparingTo(new BigDecimal("2"));
        assertThat(result2.get().dim()).isEqualTo("赊销额度能力");
        
        // 响应文件制作（2分）
        Optional<ScoreCandidate> result3 = ScoreTableRowParser.parse(
                "| 3.3(1) | 商务评分标准（56分） | 响应文件制作（2分） | 根据响应文件内容的完整性等进行综合评分 | 响应文件编制应当准确、完整，得2分 |");
        assertThat(result3).isPresent();
        assertThat(result3.get().weight()).isEqualByComparingTo(new BigDecimal("2"));
        assertThat(result3.get().dim()).isEqualTo("响应文件制作");
    }

    @Test
    void parse_col3BracketWeightWithNewline_handlesCorrectly() {
        // 列3包含换行符的情况
        Optional<ScoreCandidate> result = ScoreTableRowParser.parse(
                "| 3.3(1) | 商务评分标准（56分） | 财务状况及抗风险能力\n（10分） | 提供审计报告 | 得10分 |");
        assertThat(result).isPresent();
        assertThat(result.get().weight()).isEqualByComparingTo(new BigDecimal("10"));
        assertThat(result.get().dim()).isEqualTo("财务状况及抗风险能力");
    }

    @Test
    void parse_noBracketWeightInCol3_fallsBackToOriginalLogic() {
        // 如果列3没有括号分值，应该回退到原有逻辑（纯分值列或maxGradeScore）
        Optional<ScoreCandidate> result = ScoreTableRowParser.parse(
                "| 不平衡报价评审 | 评标委员会针对各投标人分项报价中的单项折扣率进行评价，报价合理得4-5分 | 5 |");
        assertThat(result).isPresent();
        // 应该提取纯分值列的5
        assertThat(result.get().weight()).isEqualByComparingTo(new BigDecimal("5"));
    }

    @Test
    void parse_procedureNoticeRows_skipped() {
        // 前附表程序性条款无给分细则，必须被过滤排除，不能入池
        assertThat(ScoreTableRowParser.parse("| 1.8 | 采购预备会 | 不召开 |")).isEmpty();
        assertThat(ScoreTableRowParser.parse("| 1.9 | 分包 | 不允许 |")).isEmpty();
        assertThat(ScoreTableRowParser.parse("| 5.2 | 开启程序 | 供应商在响应截止时间前解密 |")).isEmpty();
        assertThat(ScoreTableRowParser.parse("| 7.6 | 履约保证金 | 履约金额：人民币100000元整 |")).isEmpty();
        assertThat(ScoreTableRowParser.parse("| 10.6 | “码”上监督 | 投标人若对本项目有异议，可通过扫码提出 |")).isEmpty();
        assertThat(ScoreTableRowParser.parse("| 11 | 详见供应商须知正文第11款相关规定。 | 供应商有下列情形之一的将被认定为失信行为 |")).isEmpty();
    }

    @Test
    void parse_summaryRow_skipped() {
        // 分值构成等总览说明行必须被过滤排除，不能作为评分项入池
        assertThat(ScoreTableRowParser.parse("| 3.1 | 分值构成（总分100分） | 商务评分标准56分，技术评分标准34分，价格10分 |")).isEmpty();
    }
}
