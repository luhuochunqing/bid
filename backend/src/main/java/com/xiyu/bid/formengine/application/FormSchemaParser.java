// Input: 表单定义 ID + JSON schema
// Output: 字段列表
// Pos: Application 层（Orchestration，无业务规则）
// 维护声明: 仅做 JSON 反序列化，业务规则下沉 domain.
package com.xiyu.bid.formengine.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.workflowform.domain.FormFieldDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 表单 Schema JSON 解析器。
 */
@Slf4j
@Component
public class FormSchemaParser {

    private final ObjectMapper objectMapper;

    public FormSchemaParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @SuppressWarnings("unchecked")
    public List<FormFieldDefinition> parseFields(String schemaJson) {
        if (schemaJson == null || schemaJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode fields = readSchemaObject(schemaJson).path("fields");
            if (!fields.isArray()) {
                return List.of();
            }
            return objectMapper.convertValue(fields, new TypeReference<List<Map<String, Object>>>() {}).stream()
                    .map(FormFieldDefinition::fromMap)
                    .toList();
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse form schema JSON: {}", e.getMessage());
            return List.of();
        }
    }

    public Optional<String> getScopeLabel(String schemaJson) {
        if (schemaJson == null || schemaJson.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode label = readSchemaObject(schemaJson).path("label");
            return label.isTextual() ? Optional.of(label.asText()) : Optional.empty();
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse scope label from schema JSON: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private JsonNode readSchemaObject(String schemaJson) throws JsonProcessingException {
        JsonNode schema = objectMapper.readTree(schemaJson);
        if (schema.isTextual()) {
            schema = objectMapper.readTree(schema.asText());
        }
        return schema;
    }
}
