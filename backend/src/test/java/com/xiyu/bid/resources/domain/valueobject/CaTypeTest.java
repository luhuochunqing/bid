package com.xiyu.bid.resources.domain.valueobject;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CaType 枚举中文化映射单测。
 *
 * <p>验证 code ↔ label 映射正确性，以及未知/空值的兜底处理。
 */
class CaTypeTest {

    @Test
    void labelOf_knownCodes_returnChineseLabel() {
        assertThat(CaType.labelOf("ENTITY_CA")).isEqualTo("实体CA");
        assertThat(CaType.labelOf("ELECTRONIC_CA")).isEqualTo("电子CA");
    }

    @Test
    void labelOf_nullOrBlank_returnUnknown() {
        assertThat(CaType.labelOf(null)).isEqualTo("未知");
        assertThat(CaType.labelOf("")).isEqualTo("未知");
        assertThat(CaType.labelOf("  ")).isEqualTo("未知");
    }

    @Test
    void labelOf_unknownCode_returnOriginalCode() {
        // 兼容历史脏数据，原样返回不丢失信息
        assertThat(CaType.labelOf("UNKNOWN_TYPE")).isEqualTo("UNKNOWN_TYPE");
    }

    @Test
    void fromCode_knownCodes_returnEnum() {
        assertThat(CaType.fromCode("ENTITY_CA")).isEqualTo(CaType.ENTITY_CA);
        assertThat(CaType.fromCode("ELECTRONIC_CA")).isEqualTo(CaType.ELECTRONIC_CA);
    }

    @Test
    void fromCode_nullOrBlank_returnNull() {
        assertThat(CaType.fromCode(null)).isNull();
        assertThat(CaType.fromCode("")).isNull();
    }

    @Test
    void fromCode_unknownCode_returnNull() {
        assertThat(CaType.fromCode("UNKNOWN_TYPE")).isNull();
    }

    @Test
    void codeAndLabel_accessorsCorrect() {
        assertThat(CaType.ENTITY_CA.code()).isEqualTo("ENTITY_CA");
        assertThat(CaType.ENTITY_CA.label()).isEqualTo("实体CA");
        assertThat(CaType.ELECTRONIC_CA.code()).isEqualTo("ELECTRONIC_CA");
        assertThat(CaType.ELECTRONIC_CA.label()).isEqualTo("电子CA");
    }
}
