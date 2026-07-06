package com.xiyu.bid.common.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * StringListJsonConverter 单元测试。
 *
 * <p>验证 JPA AttributeConverter 的双向转换正确性。
 */
class StringListJsonConverterTest {

    private final StringListJsonConverter converter = new StringListJsonConverter();

    @Test
    void convertToDatabaseColumn_空列表_返回空数组() {
        assertThat(converter.convertToDatabaseColumn(List.of())).isEqualTo("[]");
    }

    @Test
    void convertToDatabaseColumn_null_返回空数组() {
        assertThat(converter.convertToDatabaseColumn(null)).isEqualTo("[]");
    }

    @Test
    void convertToDatabaseColumn_正常列表_返回合法JSON() {
        String json = converter.convertToDatabaseColumn(List.of("a", "b", "c"));
        assertThat(json).contains("\"a\"", "\"b\"", "\"c\"");
    }

    @Test
    void convertToEntityAttribute_合法JSON_解析正确() {
        List<String> result = converter.convertToEntityAttribute("[\"x\",\"y\"]");
        assertThat(result).containsExactly("x", "y");
    }

    @Test
    void convertToEntityAttribute_null_返回空列表() {
        assertThat(converter.convertToEntityAttribute(null)).isEmpty();
    }

    @Test
    void convertToEntityAttribute_空数组_返回空列表() {
        assertThat(converter.convertToEntityAttribute("[]")).isEmpty();
    }

    @Test
    void convertToEntityAttribute_非法JSON_返回空列表不抛异常() {
        // 历史脏数据兼容
        List<String> result = converter.convertToEntityAttribute("[不是合法JSON]");
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }

    @Test
    void roundTrip_双向转换_数据一致() {
        List<String> original = List.of("含\"引号\"", "含\n换行", "正常值");
        String json = converter.convertToDatabaseColumn(original);
        List<String> parsed = converter.convertToEntityAttribute(json);
        assertThat(parsed).containsExactlyElementsOf(original);
    }
}
