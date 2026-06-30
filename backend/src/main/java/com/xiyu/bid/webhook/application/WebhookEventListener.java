// Input: 标讯状态变更回调 4.1 监听器
// Output: 写入 webhook_delivery_tasks（bidInfoSync 格式）
// Pos: webhook/
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.webhook.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.crm.infrastructure.dto.BidInfoInnerDTO;
import com.xiyu.bid.crm.infrastructure.dto.BidInfoSyncDTO;
import com.xiyu.bid.crm.infrastructure.dto.CrmProjectStatus;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.webhook.domain.TenderStatusChangedEvent;
import com.xiyu.bid.webhook.infrastructure.CrmOpportunityCodeResolver;
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
 * <p>触发时机：标讯弃标（→ ABANDONED）或评估完成（→ EVALUATED，CO-298）时。
 * <p>⚠️ v3.8 变更：WON/LOST 不再走本回调，改由 §4.2 项目结果确认回调承担。
 * <p>⚠️ CO-314 变更：立即投标（→ BIDDING）不再触发 CRM 回调，仅放弃投标触发。
 * <p>⚠️ 2026-06-22 修复：原 tender.status_changed 格式 CRM 不识别（code:0 谎言），
 *    改为 bidInfoSync 格式（与 §4.2 ProjectResultConfirmedWebhookListener 一致）。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebhookEventListener {

    private static final DateTimeFormatter STATUS_EDIT_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String EVENT_TYPE = "tender.status_changed";
    /** CO-346: 与 §4.2 ProjectResultPayloadAssembler 对齐，标识回调来源系统。 */
    private static final String SYSTEM_NAME = "投标管理系统";

    private final WebhookDeliveryTaskRepository taskRepository;
    private final TenderRepository tenderRepository;
    private final ObjectMapper objectMapper;
    private final CrmOpportunityCodeResolver crmOpportunityCodeResolver;

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
        if (!Set.of(Tender.Status.ABANDONED, Tender.Status.EVALUATED).contains(event.newStatus())) {
            log.info("Tender status {} not in trigger states [ABANDONED, EVALUATED] (CO-314), skip webhook for tender {}",
                    event.newStatus(), event.tenderId());
            return;
        }
        Integer crmStatus = mapToCrmStatus(event.newStatus());
        Tender tender = tenderRepository.findById(event.tenderId()).orElse(null);
        if (tender == null) {
            log.warn("Tender {} not found, skip webhook", event.tenderId());
            return;
        }
        String crmOpportunityCode = crmOpportunityCodeResolver.resolve(tender.getCrmOpportunityId());
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
     * <p>CO-346: EVALUATED 状态不回调 status（置空），避免 CRM 侧产生无意义的"跟进中"记录。
     */
    private Integer mapToCrmStatus(Tender.Status tenderStatus) {
        return switch (tenderStatus) {
            case BIDDING -> CrmProjectStatus.BIDDING;
            case ABANDONED -> CrmProjectStatus.ABANDONED;
            case EVALUATED -> null;  // CO-346: 关联商机提交时不回调 status
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

    /**
     * 组装 CRM feedback JSON 字符串。
     * <p>包含：reason / vendor / paymentTerm / remark / abandonmentReason / operator / operateTime / systemName。
     * <p>CO-346: 增加 systemName="投标管理系统"，与 §4.2 ProjectResultPayloadAssembler.buildFeedbackString 对齐，
     * 让 CRM 侧能识别回调来源系统。
     * <p>CO-414: 增加 abandonmentReason 独立字段（弃标原因），便于 CRM 侧结构化消费，
     * 与 remark 并存（remark 兼容历史消费方）。
     */
    private String buildFeedback(TenderStatusChangedEvent event) {
        Map<String, Object> fb = new LinkedHashMap<>();
        fb.put("reason", event.newStatus().name());
        fb.put("vendor", "");
        fb.put("paymentTerm", "");
        fb.put("remark", event.abandonReason() != null ? event.abandonReason() : "");
        // CO-414: 弃标原因独立字段，便于 CRM 结构化消费
        fb.put("abandonmentReason", event.abandonReason() != null ? event.abandonReason() : "");
        fb.put("operator", event.operatorName() != null ? event.operatorName() : "");
        fb.put("operateTime", event.occurredAt().format(STATUS_EDIT_TIME_FORMAT));
        fb.put("systemName", SYSTEM_NAME);
        try {
            return objectMapper.writeValueAsString(fb);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize feedback", ex);
        }
    }
}