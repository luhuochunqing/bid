package com.xiyu.bid.casework.domain.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeCaseFeaturesTest {

    @Test
    @DisplayName("质量分按去除首尾空白后的长度计算，空正文为零")
    void qualityScoreUsesStrippedLength() {
        assertThat(KnowledgeCaseFeatures.qualityScore(null)).isZero();
        assertThat(KnowledgeCaseFeatures.qualityScore(" \n\t ")).isZero();
        assertThat(KnowledgeCaseFeatures.qualityScore(" " + "甲".repeat(119) + "\n")).isZero();
        assertThat(KnowledgeCaseFeatures.qualityScore(" " + "甲".repeat(120) + "\n")).isEqualTo(60);
    }

    @Test
    @DisplayName("不足 120 字质量分为 0，标题对不上子主题时记 OTHER")
    void shortUnrelatedTextScoresZeroAndOther() {
        KnowledgeCaseFeatures.Assigned assigned = KnowledgeCaseFeatures.assign("销售额", "平台工业品销售额低于 1 亿元不得分。");

        assertThat(assigned.qualityScore()).isZero();
        assertThat(assigned.subTopic()).isEqualTo("OTHER");
    }

    @Test
    @DisplayName("标题命中仓储配送，结构与数字承诺使充实正文质量分达到 85")
    void logisticsTitleAndRichBody() {
        String body = """
                一、总体保障方案
                我司承诺为本项目提供 7x24 小时全天候技术保障，全国设立 30 个本地备件库。
                1. 响应时间：30分钟内响应，2小时内到达现场排查。
                2. 故障恢复：一般故障 4 小时内恢复，重大故障 8 小时内排除并提供应急备机。
                3. 日常巡检：每月提供一次系统健康体检报告。
                """;

        KnowledgeCaseFeatures.Assigned assigned = KnowledgeCaseFeatures.assign("仓储配送方案", body);

        assertThat(assigned.subTopic()).isEqualTo("LOGISTICS_WAREHOUSE");
        assertThat(assigned.qualityScore()).isEqualTo(85);
    }

    @ParameterizedTest
    @CsvSource({"119, 0", "120, 60", "299, 60", "300, 65", "599, 65", "600, 70", "1499, 70", "1500, 75"})
    @DisplayName("无结构或数字承诺的正文按字数阶梯计分")
    void qualityScoreLengthThresholds(int length, int expected) {
        assertThat(KnowledgeCaseFeatures.qualityScore("甲".repeat(length))).isEqualTo(expected);
    }

    @Test
    @DisplayName("只有正文命中至少两个关键词时才脱离 OTHER")
    void bodyNeedsTwoKeywordHits() {
        KnowledgeCaseFeatures.Assigned weak = KnowledgeCaseFeatures.assign("服务方案", "本文只提到物流配送，没有第二个词。");
        KnowledgeCaseFeatures.Assigned strong = KnowledgeCaseFeatures.assign(
                "服务方案", "自有仓储管理，并给出物流配送和最后一公里安排。");

        assertThat(weak.subTopic()).isEqualTo("OTHER");
        assertThat(strong.subTopic()).isEqualTo("LOGISTICS_WAREHOUSE");
    }
}
