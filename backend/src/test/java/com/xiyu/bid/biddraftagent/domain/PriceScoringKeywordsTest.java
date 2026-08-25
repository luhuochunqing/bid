// Input: PriceScoringKeywords 常量与正则模式
// Output: 边界断言；prompt 同步性断言归位于 ScoreParsePromptsTest
// Pos: biddraftagent/domain — 回归：中心化后短 token 误杀与嵌套词重复计数
package com.xiyu.bid.biddraftagent.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PriceScoringKeywordsTest {

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
        // 带序号的参数行（行尾 % 值）也须判参数行，防误读为权重 80 的评分项
        assertThat(PriceScoringKeywords.PRICE_PARAM_ROW.matcher("2 BP% 80%").find()).isTrue();
        assertThat(PriceScoringKeywords.PRICE_PARAM_ROW.matcher("1 下浮率 3%").find()).isTrue();
        // 行尾无 % 值时不判参数行：保护 "1 价格分计算方法 30" 类报价主项行不被误杀；
        // 以 % 书写权重的主项行（"… 30%"）会命中此分支，由提取器维度词元守卫兼容保留
        assertThat(PriceScoringKeywords.PRICE_PARAM_ROW.matcher("1 价格分计算方法 30").find()).isFalse();
        assertThat(PriceScoringKeywords.PRICE_PARAM_ROW.matcher("2 基准价计算方法 30").find()).isFalse();
        // 负向锚定（防后续"顺手收紧/放宽"引入误杀）：序号顿号后无空白、% 后带备注均不命中；
        // 带序号短词行不判（短词歧义仍守"短词+后接数字"边界）
        assertThat(PriceScoringKeywords.PRICE_PARAM_ROW.matcher("1、下浮率 3%").find()).isFalse();
        assertThat(PriceScoringKeywords.PRICE_PARAM_ROW.matcher("2 下浮率 3% 备注").find()).isFalse();
        assertThat(PriceScoringKeywords.PRICE_PARAM_ROW.matcher("2 L1 3").find()).isFalse();
    }

    @Test
    void priceParams_containsNewToken折让率_example() {
        String all = PriceScoringKeywords.PRICE_PARAMS;
        assertThat(all).contains("下浮率");
        assertThat(all).contains("BP%");
        assertThat(all).contains("L1");
    }

    @Test
    void countDistinctLongParams_nestedKeywordCountedOnce() {
        // 嵌套关键词（基准价 ⊂ 评标基准价）只计 1 个信号，防止单一词虚增置信度
        assertThat(PriceScoringKeywords.countDistinctLongParams("评标基准价计算方法")).isEqualTo(1);
        assertThat(PriceScoringKeywords.countDistinctLongParams("下浮率与基准价")).isEqualTo(2);
        // 短词（L1/RH）不计入长词统计，孤立块兜底不会因 "L1级" 类噪声放行
        assertThat(PriceScoringKeywords.countDistinctLongParams("L1级资质要求")).isZero();
        assertThat(PriceScoringKeywords.countDistinctLongParams(null)).isZero();
    }
}
