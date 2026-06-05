package com.xiyu.bid.biddraftagent.infrastructure.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

@Component
class OpenAiJsonObjectPayloadReader {

    private final ObjectMapper strictMapper;
    private final ObjectMapper lenientMapper;
    private final ObjectMapper bindingMapper;
    private final OpenAiReadableListValueExtractor readableListValueExtractor;

    OpenAiJsonObjectPayloadReader(ObjectMapper objectMapper) {
        this.strictMapper = objectMapper;
        this.lenientMapper = objectMapper.copy()
                .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature())
                .enable(JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER.mappedFeature())
                .enable(JsonReadFeature.ALLOW_TRAILING_COMMA.mappedFeature());
        this.bindingMapper = objectMapper.copy()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.readableListValueExtractor = new OpenAiReadableListValueExtractor();
    }

    <T> T read(String content, Class<T> responseType) throws JsonProcessingException {
        JsonNode payload = normalizeForResponseType(
                parsePayload(stripMarkdownFences(content), 0),
                responseType
        );
        return bindingMapper.treeToValue(payload, responseType);
    }

    private JsonNode parsePayload(String content, int depth) throws JsonProcessingException {
        if (depth > 2) {
            throw new JsonProcessingException("AI json_object payload nesting exceeded supported depth") {
            };
        }
        JsonNode directNode = readTree(content);
        if (directNode != null) {
            return unwrapTextNode(directNode, depth);
        }

        String extractedObject = extractTopLevelObject(content);
        if (extractedObject != null && !extractedObject.equals(content)) {
            JsonNode extractedNode = readTree(extractedObject);
            if (extractedNode != null) {
                return unwrapTextNode(extractedNode, depth);
            }
        }

        throw new JsonProcessingException("AI json_object payload is not a parseable JSON object") {
        };
    }

    private JsonNode unwrapTextNode(JsonNode node, int depth) throws JsonProcessingException {
        if (node.isTextual()) {
            return parsePayload(stripMarkdownFences(node.textValue()), depth + 1);
        }
        return node;
    }

    private JsonNode readTree(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        try {
            return strictMapper.readTree(content);
        } catch (JsonProcessingException ignored) {
            return readTreeLeniently(content);
        }
    }

    private JsonNode readTreeLeniently(String content) {
        try {
            return lenientMapper.readTree(content);
        } catch (JsonProcessingException ignored) {
            return null;
        }
    }

    private String stripMarkdownFences(String content) {
        String trimmed = content == null ? "" : content.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstNewLine = trimmed.indexOf('\n');
        int lastFence = trimmed.lastIndexOf("```");
        if (firstNewLine >= 0 && lastFence > firstNewLine) {
            return trimmed.substring(firstNewLine + 1, lastFence).trim();
        }
        return trimmed;
    }

    private String extractTopLevelObject(String content) {
        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        int start = -1;
        for (int index = 0; index < content.length(); index++) {
            char current = content.charAt(index);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\') {
                escaped = true;
                continue;
            }
            if (current == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (current == '{') {
                if (depth == 0) {
                    start = index;
                }
                depth++;
                continue;
            }
            if (current == '}') {
                if (depth == 0) {
                    continue;
                }
                depth--;
                if (depth == 0 && start >= 0) {
                    return content.substring(start, index + 1);
                }
            }
        }
        return null;
    }

    private JsonNode normalizeForResponseType(JsonNode payload, Class<?> responseType) {
        if (!(payload instanceof ObjectNode objectNode)) {
            return payload;
        }
        normalizeAliases(objectNode, responseType);
        for (Field field : responseType.getFields()) {
            JsonNode value = objectNode.get(field.getName());
            if (value == null) {
                continue;
            }
            if (field.getType().equals(String.class) && !value.isTextual() && !value.isNull()) {
                objectNode.put(field.getName(), stringifyValue(value));
                continue;
            }
            if (!isListField(field)) {
                continue;
            }
            Class<?> elementType = listElementType(field);
            if (String.class.equals(elementType) || !value.isArray()) {
                objectNode.set(field.getName(), normalizeListValue(value, elementType));
            }
        }
        return objectNode;
    }

    private void normalizeAliases(ObjectNode objectNode, Class<?> responseType) {
        if (!responseType.equals(TenderRequirementOutput.class)) {
            return;
        }
        if (!objectNode.has("requirementItems") && objectNode.has("items")) {
            objectNode.set("requirementItems", objectNode.get("items"));
        }
        objectNode.remove("items");
    }

    private boolean isListField(Field field) {
        return field.getType().equals(java.util.List.class);
    }

    private Class<?> listElementType(Field field) {
        Type genericType = field.getGenericType();
        if (genericType instanceof ParameterizedType parameterizedType) {
            Type typeArgument = parameterizedType.getActualTypeArguments()[0];
            if (typeArgument instanceof Class<?> elementClass) {
                return elementClass;
            }
        }
        return Object.class;
    }

    private ArrayNode normalizeListValue(JsonNode value, Class<?> elementType) {
        ArrayNode arrayNode = strictMapper.createArrayNode();
        appendArrayValues(arrayNode, value, elementType);
        return arrayNode;
    }

    private void appendArrayValues(ArrayNode arrayNode, JsonNode value, Class<?> elementType) {
        if (value == null || value.isNull()) {
            return;
        }
        if (value.isArray()) {
            value.elements().forEachRemaining(child -> appendArrayValues(arrayNode, child, elementType));
            return;
        }
        if (String.class.equals(elementType)) {
            appendStringValues(arrayNode, value);
            return;
        }
        if (value.isObject()) {
            value.elements().forEachRemaining(arrayNode::add);
            return;
        }
        arrayNode.add(value);
    }

    private void appendStringValues(ArrayNode arrayNode, JsonNode value) {
        readableListValueExtractor.extract(value).forEach(arrayNode::add);
    }

    private String stringifyValue(JsonNode value) {
        List<String> parts = new ArrayList<>();
        collectStringParts(parts, value);
        return String.join("\n", parts).trim();
    }

    private void collectStringParts(List<String> parts, JsonNode value) {
        if (value == null || value.isNull()) {
            return;
        }
        if (value.isArray()) {
            value.elements().forEachRemaining(child -> collectStringParts(parts, child));
            return;
        }
        if (value.isObject()) {
            if (value.hasNonNull("content")) {
                parts.add(value.get("content").asText());
                return;
            }
            if (value.hasNonNull("title") && value.hasNonNull("description")) {
                parts.add(value.get("title").asText() + " / " + value.get("description").asText());
                return;
            }
            value.elements().forEachRemaining(child -> collectStringParts(parts, child));
            return;
        }
        parts.add(value.asText());
    }
}
