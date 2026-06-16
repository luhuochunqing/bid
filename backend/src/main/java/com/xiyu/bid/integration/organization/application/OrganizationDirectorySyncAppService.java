package com.xiyu.bid.integration.organization.application;

import com.xiyu.bid.integration.organization.domain.OrganizationEventNotice;
import com.xiyu.bid.integration.organization.domain.OrganizationEventNoticeParseResult;
import com.xiyu.bid.integration.organization.domain.OrganizationEventStatus;
import com.xiyu.bid.integration.organization.domain.OrganizationEventType;
import com.xiyu.bid.integration.organization.domain.OrganizationDirectoryLookupContext;
import com.xiyu.bid.integration.organization.dto.OrganizationEventWebhookRequest;
import com.xiyu.bid.integration.organization.dto.OrganizationEventWebhookResponse;
import com.xiyu.bid.integration.organization.infrastructure.client.OrganizationDirectoryHttpGatewayException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrganizationDirectorySyncAppService {
    private final OrganizationEventInboxService inboxService;
    private final OrganizationEventNoticeJsonReader noticeJsonReader;
    private final ObjectProvider<OrganizationDirectoryGateway> directoryGatewayProvider;
    private final OrganizationDepartmentSyncWriter departmentWriter;
    private final OrganizationUserSyncWriter userWriter;
    private final OrganizationIntegrationSettingsResolver settingsResolver;
    private final ObjectMapper objectMapper;

    public OrganizationEventWebhookResponse receiveWebhook(OrganizationEventWebhookRequest request) {
        String rawPayload = request == null ? "" : request.eventMessage();
        OrganizationIntegrationSettings settings = settingsResolver.resolve();
        if (!settings.enabled()) {
            String eventKey = OrganizationEventKeyFactory.hash(rawPayload);
            inboxService.markRejected(eventKey, "组织架构事件接入已关闭", rawPayload);
            return response("500", "组织架构事件接入已关闭", eventKey, false, false, OrganizationEventStatus.REJECTED);
        }
        OrganizationEventNoticeParseResult parsed = noticeJsonReader.parse(rawPayload);
        if (!parsed.valid()) {
            String eventKey = OrganizationEventKeyFactory.hash(rawPayload);
            inboxService.markRejected(eventKey, parsed.message(), rawPayload);
            return response("500", parsed.message(), eventKey, false, false, OrganizationEventStatus.REJECTED);
        }
        if (!requestTopicMatchesPayload(request, parsed.notice())) {
            String eventKey = inboxService.eventKey(parsed.notice());
            inboxService.markRejected(eventKey, "HTTP事件Topic与payload不一致", rawPayload);
            return response("500", "HTTP事件Topic与payload不一致", eventKey, false, false, OrganizationEventStatus.REJECTED);
        }
        return processNotice(parsed.notice(), rawPayload, settings);
    }

    public OrganizationEventWebhookResponse reprocessReservedEvent(String eventKey, String rawPayload) {
        OrganizationIntegrationSettings settings = settingsResolver.resolve();
        if (!settings.enabled()) {
            return response("500", "组织架构事件接入已关闭", eventKey, false, false, OrganizationEventStatus.REJECTED);
        }
        OrganizationEventNoticeParseResult parsed = noticeJsonReader.parse(rawPayload);
        if (!parsed.valid()) {
            inboxService.markNonRetryableFailure(eventKey, parsed.message(), "VALIDATION_FAILED");
            return response("500", parsed.message(), eventKey, false, false, OrganizationEventStatus.DEAD_LETTER);
        }
        OrganizationEventNotice notice = parsed.notice();
        if (!settings.sourceAllowed(notice.eventSource())) {
            inboxService.markNonRetryableFailure(eventKey, "事件来源不在白名单内", "SOURCE_NOT_ALLOWED");
            return response("500", "事件来源不在白名单内", eventKey, false, false, OrganizationEventStatus.DEAD_LETTER);
        }
        try {
            return lookupAndWrite(notice, eventKey);
        } catch (OrganizationDirectoryHttpGatewayException ex) {
            markGatewayFailure(eventKey, ex);
            return failureResponse(eventKey, ex);
        } catch (RuntimeException ex) {
            inboxService.markFailed(eventKey, "组织架构同步处理异常", "SYNC_EXCEPTION");
            return response("500", "组织架构同步处理异常", eventKey, false, false, OrganizationEventStatus.PENDING_RETRY);
        }
    }

    private OrganizationEventWebhookResponse processNotice(
            OrganizationEventNotice notice,
            String rawPayload,
            OrganizationIntegrationSettings settings
    ) {
        String eventKey = inboxService.eventKey(notice);
        if (!settings.sourceAllowed(notice.eventSource())) {
            inboxService.markRejected(eventKey, "事件来源不在白名单内", rawPayload);
            return response("500", "事件来源不在白名单内", eventKey, false, false, OrganizationEventStatus.REJECTED);
        }
        if (!inboxService.reserve(notice, rawPayload)) {
            return response("200", "success", eventKey, true, true, OrganizationEventStatus.DUPLICATE);
        }
        try {
            return lookupAndWrite(notice, eventKey);
        } catch (OrganizationDirectoryHttpGatewayException ex) {
            markGatewayFailure(eventKey, ex);
            return failureResponse(eventKey, ex);
        } catch (RuntimeException ex) {
            inboxService.markFailed(eventKey, "组织架构同步处理异常", "SYNC_EXCEPTION");
            return response("500", "组织架构同步处理异常", eventKey, false, false, OrganizationEventStatus.PENDING_RETRY);
        }
    }

    private void markGatewayFailure(String eventKey, OrganizationDirectoryHttpGatewayException ex) {
        if (ex.retryable()) {
            inboxService.markRetryableFailure(
                    eventKey,
                    "组织架构主数据接口调用失败",
                    ex.failureKind().name(),
                    ex.failureKind()
            );
        } else {
            inboxService.markNonRetryableFailure(eventKey, "组织架构主数据接口拒绝请求", ex.failureKind().name());
        }
    }

    private OrganizationEventWebhookResponse failureResponse(String eventKey, OrganizationDirectoryHttpGatewayException ex) {
        OrganizationEventStatus status = ex.retryable()
                ? OrganizationEventStatus.PENDING_RETRY
                : OrganizationEventStatus.DEAD_LETTER;
        return response("500", ex.getMessage(), eventKey, false, false, status);
    }

    private OrganizationEventWebhookResponse lookupAndWrite(OrganizationEventNotice notice, String eventKey) {
        OrganizationDirectoryLookupContext context = new OrganizationDirectoryLookupContext(notice.traceId(), notice.eventSource());
        OrganizationDirectoryGateway gateway = directoryGatewayProvider.getIfAvailable();
        if (gateway == null) {
            return response("500", "组织架构网关未配置（SDK-only 模式不执行拉取）", eventKey, false, false, OrganizationEventStatus.DEAD_LETTER);
        }
        if (notice.topic() == OrganizationEventType.JOB_NOTICE) {
            return gateway.fetchJobByJobId(notice.subjectId(), context)
                    .map(snapshot -> {
                        log.info("组织架构回查 Job 成功: jobId={}, name={}, code={}",
                                snapshot.externalJobId(), snapshot.jobName(), snapshot.jobCode());
                        inboxService.markProcessed(eventKey);
                        return response("200", "success", eventKey, true, false, OrganizationEventStatus.PROCESSED);
                    })
                    .orElseGet(() -> {
                        log.info("组织架构回查 Job 未找到: jobId={}", notice.subjectId());
                        inboxService.markProcessed(eventKey);
                        return response("200", "success", eventKey, true, false, OrganizationEventStatus.PROCESSED);
                    });
        }
        if (notice.topic() == OrganizationEventType.DEPARTMENT_NOTICE) {
            return gateway.fetchDepartmentByDeptId(notice.subjectId(), context)
                    .map(snapshot -> {
                        departmentWriter.upsert(notice.eventSource(), eventKey, snapshot);
                        inboxService.markProcessed(eventKey);
                        return response("200", "success", eventKey, true, false, OrganizationEventStatus.PROCESSED);
                    })
                    .orElseGet(() -> disableLocalDepartment(notice, eventKey));
        }
        return gateway.fetchUserByUserId(notice.subjectId(), context)
                .map(snapshot -> {
                    userWriter.upsert(notice.eventSource(), eventKey, snapshot);
                    inboxService.markProcessed(eventKey);
                    return response("200", "success", eventKey, true, false, OrganizationEventStatus.PROCESSED);
                })
                .orElseGet(() -> disableLocalUser(notice, eventKey));
    }

    private OrganizationEventWebhookResponse disableLocalDepartment(OrganizationEventNotice notice, String eventKey) {
        departmentWriter.disableByExternalId(notice.eventSource(), eventKey, notice.subjectId());
        inboxService.markProcessed(eventKey);
        return response("200", "success", eventKey, true, false, OrganizationEventStatus.PROCESSED);
    }

    private OrganizationEventWebhookResponse disableLocalUser(OrganizationEventNotice notice, String eventKey) {
        userWriter.disableByExternalId(notice.eventSource(), eventKey, notice.subjectId());
        inboxService.markProcessed(eventKey);
        return response("200", "success", eventKey, true, false, OrganizationEventStatus.PROCESSED);
    }

    private boolean requestTopicMatchesPayload(OrganizationEventWebhookRequest request, OrganizationEventNotice notice) {
        return request == null
                || request.eventTopic() == null
                || request.eventTopic().isBlank()
                || notice.topic().topic().equals(request.eventTopic());
    }

    private OrganizationEventWebhookResponse response(
            String code,
            String message,
            String eventKey,
            boolean accepted,
            boolean duplicate,
            OrganizationEventStatus status
    ) {
        return OrganizationEventResponseFactory.response(code, message, eventKey, accepted, duplicate, status);
    }
}
