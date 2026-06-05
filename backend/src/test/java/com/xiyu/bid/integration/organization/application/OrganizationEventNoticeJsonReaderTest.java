package com.xiyu.bid.integration.organization.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.integration.organization.domain.OrganizationEventNoticeParseResult;
import com.xiyu.bid.integration.organization.domain.OrganizationEventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrganizationEventNoticeJsonReader - JSON shell parsing")
class OrganizationEventNoticeJsonReaderTest {
    private final OrganizationEventNoticeJsonReader reader = new OrganizationEventNoticeJsonReader(new ObjectMapper());

    @Test
    @DisplayName("parses escaped JSON values through Jackson before pure validation")
    void parse_escapedJsonValue() {
        OrganizationEventNoticeParseResult result = reader.parse("""
                {
                  "traceId": "trace-1",
                  "spanId": "span-1",
                  "parentId": "parent-1",
                  "eventSource": "customer-org",
                  "eventTopic": "BaseOssUser",
                  "time": "2026-04-30T10:15:30+08:00",
                  "key": "evt-1",
                  "data": {"userId": "U\\\\u0030001"}
                }
                """);

        assertThat(result.valid()).isTrue();
        assertThat(result.notice().topic()).isEqualTo(OrganizationEventType.USER_NOTICE);
        assertThat(result.notice().subjectId()).isEqualTo("U\\u0030001");
    }

    @Test
    @DisplayName("parses numeric event bus fields and optional parentId")
    void parse_numericEventBusFields() {
        OrganizationEventNoticeParseResult result = reader.parse("""
                {
                  "traceId": "t509415008096264192",
                  "spanId": "s509415010981044224",
                  "data": {"deptId": 3730158, "id": 3600},
                  "eventSource": "oss",
                  "eventTopic": "BaseOssDept",
                  "time": 1730884403101,
                  "key": 3730158
                }
                """);

        assertThat(result.valid()).isTrue();
        assertThat(result.notice().topic()).isEqualTo(OrganizationEventType.DEPARTMENT_NOTICE);
        assertThat(result.notice().key()).isEqualTo("3730158");
        assertThat(result.notice().subjectId()).isEqualTo("3730158");
        assertThat(result.notice().parentId()).isEmpty();
    }

    @Test
    @DisplayName("rejects malformed JSON without throwing")
    void parse_malformedJson_returnsInvalid() {
        OrganizationEventNoticeParseResult result = reader.parse("{\"eventTopic\":\"BaseOssUser\"");

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).contains("JSON");
    }
}
