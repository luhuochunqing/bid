package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreCategoryClassifierTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "商务", "技术", "价格", "报价", "综合", "评议",
            "商务分", "技术分", "价格分", "报价分",
            "商务部分", "技术部分", "价格部分", "报价部分",
            "综合评审", "商务评议", "技术评议",
            "商务（30分）", "技术(40分)", "价格 (30分)",
            "business", "technical", "price", "commercial"
    })
    @DisplayName("常见顶层大类判定保持既有语义")
    void recognizesTopLevelCategories(String category) {
        assertThat(ScoreCategoryClassifier.isTopLevelCategory(category)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"商务评分", "技术评分", "综合评分", "商务评分（20", "评分项", "评分项 21", "1", "2.1"})
    void titleFallbackDoesNotTurnUnusableLabelsIntoSourceCategories(String label) {
        assertThat(ScoreCategoryClassifier.needsSpecificTitle(label)).isTrue();
        assertThat(ScoreCategoryClassifier.isTopLevelCategory(label)).isFalse();
    }

    @Test
    @DisplayName("空名称、纯序号、未闭合括号、竖线分隔符与占位符禁止作为有效落库名称")
    void invalidOrPlaceholderNameDetection() {
        for (String invalid : java.util.List.of("", "   ", "1", "2.1", "一", "二", "二十一", "商务评分（20", "商务（未闭合", "3 | 营业总收入", "评分项", "评分项 21")) {
            assertThat(ScoreCategoryClassifier.isInvalidOrPlaceholderName(invalid))
                    .as("expected invalid name: %s", invalid).isTrue();
        }
        assertThat(ScoreCategoryClassifier.isInvalidOrPlaceholderName("营业总收入")).isFalse();
        assertThat(ScoreCategoryClassifier.isInvalidOrPlaceholderName("营业总收入（2024年）")).isFalse();
    }

    @Test
    @DisplayName("空字符串或 null 视为 top-level category（需要从 detail 抽取细则）")
    void nullOrBlankResolvesToTrue() {
        assertThat(ScoreCategoryClassifier.isTopLevelCategory(null)).isTrue();
        assertThat(ScoreCategoryClassifier.isTopLevelCategory("")).isTrue();
        assertThat(ScoreCategoryClassifier.isTopLevelCategory("   ")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "能够提供最长账期",
            "交货期限承诺",
            "企业财务状况",
            "仓储能力要求",
            "售后服务响应时间",
            "技术方案与实施路径"
    })
    @DisplayName("具体评分细则标题不能被误判为 top-level category")
    void recognizesLeafTitles(String title) {
        assertThat(ScoreCategoryClassifier.isTopLevelCategory(title)).isFalse();
    }
}
