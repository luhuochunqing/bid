package com.xiyu.bid.workflowform.application.port;

import java.util.Map;
import java.util.Optional;

public record OaAttachmentPayload(
        String fileName,
        String fileUrl,
        String storagePath,
        String contentType,
        long size
) {

    static Optional<OaAttachmentPayload> from(Object value) {
        if (value instanceof OaAttachmentPayload payload) {
            return Optional.of(payload);
        }
        if (!(value instanceof Map<?, ?> map)) {
            return Optional.empty();
        }
        String fileUrl = stringValue(map.get("fileUrl"));
        String storagePath = stringValue(map.get("storagePath"));
        if (fileUrl == null && storagePath == null) {
            return Optional.empty();
        }
        return Optional.of(new OaAttachmentPayload(
                stringValue(map.get("fileName")),
                fileUrl,
                storagePath,
                stringValue(map.get("contentType")),
                longValue(map.get("size"))
        ));
    }

    private static String stringValue(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return String.valueOf(value).trim();
    }

    private static long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }
}
