package com.xiyu.bid.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson ObjectMapper 全局配置。
 *
 * <h3>枚举序列化规范</h3>
 * <ul>
 *   <li>序列化：枚举默认使用 {@code toString()} 输出，每个枚举应覆写
 *       {@code toString()} 返回前端期望的值，或为 getter 标注 {@code @JsonValue}。</li>
 *   <li>反序列化：同时接受 {@code name()} 和 {@code toString()} 形式，不区分大小写。</li>
 * </ul>
 *
 * <h3>背景</h3>
 * <ul>
 *   <li>CO-180：英文枚举值泄漏到前端（Jackson 默认序列化使用 name()）</li>
 *   <li>CO-157：状态值大小写不匹配（前端发 lowercase，后端期望 UPPER_CASE）</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer enumSerializationCustomizer() {
        return builder -> {
            builder.featuresToEnable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING);
            builder.featuresToEnable(DeserializationFeature.READ_ENUMS_USING_TO_STRING);
        };
    }
}
