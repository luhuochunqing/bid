// Input: Map<String, Object> 自定义字段值（scope 命名空间两级结构）/ 列存 JSON String
// Output: toJson / fromJson / filterScopes / replaceScope — 失败降级（log.warn + NULL/空 Map，Constitution VII）
// Pos: project/service/ - 自定义字段 JSON 编解码（ObjectMapper 构造注入，禁止 new）
package com.xiyu.bid.project.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class CustomFieldsCodec {

    /** CO-601 项目主表 scope 键。 */
    public static final String SCOPE_BASIC = "project.basic";
    /** CO-601 项目详情 scope 键。 */
    public static final String SCOPE_DETAIL = "project.detail";
    /** CO-601 立项表单 scope 键。 */
    public static final String SCOPE_INITIATION = "project.initiation";
    /** projects.custom_fields 列允许的一级 scope 键（创建链路过滤依据，契约 §1）。 */
    public static final Set<String> PROJECT_TABLE_SCOPES = Set.of(SCOPE_BASIC, SCOPE_DETAIL);

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
        // 先尝试直接解析为 Map（MySQL JSON 列 / 普通 TEXT 列的正常路径）
        try {
            Map<String, Object> result = objectMapper.readValue(json, MAP_TYPE);
            return result != null ? result : Collections.emptyMap();
        } catch (JsonProcessingException ignored) {
            // 继续尝试双重编码剥离
        }
        // H2 JSON 列双重编码兜底：H2 把 JSON 字符串值再编码为 JSON 字符串
        // 例如 {"a":1} 被存储为 "{\"a\":1}"，读取时需先取 textual 再解析为 Map
        // 纯字符串（非 JSON 对象）仍降级为空 Map，保持 fromJson_nonObjectJson_degradesToEmptyMap 契约
        try {
            var node = objectMapper.readTree(json);
            if (node.isTextual()) {
                String inner = node.asText();
                if (inner != null && !inner.isBlank() && inner.trim().startsWith("{")) {
                    Map<String, Object> result = objectMapper.readValue(inner, MAP_TYPE);
                    return result != null ? result : Collections.emptyMap();
                }
            }
        } catch (JsonProcessingException ignored) {
            // 继续走降级
        }
        log.warn("Custom fields JSON parse failed, fallback to empty map: {}", json);
        return Collections.emptyMap();
    }

    /**
     * 过滤非法 scope 一级键（契约 §1：未知 scope 丢弃 + log.warn，不阻断主流程）。
     * null / 空 Map 原样透传。
     */
    public Map<String, Object> filterScopes(Map<String, Object> customFields, Set<String> allowedScopes) {
        if (customFields == null || customFields.isEmpty()) {
            return customFields;
        }
        Map<String, Object> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : customFields.entrySet()) {
            if (allowedScopes.contains(entry.getKey())) {
                filtered.put(entry.getKey(), entry.getValue());
            } else {
                log.warn("Dropping unknown scope key '{}' from custom fields", entry.getKey());
            }
        }
        return filtered;
    }

    /**
     * 按 scope 键整体替换（契约 §2：不触碰其他 scope 键）。
     * newValues 为 null / 空 Map → 移除该 scope 键；非 Map 脏值 → 保留原值不动（防御）。
     */
    public Map<String, Object> replaceScope(
            Map<String, Object> existingFields, String scope, Object newValues) {
        Map<String, Object> updated = new LinkedHashMap<>();
        if (existingFields != null) {
            updated.putAll(existingFields);
        }
        if (newValues instanceof Map<?, ?> newMap) {
            if (newMap.isEmpty()) {
                updated.remove(scope);
            } else {
                updated.put(scope, newMap);
            }
        } else if (newValues == null) {
            updated.remove(scope);
        }
        return updated;
    }
}
