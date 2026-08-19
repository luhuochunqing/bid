// Input: Various Chinese region strings and variants
// Output: Assertions on standard province / municipality extractions
// Pos: Test/区域维度提取器单测
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.analytics.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("区域维度省份提取器测试 — RegionDimensionExtractor")
class RegionDimensionExtractorTest {

    @ParameterizedTest(name = "输入 [{0}] 应提取省份 [{1}]")
    @CsvSource(textBlock = """
            北京市北京市,        北京市
            北京市海淀区,        北京市
            北京,                北京市
            天津市和平区,        天津市
            上海市浦东新区,      上海市
            重庆市渝中区,        重庆市
            香港特别行政区中西区, 香港特别行政区
            香港,                香港特别行政区
            澳门特别行政区花地玛, 澳门特别行政区
            澳门,                澳门特别行政区
            台湾省台北市,        台湾省
            台湾,                台湾省
            江苏省淮安市清江浦区, 江苏省
            江苏省南京市,        江苏省
            广东省深圳市南山区,  广东省
            浙江省杭州市,        浙江省
            山东省济南市,        山东省
            四川省成都市,        四川省
            内蒙古自治区呼和浩特市, 内蒙古自治区
            新疆维吾尔自治区乌鲁木齐市, 新疆维吾尔自治区
            西藏自治区拉萨市,    西藏自治区
            宁夏回族自治区银川市, 宁夏回族自治区
            广西壮族自治区南宁市, 广西壮族自治区
            江苏,                江苏省
            江苏淮安,            江苏省
            内蒙古,              内蒙古自治区
            内蒙古呼和浩特市,    内蒙古自治区
            广西,                广西壮族自治区
            西藏,                西藏自治区
            新疆,                新疆维吾尔自治区
            河北,                河北省
            黑龙江,              黑龙江省
            陕西,                陕西省
            吉林市,              吉林省
            海南,                海南省
            海南省海口市,        海南省
            """)
    void testExtractProvince(String input, String expected) {
        assertThat(RegionDimensionExtractor.extractProvince(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("空或空白字符串应返回其他")
    void testExtractProvinceEmpty(String input) {
        assertThat(RegionDimensionExtractor.extractProvince(input)).isEqualTo("其他");
    }

    @Test
    @DisplayName("无法识别省后缀时返回原始文本")
    void testExtractProvinceFallback() {
        assertThat(RegionDimensionExtractor.extractProvince("华东大区")).isEqualTo("华东大区");
        assertThat(RegionDimensionExtractor.extractProvince("海外地区")).isEqualTo("海外地区");
    }

    @Test
    @DisplayName("前缀歧义 key：青海海南藏族自治州/海南州不归入海南省，保持独立桶")
    void testAmbiguousShortNameKeepsOwnBucket() {
        assertThat(RegionDimensionExtractor.extractProvince("海南藏族自治州")).isEqualTo("海南藏族自治州");
        assertThat(RegionDimensionExtractor.extractProvince("海南州")).isEqualTo("海南州");
        assertThat(RegionDimensionExtractor.extractProvince("海南藏族自治州共和县")).isEqualTo("海南藏族自治州共和县");
        // 精确短省名仍归一化
        assertThat(RegionDimensionExtractor.extractProvince("海南")).isEqualTo("海南省");
    }

    @Test
    @DisplayName("别名映射全量遍历：每个短省名 key 均归一化为对应全称（防手写映射拼写逃逸）")
    @SuppressWarnings("unchecked")
    void testAllShortNameAliasesNormalized() throws Exception {
        var field = RegionDimensionExtractor.class.getDeclaredField("SHORT_NAME_ALIASES");
        field.setAccessible(true);
        java.util.Map<String, String> aliases = (java.util.Map<String, String>) field.get(null);

        assertThat(aliases).hasSize(34);
        aliases.forEach((shortName, fullName) ->
                assertThat(RegionDimensionExtractor.extractProvince(shortName))
                        .as("短省名 [%s] 应归一化为 [%s]", shortName, fullName)
                        .isEqualTo(fullName));
    }

    @Test
    @DisplayName("双源取值口径：总部所在地非空白优先，空白回退标讯区域，均空白返回 null")
    void testResolveRawLocation() {
        assertThat(RegionDimensionExtractor.resolveRawLocation("江苏省淮安市", "浙江省杭州市"))
                .isEqualTo("江苏省淮安市");
        assertThat(RegionDimensionExtractor.resolveRawLocation(null, "浙江省杭州市"))
                .isEqualTo("浙江省杭州市");
        assertThat(RegionDimensionExtractor.resolveRawLocation("", "浙江省杭州市"))
                .isEqualTo("浙江省杭州市");
        assertThat(RegionDimensionExtractor.resolveRawLocation("   ", "浙江省杭州市"))
                .isEqualTo("浙江省杭州市");
        assertThat(RegionDimensionExtractor.resolveRawLocation(null, null)).isNull();
        assertThat(RegionDimensionExtractor.resolveRawLocation("", "")).isNull();
        assertThat(RegionDimensionExtractor.resolveRawLocation(" 江苏省淮安市 ", null))
                .isEqualTo("江苏省淮安市");
    }
}
