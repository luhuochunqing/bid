// Input: ProjectResultConfirmedEvent
// Output: 入队 WebhookDeliveryTask（使用 CRM 原生 bidInfoList 格式，与 WebhookEventListener 一致）
// Pos: webhook/application/
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.webhook.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.crm.infrastructure.dto.BidInfoInnerDTO;
import com.xiyu.bid.crm.infrastructure.dto.BidInfoSyncDTO;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.project.core.BidResultType;
import com.xiyu.bid.project.domain.ProjectResultConfirmedEvent;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.webhook.infrastructure.WebhookDeliveryTask;
import com.xiyu.bid.webhook.infrastructure.WebhookDeliveryTaskRepository;
import com.xiyu.bid.webhook.infrastructure.WebhookDeliveryTaskStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 项目结果确认回调监听器（接口文档 §4.2）。
 * <p>监听 {@link ProjectResultConfirmedEvent}，组装 CRM 原生 bidInfoList 格式后入队 {@link WebhookDeliveryTask}。
 * <p>复用 §4.1 的重试机制：1min/5min/15min 重试 3 次，仍失败进入死信队列。
 * <p>统一配置项 {@code webhook.crm.url}（与 §4.1 共用），不再单独配置回调地址。
 * <p>event_type 区分 §4.1/§4.2：{@code project.result_confirmed} vs {@code tender.status_changed}。
 * <p><b>⚠️ 2026-06-21 修复：</b>原使用 §4.2 格式（ProjectResultCallbackPayload），CRM 的 bidInfoSync
 * 接口不认识该格式，返回 {@code code:0 success} 但不处理业务数据（CRM 经验文档第 5 节"code:0 谎言铁律"）。
 * 现改为与 WebhookEventListener（commit b6d1119ca, CO-263）一致的 CRM 原生 bidInfoList 格式，
 * BidResultType 映射到 CRM projectStatus 枚举：WON→2, LOST→3, FAILED→4, ABANDONED→6。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProjectResultConfirmedWebhookListener {

    private static final DateTimeFormatter STATUS_EDIT_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final WebhookDeliveryTaskRepository taskRepository;
    private final TenderRepository tenderRepository;
    private final ObjectMapper objectMapper;

    @Value("${webhook.crm.url:}")
    private String crmWebhookUrl;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onProjectResultConfirmed(ProjectResultConfirmedEvent event) {
        log.info("Received ProjectResultConfirmedEvent: projectId={}, tenderId={}, resultType={}, resultId={}",
                event.projectId(), event.tenderId(), event.resultType(), event.resultId());
        if (crmWebhookUrl == null || crmWebhookUrl.isBlank()) {
            log.warn("CRM webhook URL not configured, skipping enqueue for project {}", event.projectId());
            return;
        }
        Integer crmStatus = mapToCrmStatus(event.resultType());
        if (crmStatus == null) {
            log.warn("Unknown resultType {}, skip webhook for project {}", event.resultType(), event.projectId());
            return;
        }
        // 取标讯关联的 CRM 商机编号/名称，用于填充 bidInfoSync 的 code/name 字段。
        // code 必须是 CRM 商机编号（crm_opportunity_id），而非 externalId 的 sourceId 部分。
        // 无关联商机时 code/name 填空字符串，CRM 侧接受。
        Tender tender = tenderRepository.findById(event.tenderId()).orElse(null);
        if (tender == null) {
            log.warn("Tender {} not found, skip webhook (cannot resolve crm opportunity code)", event.tenderId());
            return;
        }
        String crmOpportunityCode = tender.getCrmOpportunityId() != null ? tender.getCrmOpportunityId() : "";
        String crmOpportunityName = tender.getCrmOpportunityName() != null ? tender.getCrmOpportunityName() : "";
        taskRepository.save(WebhookDeliveryTask.builder()
                .tenderId(event.tenderId())
                .externalId(null)
                .targetUrl(crmWebhookUrl)
                .eventType("project.result_confirmed")
                .businessKey(buildBusinessKey(event))
                .payload(buildPayload(event, crmStatus, crmOpportunityCode, crmOpportunityName))
                .status(WebhookDeliveryTaskStatus.PENDING)
                .build());
        log.info("Webhook delivery task enqueued for project {}, resultType={}, crmStatus={}, crmOpportunityCode={}, url={}",
                event.projectId(), event.resultType(), crmStatus,
                crmOpportunityCode.isEmpty() ? "(none)" : crmOpportunityCode, crmWebhookUrl);
    }

    private String buildBusinessKey(ProjectResultConfirmedEvent event) {
        return "%s:%s:%s".formatted(event.projectId(), event.resultType().name(), event.occurredAt());
    }

    /**
     * 映射 BidResultType 到 CRM bidInfoSync 的 status 数字（CRM projectStatus 枚举）。
     * <p>CRM projectStatus 枚举（来自 CRM 商机操作记录原文）：
     * 1-跟进中 2-中标 3-丢标 4-流标 5-投标中 6-弃标。
     * <p>⚠️ 与 WebhookEventListener.mapToCrmStatus() 保持一致：WON→2, LOST→3, FAILED→4。
     * 注意 FAILED 是本平台概念（流标），对应 CRM status=4（流标）。
     */
    private Integer mapToCrmStatus(BidResultType resultType) {
        return switch (resultType) {
            case WON -> 2;
            case LOST -> 3;
            case FAILED -> 4;
            case ABANDONED -> 6;
        };
    }

    /**
     * 构造符合 CRM POST /customer-chance/bidInfoSync 契约的请求体。
     * <p>code 填 CRM 商机编号（crm_opportunity_id），name 填商机名称（crm_opportunity_name）。
     * 无关联商机时两者填空字符串——CRM 侧接受（实测返回 code:0 success）。
     * <p>⚠️ 切勿用 externalId 的 sourceId 部分填 code：那是来源系统数据唯一 ID，非商机编号，
     * 会导致 CRM 匹配失败返回 code:1。
     */
    private String buildPayload(ProjectResultConfirmedEvent event, Integer crmStatus,
                                String crmOpportunityCode, String crmOpportunityName) {
        try {
            String statusEditTime = event.occurredAt().format(STATUS_EDIT_TIME_FORMAT);
            String operator = event.operatorName() != null ? event.operatorName() : "";
            BidInfoInnerDTO inner = new BidInfoInnerDTO(
                    crmOpportunityName,
                    crmOpportunityCode,
                    crmStatus,
                    operator,
                    statusEditTime,
                    buildFeedback(event, operator));
            BidInfoSyncDTO dto = new BidInfoSyncDTO(List.of(inner));
            return objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize bidInfoSync payload", ex);
        }
    }

    /**
     * CRM 要求 feedback 为 JSON 字符串，包含原因+友商+账期+备注+操作人+操作时间。
     * <p>reason 填 resultType 名称；vendor 填竞争对手名称（逗号分隔）；paymentTerm/remark 暂空。
     */
    private String buildFeedback(ProjectResultConfirmedEvent event, String operator) {
        Map<String, Object> fb = new LinkedHashMap<>();
        fb.put("reason", event.resultType().name());
        String vendor = event.competitors() != null && !event.competitors().isEmpty()
                ? event.competitors().stream()
                    .map(c -> c.name() != null ? c.name() : "")
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.joining(", "))
                : "";
        fb.put("vendor", vendor);
        fb.put("paymentTerm", "");
        fb.put("remark", "");
        fb.put("operator", operator);
        fb.put("operateTime", event.occurredAt().format(STATUS_EDIT_TIME_FORMAT));
        try {
            return objectMapper.writeValueAsString(fb);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize feedback", ex);
        }
    }
}