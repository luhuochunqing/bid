package com.xiyu.bid.integration.organization.infrastructure.sdk;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ehsy.eventlibrary.clientsdk.common.anno.AcceptEvent;
import com.ehsy.eventlibrary.clientsdk.systeminteraction.result.EventResult;
import com.xiyu.bid.integration.organization.application.OrganizationEventAppService;
import java.util.Iterator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * SDK event consumer that bridges the EHSY {@code ClientSDK @AcceptEvent} annotations
 * to the internal {@link OrganizationEventAppService}.
 *
 * <p>Requires {@code XIYU_ORG_EVENT_SDK_ENABLED=true} and the EHSY ClientSDK jar on the classpath.
 *
 * <p>Return type is {@link EventResult} with {@code code} and {@code msg} fields.
 * The SDK serializes the return value to JSON and checks {@code code} against
 * {@code ResultStatusEnum.SUCCESS} ({@code "200"}).
 *
 * <p>SDK message normalization: The SDK delivers events in a format that wraps
 * tracing fields inside {@code eventTrackInfo} and places data fields at the root
 * level. This adapter normalizes that format into the documented event structure
 * before passing to the internal parser.
 *
 * @see OrganizationEventSdkResponseMapper
 */
@Component
@ConditionalOnClass(name = "com.ehsy.eventlibrary.clientsdk.common.anno.AcceptEvent")
@ConditionalOnProperty(
        prefix = "xiyu.integrations.organization.event-sdk",
        name = "enabled",
        havingValue = "true"
)
@RequiredArgsConstructor
@Slf4j
public class OrganizationEventSdkConsumerAdapter {

    private final OrganizationEventAppService appService;
    private final ObjectMapper objectMapper;

    @AcceptEvent(
            eventTopic = "BaseOssDept",
            consumerGroup = "bms"
    )
    public EventResult onDeptChanged(String eventMessage) {
        log.info("SDK BaseOssDept event received");
        return handleEvent("BaseOssDept", eventMessage);
    }

    @AcceptEvent(
            eventTopic = "BaseOssJob",
            consumerGroup = "bms"
    )
    public EventResult onJobChanged(String eventMessage) {
        log.info("SDK BaseOssJob event received");
        return handleEvent("BaseOssJob", eventMessage);
    }

    @AcceptEvent(
            eventTopic = "BaseOssUser",
            consumerGroup = "bms"
    )
    public EventResult onUserChanged(String eventMessage) {
        log.info("SDK BaseOssUser event received");
        return handleEvent("BaseOssUser", eventMessage);
    }

    private EventResult handleEvent(String topic, String eventMessage) {
        try {
            String normalized = normalizeSdkMessage(topic, eventMessage);
            if (normalized == null) {
                log.debug("SDK {} heartbeat/system message skipped", topic);
                return successResult("200", "skipped non-data event");
            }
            var resp = appService.receiveViaSdk(topic, normalized);
            var result = successResult(resp.code(), resp.msg());
            result.setMsg(resp.msg());
            return result;
        } catch (RuntimeException ex) {
            log.error("SDK {} event handling failed: {}", topic, ex.getMessage(), ex);
            return successResult("500", ex.getMessage());
        }
    }

    /**
     * Transforms the SDK-delivered message format into the documented event format
     * expected by {@code OrganizationEventNoticeJsonReader}.
     *
     * <p>SDK format (actual, observed on server):
     * <pre>{@code
     * {"eventTrackInfo":{"traceId":"t...","spanId":"s...","parentId":"0"},
     *  "id":575338,"userId":721146639}
     * }</pre>
     *
     * <p>Documented format (expected by parser):
     * <pre>{@code
     * {"traceId":"t...","spanId":"s...","parentId":"0",
     *  "eventSource":"oss","eventTopic":"BaseOssUser",
     *  "time":"1777511328702","key":"575338",
     *  "data":{"id":575338,"userId":721146639}}
     * }</pre>
     *
     * <p>Heartbeat/system messages contain only {@code eventTrackInfo} with no
     * data fields ({@code id}/{@code userId}/{@code deptId}); these are skipped
     * by returning {@code null}.
     *
     * @return normalized JSON string, or {@code null} for heartbeat/system messages
     */
    private String normalizeSdkMessage(String topic, String eventMessage) {
        try {
            JsonNode root = objectMapper.readTree(eventMessage);
            JsonNode trackInfo = root.path("eventTrackInfo");

            boolean hasId = root.has("id") && !root.path("id").isNull();
            boolean hasUserId = root.has("userId") && !root.path("userId").isNull();
            boolean hasDeptId = root.has("deptId") && !root.path("deptId").isNull();
            boolean isDataEvent = hasId || hasUserId || hasDeptId || root.has("data");

            if (!isDataEvent) {
                log.info("SDK {} heartbeat/system message skipped (trackInfo: {})",
                        topic, !trackInfo.isMissingNode());
                return null;
            }

            ObjectNode doc = objectMapper.createObjectNode();
            doc.put("traceId", trackInfo.path("traceId").asText(""));
            doc.put("spanId", trackInfo.path("spanId").asText(""));
            doc.put("parentId", trackInfo.path("parentId").asText(""));
            doc.put("eventSource", "oss");
            doc.put("eventTopic", topic);
            doc.put("time", String.valueOf(System.currentTimeMillis()));
            String key = root.path("id").asText(
                    root.path("userId").asText(
                            root.path("deptId").asText("")));
            doc.put("key", key);

            ObjectNode data = objectMapper.createObjectNode();
            Iterator<String> fieldNames = root.fieldNames();
            while (fieldNames.hasNext()) {
                String field = fieldNames.next();
                if ("eventTrackInfo".equals(field)) {
                    continue;
                }
                data.set(field, root.path(field));
            }
            doc.set("data", data);

            String normalized = objectMapper.writeValueAsString(doc);
            log.info("SDK {} message normalized: {} chars -> documented format", topic, eventMessage.length());
            log.debug("SDK {} original: {}", topic, eventMessage);
            log.debug("SDK {} normalized: {}", topic, normalized);
            return normalized;
        } catch (JsonProcessingException e) {
            log.error("SDK {} failed to parse event message, passing through: {}", topic, eventMessage, e);
            return eventMessage;
        }
    }

    private static EventResult successResult(String code, String msg) {
        var result = new EventResult();
        result.setCode(code);
        result.setMsg(msg);
        return result;
    }
}
