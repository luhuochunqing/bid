package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TimelineReasonablenessPolicy 单元测试.
 */
class TimelineReasonablenesspolicyTest {

    private static DocumentContent pageDoc(final String text) {
        return new DocumentContent(
                "test.pdf",
                "hash",
                text,
                List.of(new DocumentContent.PageContent(1, text, List.of())));
    }

    // ===== 开竣工日期倒挂 =====

    @Test
    void invertedDates_startAfterEnd_returnFail() {
        String text = """
                施工进度计划
                计划开工日期：2025年6月1日
                计划竣工日期：2025年3月1日
                """;
        var result = TimelineReasonablenessPolicy.checkTimeline(pageDoc(text), null);
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.conclusion()).contains("倒挂");
    }

    @Test
    void validDates_startBeforeEnd_returnsNull() {
        String text = """
                施工进度计划
                计划开工日期：2025年3月1日
                计划竣工日期：2025年9月1日
                工期：184日历天
                """;
        var result = TimelineReasonablenessPolicy.checkTimeline(pageDoc(text), null);
        // 无明显异常，移交 LLM 或 null
        assertThat(result == null
                || result.status() == QualityCheckStatus.PASS
                || result.status() == QualityCheckStatus.MANUAL_REVIEW).isTrue();
    }

    // ===== 自述工期超限 =====

    @Test
    void statedDurationExceedsMax_returnFail() {
        String text = """
                施工进度计划
                总工期：300日历天
                工期保证措施：xxxx
                """;
        var result = TimelineReasonablenessPolicy.checkTimeline(pageDoc(text), 180);
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.conclusion()).contains("超出招标要求");
    }

    @Test
    void statedDurationWithinMax_returnsNullOrPass() {
        String text = """
                施工进度计划
                总工期：150日历天
                工期保证措施：xxxx
                """;
        var result = TimelineReasonablenessPolicy.checkTimeline(pageDoc(text), 180);
        // 在限内无异常，返回 null 或 PASS
        assertThat(result == null
                || result.status() == QualityCheckStatus.PASS
                || result.status() == QualityCheckStatus.MANUAL_REVIEW).isTrue();
    }

    // ===== 开竣工跨度超限 =====

    @Test
    void spanExceedsMax_returnFail() {
        // 跨度 365 天，限 180 天
        String text = """
                施工进度计划
                横道图
                计划开工日期：2025年1月1日
                计划竣工日期：2026年1月1日
                """;
        var result = TimelineReasonablenessPolicy.checkTimeline(pageDoc(text), 180);
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.conclusion()).contains("超出招标最大工期");
    }

    // ===== 横道图占位模板未填 =====

    @Test
    void ganttPlaceholder_returnFail() {
        String text = """
                施工进度计划
                横道图________（待填）
                里程碑节点：待确认
                """;
        var result = TimelineReasonablenessPolicy.checkTimeline(pageDoc(text), null);
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.conclusion()).contains("占位模板");
    }

    // ===== 无进度计划关键字 =====

    @Test
    void noScheduleKeywords_returnsNull() {
        String text = "投标函\n项目名称：某某工程\n投标总价：100万元";
        var result = TimelineReasonablenessPolicy.checkTimeline(pageDoc(text), null);
        assertThat(result).isNull();
    }


    @Test
    void nullDoc_returnsNull() {
        var result = TimelineReasonablenessPolicy.checkTimeline(null, null);
        assertThat(result).isNull();
    }

    // ===== toOrdinal 辅助方法 =====

    @Test
    void toOrdinal_correctOrder() {
        int earlier = TimelineReasonablenessPolicy.toOrdinal(2024, 1, 1);
        int later = TimelineReasonablenessPolicy.toOrdinal(2024, 12, 31);
        assertThat(earlier).isLessThan(later);
    }

    @Test
    void toOrdinal_sameDate_equal() {
        int a = TimelineReasonablenessPolicy.toOrdinal(2025, 6, 15);
        int b = TimelineReasonablenessPolicy.toOrdinal(2025, 6, 15);
        assertThat(a).isEqualTo(b);
    }

    // ===== 工期推算差异预警（开竣工跨度与自述天数不一致超10天） =====

    @Test
    void durationMismatchWarning_returnManualReview() {
        // 跨度 ~90 天，自述工期 60 天，差距 >10 天
        String text = """
                施工进度计划
                计划开工日期：2025年1月1日
                计划竣工日期：2025年4月1日
                总工期：60日历天
                """;
        var result = TimelineReasonablenessPolicy.checkTimeline(pageDoc(text), 200);
        // 工期推算差异，应返回 MANUAL_REVIEW 或 null（不 FAIL）
        assertThat(result == null
                || result.status() == QualityCheckStatus.MANUAL_REVIEW).isTrue();
    }
}
