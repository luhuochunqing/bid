package com.xiyu.bid.integration.organization.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrganizationEventNoticeParser - pure notice parsing")
class OrganizationEventNoticeParserTest {

    @Test
    @DisplayName("parses BaseOssDept notice and keeps only notification identity")
    void parse_acceptsBaseOssDeptNotice() {
        OrganizationEventNoticeParseResult result = OrganizationEventNoticeParser.parse(new OrganizationEventNoticeFields(
                "trace-1", "span-1", "parent-1", "customer-org", "BaseOssDept",
                "2026-04-30T10:15:30+08:00", "evt-1", "D001", "", ""
        ));

        assertThat(result.valid()).isTrue();
        assertThat(result.notice().topic()).isEqualTo(OrganizationEventType.DEPARTMENT_NOTICE);
        assertThat(result.notice().subjectId()).isEqualTo("D001");
    }

    @Test
    @DisplayName("accepts optional parentId and millisecond event time from event bus")
    void parse_acceptsOptionalParentIdAndEpochMillis() {
        OrganizationEventNoticeParseResult result = OrganizationEventNoticeParser.parse(new OrganizationEventNoticeFields(
                "t509415008096264192", "s509415010981044224", "", "oss", "BaseOssUser",
                "1730884403101", "720518523", "", "720518523", ""
        ));

        assertThat(result.valid()).isTrue();
        assertThat(result.notice().parentId()).isEmpty();
        assertThat(result.notice().key()).isEqualTo("720518523");
        assertThat(result.notice().subjectId()).isEqualTo("720518523");
    }

    @Test
    @DisplayName("accepts null parentId as optional event bus field")
    void parse_acceptsNullParentId() {
        OrganizationEventNoticeParseResult result = OrganizationEventNoticeParser.parse(new OrganizationEventNoticeFields(
                "t509415008096264192", "s509415010981044224", null, "oss", "BaseOssUser",
                "1730884403101", "720518523", "", "720518523", ""
        ));

        assertThat(result.valid()).isTrue();
        assertThat(result.notice().parentId()).isEmpty();
    }

    @Test
    @DisplayName("rejects missing topic-specific data id")
    void parse_rejectsMissingUserId() {
        OrganizationEventNoticeParseResult result = OrganizationEventNoticeParser.parse(new OrganizationEventNoticeFields(
                "trace-1", "span-1", "parent-1", "customer-org", "BaseOssUser",
                "2026-04-30T10:15:30+08:00", "evt-1", "D001", "", ""
        ));

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).contains("userId");
    }

    @Test
    @DisplayName("rejects unknown event topic")
    void parse_rejectsUnknownTopic() {
        OrganizationEventNoticeParseResult result = OrganizationEventNoticeParser.parse(new OrganizationEventNoticeFields(
                "trace-1", "span-1", "parent-1", "customer-org", "LegacyUser",
                "2026-04-30T10:15:30+08:00", "evt-1", "", "U001", ""
        ));

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).contains("主题");
    }
}
