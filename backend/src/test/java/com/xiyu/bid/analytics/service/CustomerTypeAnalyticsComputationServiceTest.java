// Input: Customer type strings and variants
// Output: Normalized customer category assertions
// Pos: Test/客户类型标准化与聚合计算单测
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.analytics.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("客户类型分析计算服务测试 — 标准化归一与模糊匹配")
class CustomerTypeAnalyticsComputationServiceTest {

    private final CustomerTypeAnalyticsComputationService service = new CustomerTypeAnalyticsComputationService();

    @ParameterizedTest(name = "输入 [{0}] 应归一为 [{1}]")
    @CsvSource(textBlock = """
            政府机关/事业单位/高校, 政府机关/事业单位/高校
            政府机关,             政府机关/事业单位/高校
            事业单位,             政府机关/事业单位/高校
            高校,                 政府机关/事业单位/高校
            市公安局等机关单位,   政府机关/事业单位/高校
            GOVERNMENT,           政府机关/事业单位/高校
            GOVERNMENT_INSTITUTION, 政府机关/事业单位/高校
            央企,                 央企
            中央企业,             央企
            国家电网（央企直属）, 央企
            CENTRAL_SOE,          央企
            STATE_OWNED,          央企
            地方国企,             地方国企
            省属国企,             地方国企
            国有独资企业,         地方国企
            LOCAL_SOE,            地方国企
            民企,                 民企
            民营企业,             民企
            私营股份公司民企,     民企
            PRIVATE,              民企
            PRIVATE_ENTERPRISE,   民企
            港澳台及外企,         港澳台及外企
            外企,                 港澳台及外企
            外资独资,             港澳台及外企
            中外合资,             港澳台及外企
            港资企业,             港澳台及外企
            FOREIGN,              港澳台及外企
            FOREIGN_HK_MACAO_TW,  港澳台及外企
            """)
    void testNormalizeCustomerType(String input, String expected) {
        assertThat(service.normalizeCustomerType(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "未知类型", "个体工商户", "个人"})
    @DisplayName("非标准或空输入应返回 null")
    void testNormalizeCustomerTypeInvalid(String input) {
        assertThat(service.normalizeCustomerType(input)).isNull();
    }
}
