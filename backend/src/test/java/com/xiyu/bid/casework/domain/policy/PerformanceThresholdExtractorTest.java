package com.xiyu.bid.casework.domain.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PerformanceThresholdExtractor - 资格项品类-金额门槛解析（FP-Java 纯核心）")
class PerformanceThresholdExtractorTest {

    @Test
    @DisplayName("双标包双门槛：办公用品 2000 万 / 标准工业品 4500 万分句配对")
    void testExtractDualPackageThresholds() {
        String text = "供应商近三年（2023年1月1日起至响应文件递交截止日），应至少具有1项大型集团级企业电商化品类年度供应业绩："
                + "参与标包1的，单项集团层级业绩的项目服务范围应包含办公用品且单个业绩所对应的合同项下发票在2023年1月1日起至响应文件递交截止日任意一个自然年内的累计金额不低于2000万元；"
                + "参与标包2的，单项集团层级业绩的项目服务范围应包含标准工业品且单个业绩所对应的合同项下发票在2023年1月1日起至响应文件递交截止日任意一个自然年内的累计金额不低于4500万元。";

        List<PerformanceThresholdExtractor.CategoryThreshold> thresholds = PerformanceThresholdExtractor.extract(text);

        assertThat(thresholds).extracting(PerformanceThresholdExtractor.CategoryThreshold::category)
                .contains("办公用品", "标准工业品");
        assertThat(amountOf(thresholds, "办公用品")).isEqualByComparingTo(new BigDecimal("2000"));
        assertThat(amountOf(thresholds, "标准工业品")).isEqualByComparingTo(new BigDecimal("4500"));
    }

    @Test
    @DisplayName("金额单位归一：亿元/万元/千元/元 混写均换算为万元")
    void testAmountUnitNormalization() {
        assertThat(amountOf(PerformanceThresholdExtractor.extract(
                "办公用品年度供应业绩累计金额不低于0.2亿元。"), "办公用品"))
                .isEqualByComparingTo(new BigDecimal("2000"));
        assertThat(amountOf(PerformanceThresholdExtractor.extract(
                "标准工业品年度供应业绩累计金额不低于4,500万元。"), "标准工业品"))
                .isEqualByComparingTo(new BigDecimal("4500"));
        assertThat(amountOf(PerformanceThresholdExtractor.extract(
                "办公用品年度供应业绩累计金额不低于300千元。"), "办公用品"))
                .isEqualByComparingTo(new BigDecimal("30"));
        assertThat(amountOf(PerformanceThresholdExtractor.extract(
                "标准工业品年度供应业绩累计金额不低于500000元。"), "标准工业品"))
                .isEqualByComparingTo(new BigDecimal("50"));
    }

    @Test
    @DisplayName("同分句多品类共享一个金额时，每个品类均获得该门槛")
    void testSharedAmountAcrossCategories() {
        String text = "投标人须具有办公用品及工业品年度供应业绩，累计金额不低于3000万元。";

        List<PerformanceThresholdExtractor.CategoryThreshold> thresholds = PerformanceThresholdExtractor.extract(text);

        assertThat(amountOf(thresholds, "办公用品")).isEqualByComparingTo(new BigDecimal("3000"));
        assertThat(amountOf(thresholds, "标准工业品")).isEqualByComparingTo(new BigDecimal("3000"));
    }

    @Test
    @DisplayName("金额在品类之前出现时仍能配对（就近向前匹配）")
    void testAmountBeforeCategory() {
        String text = "累计金额不低于2000万元的办公用品类供货业绩。";

        List<PerformanceThresholdExtractor.CategoryThreshold> thresholds = PerformanceThresholdExtractor.extract(text);

        assertThat(amountOf(thresholds, "办公用品")).isEqualByComparingTo(new BigDecimal("2000"));
    }

