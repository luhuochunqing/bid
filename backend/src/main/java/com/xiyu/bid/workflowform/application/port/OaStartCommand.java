package com.xiyu.bid.workflowform.application.port;

import com.xiyu.bid.workflowform.domain.FormBusinessType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record OaStartCommand(
        String workflowCode,
        FormBusinessType businessType,
        Long formInstanceId,
        String applicantName,
        Map<String, Object> formData,
        String templateCode,
        Map<String, Object> mappedPayload,
        boolean trial
) {
    public OaStartCommand {
        formData = normalizeFormData(formData);
    }

    private static Map<String, Object> normalizeFormData(Map<String, Object> source) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        if (source == null) {
            return normalized;
        }
        source.forEach((key, value) -> normalized.put(key, normalizeValue(value)));
        return Collections.unmodifiableMap(normalized);
    }

    private static Object normalizeValue(Object value) {
        return OaAttachmentPayload.from(value)
                .map(payload -> (Object) payload)
                .orElseGet(() -> normalizeList(value));
    }

    private static Object normalizeList(Object value) {
        if (!(value instanceof List<?> list)) {
            return value;
        }
        return Collections.unmodifiableList(list.stream().map(OaStartCommand::normalizeValue).toList());
    }
}
