package com.xiyu.bid.common.display;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * Jackson {@link JsonSerializer}，将 {@link DisplayableEnum} 序列化为
 * {@code {"name":"ENUM_VALUE","displayName":"中文标签"}} 格式。
 *
 * <p>用法：在 DTO 字段上添加
 * {@code @JsonSerialize(using = EnumPairSerializer.class)}，即可输出
 * {@code status: {name: "SATISFIED", displayName: "已满足"}}。</p>
 *
 * <p>此序列化器为「opt-in」模式——只影响显式标注 {@code @JsonSerialize} 的字段，
 * 其他枚举字段的序列化行为不变（仍为枚举 name 字符串）。</p>
 *
 * @see DisplayableEnum
 */
public class EnumPairSerializer extends JsonSerializer<DisplayableEnum> {

    @Override
    public void serialize(DisplayableEnum value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeStartObject();
        gen.writeStringField("name", ((Enum<?>) value).name());
        gen.writeStringField("displayName", value.getDisplayName());
        gen.writeEndObject();
    }
}