    @Test
    @DisplayName("只有品类没有金额时不产出门槛；与业绩无关的资格文本返回空")
    void testNoThresholdWithoutAmountAndIrrelevantText() {
        assertThat(PerformanceThresholdExtractor.extract("投标人须具备办公用品供货业绩，提供合同复印件。")).isEmpty();
        assertThat(PerformanceThresholdExtractor.extract("具有独立承担民事责任的能力（提供营业执照）；具有良好的商业信誉。")).isEmpty();
        assertThat(PerformanceThresholdExtractor.extract(null)).isEmpty();
        assertThat(PerformanceThresholdExtractor.extract("   ")).isEmpty();
    }

    @Test
    @DisplayName("同分句先枚举品类再逐项写金额：金额按最近品类归属，不得跨品类串台")
    void testCategoryEnumeratedBeforeAmounts() {
        String text = "项目服务范围包含办公用品和标准工业品，其中标准工业品累计金额不低于4500万元，办公用品累计金额不低于2000万元。";

        List<PerformanceThresholdExtractor.CategoryThreshold> thresholds = PerformanceThresholdExtractor.extract(text);

        assertThat(amountOf(thresholds, "办公用品")).isEqualByComparingTo(new BigDecimal("2000"));
        assertThat(amountOf(thresholds, "标准工业品")).isEqualByComparingTo(new BigDecimal("4500"));
    }

    @Test
    @DisplayName("金额在前品类在后：金额归属最近品类")
    void testAmountBeforeEnumeratedCategories() {
        String text = "金额不低于2000万元的办公用品业绩和金额不低于4500万元的标准工业品业绩。";

        List<PerformanceThresholdExtractor.CategoryThreshold> thresholds = PerformanceThresholdExtractor.extract(text);

        assertThat(amountOf(thresholds, "办公用品")).isEqualByComparingTo(new BigDecimal("2000"));
        assertThat(amountOf(thresholds, "标准工业品")).isEqualByComparingTo(new BigDecimal("4500"));
    }

    @Test
    @DisplayName("同分句同品类多金额取最大值（单笔低于门槛但累计达标不得被误杀）")
    void testMultipleAmountsForSameCategoryTakeMax() {
        String text = "为大型集团提供办公用品年度供应，单笔订单金额50万元，年度累计发票金额2600万元。";

        List<PerformanceThresholdExtractor.CategoryThreshold> thresholds = PerformanceThresholdExtractor.extract(text);

        assertThat(amountOf(thresholds, "办公用品")).isEqualByComparingTo(new BigDecimal("2600"));
    }

    @Test
    @DisplayName("区间金额取下界；上限修饰（不超过/以内）不计入业绩门槛")
    void testAmountRangeLowerBoundAndUpperBoundSkipped() {
        assertThat(amountOf(PerformanceThresholdExtractor.extract("办公用品累计金额2000-4500万元。"), "办公用品"))
                .isEqualByComparingTo(new BigDecimal("2000"));
        assertThat(PerformanceThresholdExtractor.extract("办公用品年度供应业绩发票累计金额不超过2000万元。"))
                .isEmpty();
    }

    @Test
    @DisplayName("品类与金额跨句分列（品类句。金额句。）仍能配对出门槛")
    void testCrossSentenceCategoryAndAmountPairing() {
        String text = "供应商近三年应具有办公用品年度供应业绩。累计发票金额不低于2000万元。";

        assertThat(amountOf(PerformanceThresholdExtractor.extract(text), "办公用品"))
                .isEqualByComparingTo(new BigDecimal("2000"));
    }

    private static BigDecimal amountOf(List<PerformanceThresholdExtractor.CategoryThreshold> thresholds, String category) {
        List<BigDecimal> amounts = amountsOf(thresholds, category);
        assertThat(amounts).as("category %s should have threshold", category).isNotEmpty();
        return amounts.get(0);
    }

    private static List<BigDecimal> amountsOf(List<PerformanceThresholdExtractor.CategoryThreshold> thresholds, String category) {
        return thresholds.stream()
                .filter(t -> t.category().equals(category))
                .map(PerformanceThresholdExtractor.CategoryThreshold::amountWan)
                .toList();
    }
}
