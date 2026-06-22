// Input: 标讯状态变更回调 4.1 监听器
// Output: 写入 webhook_delivery_tasks（bidInfoSync 格式）
// Pos: webhook/
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.webhook.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.crm.application.CrmProjectLeaderService;
import com.xiyu.bid.crm.infrastructure.dto.BidInfoInnerDTO;
import com.xiyu.bid.crm.infrastructure.dto.BidInfoSyncDTO;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.webhook.domain.TenderStatusChangedEvent;
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
import java.util.Set;

/**
 * 标讯状态变更回调监听器（接口文档 §4.1）。
 * <p>本平台在标讯状态发生变化时，向 CRM 推送 bidInfoSync 回调。
 * <p>触发时机：标讯提交投标（PENDING → BIDDING）或弃标（→ ABANDONED）时。
 * <p>⚠️ v3.8 变更：WON/LOST 不再走本回调，改由 §4.2 项目结果确认回调承担。
 * <p>⚠️ 2026-06-22 修复：原 tender.status_changed 格式 CRM 不识别（code:0 谎言），
 *    改为 bidInfoSync 格式（与 §4.2 ProjectResultConfirmedWebhookListener 一致）。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebhookEventListener {

    private static final DateTimeFormatter STATUS_EDIT_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String EVENT_TYPE = "tender.status_changed";

    private static final Set<Tender.Status> TRIGGER_STATES = Set.of(
            Tender.Status.BIDDING, Tender.Status.ABANDONED, Tender.Status.EVALUATED);

    private final WebhookDeliveryTaskRepository taskRepository;
    private final TenderRepository tenderRepository;
    private final ObjectMapper objectMapper;
    private final CrmProjectLeaderService crmProjectLeaderService;

    @Value("${webhook.crm.url:}")
    private String crmWebhookUrl;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTenderStatusChanged(TenderStatusChangedEvent event) {
        log.info("WebhookEventListener received TenderStatusChangedEvent: tenderId={}, oldStatus={}, newStatus={}, externalId={}",
                event.tenderId(), event.oldStatus(), event.newStatus(), event.externalId());
        if (crmWebhookUrl == null || crmWebhookUrl.isBlank()) {
            log.warn("CRM webhook URL not configured, skipping enqueue for tender {}", event.tenderId());
            return;
        }
        if (!TRIGGER_STATES.contains(event.newStatus())) {
            log.info("Tender status {} not in trigger states {} (v3.8), skip webhook for tender {}",
                    event.newStatus(), TRIGGER_STATES, event.tenderId());
            return;
        }
        Integer crmStatus = mapToCrmStatus(event.newStatus());
        Tender tender = tenderRepository.findById(event.tenderId()).orElse(null);
        if (tender == null) {
            log.warn("Tender {} not found, skip webhook", event.tenderId());
            return;
        }
        String crmOpportunityCode = resolveCrmOpportunityCode(tender.getCrmOpportunityId());
        String crmOpportunityName = tender.getCrmOpportunityName() != null ? tender.getCrmOpportunityName() : "";
        String payload = buildPayload(event, crmStatus, crmOpportunityCode, crmOpportunityName);
        taskRepository.save(WebhookDeliveryTask.builder()
                .tenderId(event.tenderId())
                .externalId(event.externalId())
                .targetUrl(crmWebhookUrl)
                .eventType(EVENT_TYPE)
                .businessKey(buildBusinessKey(event))
                .payload(payload)
                .status(WebhookDeliveryTaskStatus.PENDING)
                .build());
        log.info("Webhook delivery task enqueued for tender {}, newStatus={}, crmStatus={}, crmOpportunityCode={}, url={}",
                event.tenderId(), event.newStatus(), crmStatus,
                crmOpportunityCode.isEmpty() ? "(none)" : crmOpportunityCode, crmWebhookUrl);
    }

    private String buildBusinessKey(TenderStatusChangedEvent event) {
        return "%s:%s:%s".formatted(event.tenderId(), event.newStatus().name(), event.occurredAt());
    }

    /**
     * 映射 Tender.Status 到 CRM bidInfoSync 的 status 数字。
     * <p>CRM projectStatus 枚举：1-跟进中 2-中标 3-丢标 4-流标 5-投标中 6-弃标。
     */
    private Integer mapToCrmStatus(Tender.Status tenderStatus) {
        return switch (tenderStatus) {
            case BIDDING -> 5;
            case ABANDONED -> 6;
            case EVALUATED -> 1;  // CO-298: 待评估标讯关联商机后提交 → 跟进中
            default -> null;
        };
    }

    /**
     * 构造符合 CRM POST /customer-chance/bidInfoSync 契约的请求体。
     * <p>⚠️ 2026-06-22 新增 tenderId 字段（CO-298）：标讯内部 ID，方便 CRM 侧关联回标讯。
     */
    private String buildPayload(TenderStatusChangedEvent event, Integer crmStatus,
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
                    buildFeedback(event),
                    event.tenderId());  // CO-298: tenderId 字段
            BidInfoSyncDTO dto = new BidInfoSyncDTO(List.of(inner));
            return objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize bidInfoSync payload", ex);
        }
    }

    private String buildFeedback(TenderStatusChangedEvent event) {
        Map<String, Object> fb = new LinkedHashMap<>();
        fb.put("reason", event.newStatus().name());
        fb.put("vendor", "");
        fb.put("paymentTerm", "");
        fb.put("remark", event.abandonReason() != null ? event.abandonReason() : "");
        fb.put("operator", event.operatorName() != null ? event.operatorName() : "");
        fb.put("operateTime", event.occurredAt().format(STATUS_EDIT_TIME_FORMAT));
        try {
            return objectMapper.writeValueAsString(fb);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize feedback", ex);
        }
    }

    /**
     * 解析 CRM 商机编号（CC 前缀格式）。
     * <p>CO-277: tender.crm_opportunity_id 可能存的是商机主键 id（纯数字），
     * 而非商机编号 code（CC 前缀）。CRM bidInfoSync 接口期望 code 格式。
     */
    private String resolveCrmOpportunityCode(String crmOpportunityId) {
        if (crmOpportunityId == null || crmOpportunityId.isBlank()) {
            return "";
        }
        Long chanceId = tryParseChanceId(crmOpportunityId);
        if (chanceId == null) {
            // 已经是非纯数字（可能是 CC 前缀），直接返回
            return crmOpportunityId;
        }
        try {
            CrmProjectLeaderService.ProjectLeaderResult leader =
                    crmProjectLeaderService.findProjectLeaderByChanceId(chanceId);
            if (leader != null && leader.opportunityCode() != null && !leader.opportunityCode().isBlank()) {
                log.info("Resolved crmOpportunityId: id={} → code={}", chanceId, leader.opportunityCode());
                return leader.opportunityCode();
            }
            log.warn("resolveCrmOpportunityCode: CRM returned no code for chanceId={}, using raw id as fallback", chanceId);
        } catch (RuntimeException e) {
            log.error("resolveCrmOpportunityCode: CRM lookup failed for chanceId={}, using raw id as fallback: {}",
                    chanceId, e.getMessage());
        }
        return crmOpportunityId;
    }

    private Long tryParseChanceId(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}