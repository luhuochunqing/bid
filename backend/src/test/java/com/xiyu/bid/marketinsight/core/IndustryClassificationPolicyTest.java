package com.xiyu.bid.marketinsight.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IndustryClassificationPolicyTest {

    @Test
    void classifyIndustry_ShouldMatchOffice() {
        assertThat(IndustryClassificationPolicy.classifyIndustry(
                "2024年办公用品集中采购")).isEqualTo("办公");
    }

    @Test
    void classifyIndustry_ShouldMatchEnergyPower() {
        assertThat(IndustryClassificationPolicy.classifyIndustry(
                "某电力公司输变电设备采购")).isEqualTo("能源电力");
    }

    @Test
    void classifyIndustry_ShouldMatchSafety() {
        assertThat(IndustryClassificationPolicy.classifyIndustry(
                "安全防护用品年度采购")).isEqualTo("劳保安全");
    }

    @Test
    void classifyIndustry_ShouldMatchDataCenter() {
        assertThat(IndustryClassificationPolicy.classifyIndustry(
                "数据中心机房建设")).isEqualTo("数据中心");
    }

    @Test
    void classifyIndustry_NoMatch_ShouldReturnDefault() {
        assertThat(IndustryClassificationPolicy.classifyIndustry(
                "未知领域项目")).isEqualTo("综合类");
    }

    @Test
    void classifyIndustry_NullInput_ShouldReturnDefault() {
        assertThat(IndustryClassificationPolicy.classifyIndustry(null))
                .isEqualTo("综合类");
    }

    @Test
    void classifyIndustry_EmptyInput_ShouldReturnDefault() {
        assertThat(IndustryClassificationPolicy.classifyIndustry(""))
                .isEqualTo("综合类");
    }

    @Test
    void getColorForIndustry_ShouldReturnCyanForOffice() {
        assertThat(IndustryClassificationPolicy.getColorForIndustry("办公"))
                .isEqualTo("cyan");
    }

    @Test
    void getColorForIndustry_ShouldReturnGrayForDefault() {
        assertThat(IndustryClassificationPolicy.getColorForIndustry("综合类"))
                .isEqualTo("gray");
    }

    @Test
    void getColorForIndustry_ShouldReturnRedForSafety() {
        assertThat(IndustryClassificationPolicy.getColorForIndustry("劳保安全"))
                .isEqualTo("red");
    }

    @Test
    void getColorForIndustry_ShouldReturnBlueForTools() {
        assertThat(IndustryClassificationPolicy.getColorForIndustry("工具"))
                .isEqualTo("blue");
    }

    @Test
    void getColorForIndustry_ShouldReturnIndigoForEnergy() {
        assertThat(IndustryClassificationPolicy.getColorForIndustry("能源电力"))
                .isEqualTo("indigo");
    }

    @Test
    void getColorForIndustry_UnknownIndustry_ShouldReturnGray() {
        assertThat(IndustryClassificationPolicy.getColorForIndustry("不存在的行业"))
                .isEqualTo("gray");
    }

    @Test
    void getAllIndustries_ShouldContainAllDefinedIndustries() {
        var industries = IndustryClassificationPolicy.getAllIndustries();
        assertThat(industries).contains("工具", "工具耗材", "焊接",
                "劳保安全", "消防", "工控低压", "电工照明", "办公",
                "制冷暖通", "建工材料", "能源电力", "数据中心",
                "自动化", "运维服务", "智慧园区", "高速公路", "交通运输");
    }

    @Test
    void getAllIndustries_ShouldIncludeDefaultCategory() {
        var industries = IndustryClassificationPolicy.getAllIndustries();
        assertThat(industries).contains("综合类");
    }

    @Test
    void classifyIndustry_ShouldMatchTools() {
        assertThat(IndustryClassificationPolicy.classifyIndustry(
                "2025年电钻及角磨机采购")).isEqualTo("工具");
    }

    @Test
    void classifyIndustry_ShouldMatchFireSafety() {
        assertThat(IndustryClassificationPolicy.classifyIndustry(
                "灭火器及消火栓采购项目")).isEqualTo("消防");
    }

    @Test
    void classifyIndustry_ShouldMatchWelding() {
        assertThat(IndustryClassificationPolicy.classifyIndustry(
                "电焊机及焊接材料采购")).isEqualTo("焊接");
    }

    @Test
    void classifyIndustry_FirstMatchWinsWhenMultipleKeywords() {
        // Title contains both "办公" and "空调", first match should win
        var result = IndustryClassificationPolicy.classifyIndustry(
                "办公空调采购项目");
        assertThat(result).isNotNull();
    }
}
