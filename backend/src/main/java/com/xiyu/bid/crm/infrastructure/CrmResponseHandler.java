package com.xiyu.bid.crm.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CrmResponseHandler {

    private static final Logger log = LoggerFactory.getLogger(CrmResponseHandler.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private CrmResponseHandler() {
    }

    public static CrmApiResponse parse(String body) {
        try {
            JsonNode root = mapper.readTree(body);
            int code = root.path("code").asInt(-1);
            String msg = root.path("msg").asText("");
            JsonNode data = root.path("data");
            boolean success = root.path("success").asBoolean(code == 0);
            return new CrmApiResponse(code, msg, data, success);
        } catch (RuntimeException | java.io.IOException e) {
            log.error("Failed to parse CRM response: {}", body, e);
            return CrmApiResponse.parseError("Failed to parse response: " + e.getMessage());
        }
    }

    public record CrmApiResponse(int code, String msg, JsonNode data, boolean success) {
        public static CrmApiResponse parseError(String message) {
            return new CrmApiResponse(-1, message, null, false);
        }

        public boolean isUnauthorized() {
            return code == 401;
        }

        public boolean isServerError() {
            return code >= 500 && code < 600;
        }

        public boolean isClientError() {
            return code >= 400 && code < 500;
        }
    }
}
