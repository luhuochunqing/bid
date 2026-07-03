package com.xiyu.bid.integration.external;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * {@link XiyuContactOverride} 单元测试。
 *
 * <p>验证 CRM 商机负责人优先策略：用 projectManagerName 覆盖 CRM 推送的 XIYU_CONTACT 字段。
 * 防复发：tender 931 bug 的回归测试。
 */
class XiyuContactOverrideTest {

    @Test
    @DisplayName("XIYU_CONTACT 字段应被 projectManagerName 覆盖")
    void apply_withXiyuContact_shouldOverride() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("roleKey", "DECISION_MAKER");
        row.put("XIYU_CONTACT", "张頔");
        List<Map<String, Object>> customerInfos = new ArrayList<>(List.of(row));

        XiyuContactOverride.apply(customerInfos, "南自婷");

        assertThat(row.get("XIYU_CONTACT")).isEqualTo("南自婷");
        assertThat(row.get("roleKey")).isEqualTo("DECISION_MAKER");
    }

    @Test
    @DisplayName("多行 customerInfos 应全部覆盖 XIYU_CONTACT")
    void apply_multipleRows_shouldOverrideAll() {
        Map<String, Object> row1 = new LinkedHashMap<>();
        row1.put("roleKey", "DECISION_MAKER");
        row1.put("XIYU_CONTACT", "张頔");
        Map<String, Object> row2 = new LinkedHashMap<>();
        row2.put("roleKey", "CONTACT_PERSON");
        row2.put("XIYU_CONTACT", "李四");
        List<Map<String, Object>> customerInfos = new ArrayList<>(List.of(row1, row2));

        XiyuContactOverride.apply(customerInfos, "南自婷");

        assertThat(row1.get("XIYU_CONTACT")).isEqualTo("南自婷");
        assertThat(row2.get("XIYU_CONTACT")).isEqualTo("南自婷");
    }

    @Test
    @DisplayName("projectManagerName 为 null 时不应覆盖（保留 CRM 原值）")
    void apply_nullProjectManagerName_shouldNotOverride() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("XIYU_CONTACT", "张頔");
        List<Map<String, Object>> customerInfos = new ArrayList<>(List.of(row));

        XiyuContactOverride.apply(customerInfos, null);

        assertThat(row.get("XIYU_CONTACT")).isEqualTo("张頔");
    }

    @Test
    @DisplayName("projectManagerName 为空字符串时不应覆盖（保留 CRM 原值）")
    void apply_blankProjectManagerName_shouldNotOverride() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("XIYU_CONTACT", "张頔");
        List<Map<String, Object>> customerInfos = new ArrayList<>(List.of(row));

        XiyuContactOverride.apply(customerInfos, "   ");

        assertThat(row.get("XIYU_CONTACT")).isEqualTo("张頔");
    }

    @Test
    @DisplayName("customerInfos 为 null 时不应抛异常")
    void apply_nullCustomerInfos_shouldNotThrow() {
        assertThatCode(() -> XiyuContactOverride.apply(null, "南自婷"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("没有 XIYU_CONTACT 字段的行不应被修改")
    void apply_noXiyuContactKey_shouldNotModify() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("roleKey", "DECISION_MAKER");
        row.put("NAME", "客户A");
        row.put("CONTACT_INFO", "13800138000");
        List<Map<String, Object>> customerInfos = new ArrayList<>(List.of(row));

        XiyuContactOverride.apply(customerInfos, "南自婷");

        assertThat(row).hasSize(3);
        assertThat(row.get("NAME")).isEqualTo("客户A");
        assertThat(row.get("CONTACT_INFO")).isEqualTo("13800138000");
        assertThat(row).doesNotContainKey("XIYU_CONTACT");
    }

    @Test
    @DisplayName("混合行：部分有 XIYU_CONTACT 部分没有，应只覆盖存在的")
    void apply_mixedRows_shouldOverrideOnlyExisting() {
        Map<String, Object> row1 = new LinkedHashMap<>();
        row1.put("roleKey", "DECISION_MAKER");
        row1.put("XIYU_CONTACT", "张頔");
        Map<String, Object> row2 = new LinkedHashMap<>();
        row2.put("roleKey", "CONTACT_PERSON");
        row2.put("NAME", "联系人B");
        List<Map<String, Object>> customerInfos = new ArrayList<>(List.of(row1, row2));

        XiyuContactOverride.apply(customerInfos, "南自婷");

        assertThat(row1.get("XIYU_CONTACT")).isEqualTo("南自婷");
        assertThat(row2).doesNotContainKey("XIYU_CONTACT");
        assertThat(row2.get("NAME")).isEqualTo("联系人B");
    }

    @Test
    @DisplayName("空 customerInfos 列表不应抛异常")
    void apply_emptyCustomerInfos_shouldNotThrow() {
        List<Map<String, Object>> customerInfos = new ArrayList<>();

        XiyuContactOverride.apply(customerInfos, "南自婷");

        assertThat(customerInfos).isEmpty();
    }

    @Test
    @DisplayName("包含 null 行的列表应跳过 null 行不抛异常")
    void apply_listWithNullRow_shouldSkipNullRow() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("XIYU_CONTACT", "张頔");
        List<Map<String, Object>> customerInfos = new ArrayList<>();
        customerInfos.add(null);
        customerInfos.add(row);
        customerInfos.add(null);

        XiyuContactOverride.apply(customerInfos, "南自婷");

        assertThat(row.get("XIYU_CONTACT")).isEqualTo("南自婷");
    }
}
