package com.xiyu.bid.common.util;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.List;

/**
 * JPA AttributeConverter: List<String> ↔ JSON 数组字符串。
 *
 * <p>用于 columnDefinition = "JSON" 的字段，让 Entity 字段直接声明为 List<String>，
 * JPA 自动处理序列化/反序列化。业务代码完全不需要关心 JSON 转换细节。
 *
 * <p>使用方式：
 * <pre>{@code
 *   @Column(name = "platforms_json", columnDefinition = "JSON")
 *   @Convert(converter = StringListJsonConverter.class)
 *   private List<String> platforms;
 * }</pre>
 *
 * <p>降级策略：
 * <ul>
 *   <li>序列化失败：返回 "[]"，保证不抛异常中断业务</li>
 *   <li>反序列化失败：返回空列表，兼容历史脏数据</li>
 * </ul>
 */
@Converter
public class StringListJsonConverter implements AttributeConverter<List<String>, String> {

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        return JsonUtils.toStringListJson(attribute);
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        return JsonUtils.fromStringListJson(dbData);
    }
}
