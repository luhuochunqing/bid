package com.xiyu.bid.integration.organization.domain;

public final class OrganizationEventNoticeParser {

    private OrganizationEventNoticeParser() {
    }

    public static OrganizationEventNoticeParseResult parse(OrganizationEventNoticeFields fields) {
        if (fields == null) {
            return OrganizationEventNoticeParseResult.invalid("事件通知内容不能为空");
        }
        return OrganizationSyncPolicy.topicFromEventTopic(fields.eventTopic())
                .map(topic -> noticeFrom(fields, topic))
                .orElseGet(() -> OrganizationEventNoticeParseResult.invalid("不支持的组织事件主题"));
    }

    private static OrganizationEventNoticeParseResult noticeFrom(OrganizationEventNoticeFields fields, OrganizationEventType topic) {
        String subjectId;
        if (topic == OrganizationEventType.DEPARTMENT_NOTICE) {
            subjectId = fields.deptId();
        } else if (topic == OrganizationEventType.JOB_NOTICE) {
            subjectId = fields.jobId();
        } else {
            subjectId = fields.userId();
        }
        String missing = firstMissing(
                fields.traceId(),
                fields.spanId(),
                fields.eventSource(),
                fields.time(),
                fields.key(),
                subjectId
        );
        if (!missing.isBlank()) {
            return OrganizationEventNoticeParseResult.invalid("事件通知缺少字段 " + missing);
        }
        return OrganizationEventNoticeParseResult.ok(new OrganizationEventNotice(
                fields.traceId().trim(),
                fields.spanId().trim(),
                trim(fields.parentId()),
                fields.eventSource().trim(),
                topic,
                fields.time().trim(),
                fields.key().trim(),
                subjectId.trim()
        ));
    }

    private static String firstMissing(
            String traceId,
            String spanId,
            String eventSource,
            String time,
            String key,
            String subjectId
    ) {
        if (isBlank(traceId)) {
            return "traceId";
        }
        if (isBlank(spanId)) {
            return "spanId";
        }
        if (isBlank(eventSource)) {
            return "eventSource";
        }
        if (isBlank(time)) {
            return "time";
        }
        if (isBlank(key)) {
            return "key";
        }
        return isBlank(subjectId) ? "data.deptId/userId" : "";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
