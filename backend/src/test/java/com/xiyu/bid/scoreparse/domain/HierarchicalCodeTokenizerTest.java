package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class HierarchicalCodeTokenizerTest {

    @ParameterizedTest
    @ValueSource(strings = {
            // 单数字与纯数字
            "1", "7", "12", "99",
            // 典型多层级数字
            "1.1", "1.1.1", "1.2.3.4", "3.2.1",
            // 带连字符或横杠
            "1-1", "1-2-1", "2-1",
            // 带标点或括号
            "1、", "1.", "1．", "1)", "1）",
            "(1)", "（1）", "1(1)", "1（1）", "1-1(1)",
            // 中文序号
            "一、", "二、", "三、", "（一）", "(一)", "第一条", "第二款", "条款1",
            // 罗马数字
            "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "I.", "II、", "(I)",
            // 带圈数字
            "①", "②", "③", "④", "⑤", "⑴", "⑵", "⒈",
            // 业务编码前缀
            "BIZ_1", "BIZ-1", "TECH-01", "PRICE_2", "A-1", "B.2", "A1"
    })
    @DisplayName("正向用例: 应正确识别各种形式的评分项编号")
    void shouldRecognizeValidScoringCodes(String code) {
        assertThat(HierarchicalCodeTokenizer.isScoringCode(code))
                .as("Code '%s' should be recognized as a valid scoring code", code)
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // 典型年份与日期正文
            "2024年度履约承诺", "2023年财务报表", "2025年1月",
            // 期限与年限正文
            "3年期质量保证", "5年免费维保", "1年内无安全事故",
            // 数量与家数正文
            "5家同类业绩", "3个标段", "10项专利", "100人团队", "2次违规扣分",
            // 金额与百分比正文
            "500万元注册资本", "1000万履约保函", "100元", "95分及格", "50%比例",
            // 普通无编号文字
            "评分标准", "商务评分", "技术规格偏差", "投标人资格要求",
            // 空值与空白字符
            "", "   "
    })
    @DisplayName("反向用例: 必须拦截年份、年限、数量、金额等普通描述文本，杜绝误判过拟合")
    void shouldRejectInvalidOrNonCodeTexts(String text) {
        assertThat(HierarchicalCodeTokenizer.isScoringCode(text))
                .as("Text '%s' should NOT be recognized as a scoring code", text)
                .isFalse();
    }

    @Test
    @DisplayName("边界用例: null 输入应返回 false")
    void shouldReturnFalseForNull() {
        assertThat(HierarchicalCodeTokenizer.isScoringCode(null)).isFalse();
    }
}
