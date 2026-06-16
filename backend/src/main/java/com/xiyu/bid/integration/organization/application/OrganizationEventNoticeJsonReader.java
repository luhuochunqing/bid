package com.xiyu.bid.integration.organization.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.integration.organization.domain.OrganizationEventNoticeFields;
import com.xiyu.bid.integration.organization.domain.OrganizationEventNoticeParseResult;
import com.xiyu.bid.integration.organization.domain.OrganizationEventNoticeParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrganizationEventNoticeJsonReader {
    private final ObjectMapper objectMapper;

    public OrganizationEventNoticeParseResult parse(String rawPayload) {
        if (rawPayload == null || rawPayload.isBlank()) {
            return OrganizationEventNoticeParseResult.invalid("事件通知内容不能为空");
        }
        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            JsonNode data = root.path("data");
            return OrganizationEventNoticeParser.parse(new OrganizationEventNoticeFields(
                    text(root, "traceId"),
                    text(root, "spanId"),
                    text(root, "parentId"),
                    text(root, "eventSource"),
                    text(root, "eventTopic"),
                    text(root, "time"),
                    text(root, "key"),
                    text(data, "deptId"),
                    text(data, "userId"),
                    text(data, "jobId")
            ));
        } catch (JsonProcessingException ex) {
            return OrganizationEventNoticeParseResult.invalid("事件通知JSON格式错误");
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isValueNode() && !value.isNull() ? value.asText().trim() : "";
    }
}
