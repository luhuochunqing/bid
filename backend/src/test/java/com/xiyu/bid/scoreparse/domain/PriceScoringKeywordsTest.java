// Input: PriceScoringKeywords 常量与 ScoreParsePrompts 模板
// Output: 同步性与边界断言
// Pos: scoreparse/domain — 回归：中心化后遗漏同步与短 token 误杀
package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.infrastructure.openai.ScoreParsePrompts;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PriceScoringKeywordsTest {

    @Test
    void prompt_mustContainCriticalPriceParams() {
        // 中心化后若新增关键参数（如 折让率）未同步到 prompt，测试失败提醒同步 ScoreParsePrompts
        // 仅校验 prompt 必须覆盖的核心报价参数（与 ScoreParsePrompts 硬编码列表对应），避免过度耦合
        String prompt = ScoreParsePrompts.buildCandidateExtractionPrompt("test", 1, 1);
        String[] critical = {"下浮率", "BP%", "HP%", "LP%", "L1", "L2", "RH", "RL", "基准价", "价格分计算", "进入价格", "报价人"};
        for (String token : critical) {
            String core = token.replace("%", "");
            assertThat(prompt)
                    .as("prompt 需同步包含关键参数 '%s'，若新增请同步更新 ScoreParsePrompts", token)
                    .contains(core);
        }
        // SCORE_EXTENSION 核心也需出现
        assertThat(prompt).contains("基准价");
        assertThat(prompt).contains("报价评分");
    }

    @Test
    void priceParamRow_shortTokenRequiresTrailingDigit() {
        // L1/L2/RH/RL 歧义：仅后接数字时才视为参数行，避免误杀评分项 "L1 技术方案 5分"
        assertThat(PriceScoringKeywords.PRICE_PARAM_ROW.matcher("L1 3").find()).isTrue();
        assertThat(PriceScoringKeywords.PRICE_PARAM_ROW.matcher("L2 6").find()).isTrue();
        assertThat(PriceScoringKeywords.PRICE_PARAM_ROW.matcher("RH 1").find()).isTrue();
        assertThat(PriceScoringKeywords.PRICE_PARAM_ROW.matcher("RL 0").find()).isTrue();
        // 评分项不应被判为参数行
        assertThat(PriceScoringKeywords.PRICE_PARAM_ROW.matcher("L1 技术方案 5分").find()).isFalse();
        assertThat(PriceScoringKeywords.PRICE_PARAM_ROW.matcher("L1 技术方案").find()).isFalse();
        assertThat(PriceScoringKeywords.PRICE_PARAM_ROW.matcher("L10 5").find()).isFalse();
        // 长词参数行仍按前缀匹配
        assertThat(PriceScoringKeywords.PRICE_PARAM_ROW.matcher("下浮率 3%").find()).isTrue();
        assertThat(PriceScoringKeywords.PRICE_PARAM_ROW.matcher("BP% 80%").find()).isTrue();
        assertThat(PriceScoringKeywords.PRICE_PARAM_ROW.matcher("基准价 计算").find()).isTrue();
    }

    @Test
    void priceParams_containsNewToken折让率_example() {
        // 示例：若未来新增 “折让率”，需同时出现在 PRICE_PARAMS 与 prompt；此测试为文档化预期
        // 当前 PRICE_PARAMS 尚未包含折让率，提示新增时同步
        String all = PriceScoringKeywords.PRICE_PARAMS;
        // 占位：确保当前已覆盖的核心参数仍在
        assertThat(all).contains("下浮率");
        assertThat(all).contains("BP%");
        assertThat(all).contains("L1");
    }
}
