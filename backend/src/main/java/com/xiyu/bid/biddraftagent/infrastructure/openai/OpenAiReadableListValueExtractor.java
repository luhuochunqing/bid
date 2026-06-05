package com.xiyu.bid.biddraftagent.infrastructure.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

final class OpenAiReadableListValueExtractor {

    private static final Set<String> METADATA_KEYS = Set.of(
            "category", "mandatory", "required", "confidence", "score", "id", "key", "code",
            "sourceExcerpt", "sourceReference", "sourceReferences", "section", "sectionId", "sectionTitle"
    );

    List<String> extract(JsonNode value) {
        List<String> values = new ArrayList<>();
        collect(values, value);
        return values;
    }

    private void collect(List<String> values, JsonNode value) {
        if (value == null || value.isNull()) {
            return;
        }
        if (value.isArray()) {
            value.elements().forEachRemaining(child -> collect(values, child));
            return;
        }
        if (value.isObject()) {
            collectObject(values, (ObjectNode) value);
            return;
        }
        if (value.isTextual()) {
            values.add(value.asText());
        }
    }

    private void collectObject(List<String> values, ObjectNode objectNode) {
        String preferred = preferredText(objectNode);
        if (preferred != null) {
            values.add(preferred);
            return;
        }
        Iterator<String> fieldNames = objectNode.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            if (METADATA_KEYS.contains(fieldName)) {
                continue;
            }
            collect(values, objectNode.get(fieldName));
        }
    }

    private String preferredText(ObjectNode objectNode) {
        String title = firstNonBlank(objectNode, "title", "name", "label", "value", "material", "requirement");
        String description = firstNonBlank(objectNode, "description");
        if (title != null && description != null) {
            return title + " / " + description;
        }
        String content = firstNonBlank(objectNode, "content", "text", "body", "summary", "description");
        if (content != null) {
            return content;
        }
        return title;
    }

    private String firstNonBlank(ObjectNode objectNode, String... candidateKeys) {
        for (String key : candidateKeys) {
            JsonNode value = objectNode.get(key);
            if (value != null && value.isTextual()) {
                String text = value.asText().trim();
                if (!text.isEmpty()) {
                    return text;
                }
            }
        }
        return null;
    }
}
