// Input: Map<String, Object> 自定义字段值（scope 命名空间两级结构）/ 列存 JSON String
// Output: toJson / fromJson — 失败降级（log.warn + NULL/空 Map，Constitution VII）
// Pos: project/service/ - 自定义字段 JSON 编解码（ObjectMapper 构造注入，禁止 new）
package com.xiyu.bid.project.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class CustomFieldsCodec {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public String toJson(Map<String, Object> customFields) {
        if (customFields == null || customFields.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(customFields);
        } catch (JsonProcessingException ex) {
            log.warn("Custom fields serialize failed, storing NULL: {}", ex.getMessage());
            return null;
        }
    }

    public Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> result = objectMapper.readValue(json, MAP_TYPE);
            return result != null ? result : Collections.emptyMap();
        } catch (JsonProcessingException ex) {
            log.warn("Custom fields JSON parse failed, fallback to empty map: {}", ex.getMessage());
            return Collections.emptyMap();
        }
    }
}
