package com.xiyu.bid.webhook.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@Component
@RequiredArgsConstructor
@Slf4j
public class WebhookEventListener {
    private static final DateTimeFormatter STATUS_EDIT_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final WebhookDeliveryTaskRepository taskRepository;
    private final TenderRepository tenderRepository;
    private final ObjectMapper objectMapper;

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
        Integer crmStatus = mapToCrmStatus(event.newStatus());
        if (crmStatus == null) {
            log.info("Tender status {} not a CRM terminal state, skip webhook for tender {}",
                    event.newStatus(), event.tenderId());
            return;
        }
        // 取标讯关联的 CRM 商机编号/名称，用于填充 bidInfoSync 的 code/name 字段。
        // code 必须是 CRM 商机编号（crm_opportunity_id 列存 code，CC... 格式）—— bidInfoSync 契约
        // （西域CRM商机对接接口.md BidInfoInnerDTO schema：code=编号）。CO-277 确保 CRM 推的 id 已反查为 code 落库。
        // 而非 externalId 的 sourceId 部分（那是来源系统数据唯一 ID，非商机编号，会导致 CRM 匹配失败返回 code:1）。
        // 无关联商机时 code/name 填空字符串，CRM 侧接受（实测 tender 249 返回 code:0 success）。
        Tender tender = tenderRepository.findById(event.tenderId()).orElse(null);
        if (tender == null) {
            log.warn("Tender {} not found, skip webhook (cannot resolve crm opportunity code)", event.tenderId());
            return;
        }
        String crmOpportunityCode = tender.getCrmOpportunityId() != null ? tender.getCrmOpportunityId() : "";
        String crmOpportunityName = tender.getCrmOpportunityName() != null ? tender.getCrmOpportunityName() : "";
        taskRepository.save(WebhookDeliveryTask.builder()
                .tenderId(event.tenderId())
                .externalId(event.externalId())
                .targetUrl(crmWebhookUrl)
                .eventType("tender.status_changed")
                .businessKey(buildBusinessKey(event))
                .payload(buildPayload(event, crmStatus, crmOpportunityCode, crmOpportunityName))
                .status(WebhookDeliveryTaskStatus.PENDING)
                .build());
        log.info("Webhook delivery task enqueued for tender {}, crmStatus={}, crmOpportunityCode={}, url={}",
                event.tenderId(), crmStatus, crmOpportunityCode.isEmpty() ? "(none)" : crmOpportunityCode, crmWebhookUrl);
    }

    private String buildBusinessKey(TenderStatusChangedEvent event) {
        return "%s:%s:%s".formatted(event.tenderId(), event.newStatus().name(), event.occurredAt());
    }

    /**
     * 映射标讯终态到 CRM bidInfoSync 的 status 数字（即 CRM projectStatus 枚举值）。
     * <p>CRM projectStatus 枚举（来自 CRM 商机操作记录原文）：
     * 1-跟进中 2-中标 3-丢标 4-流标 5-投标中 6-弃标。
     * <p>⚠️ 接口文档《西域CRM商机对接接口.md》曾误写为"1-弃标 2-中标 3-丢标 4-流标"，
     * 实际 1=跟进中、弃标=6。曾因照抄错误文档把 ABANDONED 映射成 1，导致回传后 CRM
     * 把商机状态改成"跟进中"而非"弃标"（tender 268 实测，2026-06-18 排查）。
     * <p>中间态（待分配/跟踪/评估/投标）不回传，返回 null。
     */
    private Integer mapToCrmStatus(Tender.Status status) {
        return switch (status) {
            case ABANDONED -> 6;
            case WON -> 2;
            case LOST -> 3;
            default -> null;
        };
    }

    /**
     * 构造符合 CRM POST /customer-chance/bidInfoSync 契约的请求体。
     * <p>code 填 CRM 商机编号（crm_opportunity_id），name 填商机名称（crm_opportunity_name）。
     * 无关联商机时两者填空字符串——CRM 侧接受（实测返回 code:0 success）。
     * <p>⚠️ 切勿用 externalId 的 sourceId 部分填 code：那是来源系统数据唯一 ID，非商机编号，
     * 会导致 CRM 匹配失败返回 code:1（tender 268 案例）。
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
                    buildFeedback(event, operator));
            BidInfoSyncDTO dto = new BidInfoSyncDTO(List.of(inner));
            return objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize bidInfoSync payload", ex);
        }
    }

    /**
     * CRM 要求 feedback 为 JSON 字符串，包含原因+友商+账期+备注+操作人+操作时间。
     * 事件未携带友商/账期/备注，置空字符串。
     */
    private String buildFeedback(TenderStatusChangedEvent event, String operator) {
        Map<String, Object> fb = new LinkedHashMap<>();
        fb.put("reason", event.abandonReason() != null ? event.abandonReason() : "");
        fb.put("vendor", "");
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
