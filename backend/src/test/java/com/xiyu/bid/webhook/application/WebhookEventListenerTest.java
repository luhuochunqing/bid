package com.xiyu.bid.webhook.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.webhook.domain.TenderStatusChangedEvent;
import com.xiyu.bid.webhook.infrastructure.CrmOpportunityCodeResolver;
import com.xiyu.bid.webhook.infrastructure.WebhookDeliveryTask;
import com.xiyu.bid.webhook.infrastructure.WebhookDeliveryTaskRepository;
import com.xiyu.bid.webhook.infrastructure.WebhookDeliveryTaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WebhookEventListener 单元测试（§4.1 bidInfoSync 格式）。
 * <p>覆盖：
 * <ul>
 *   <li>触发时机：仅 ABANDONED 和 EVALUATED 入队；BIDDING/TRACKING/WON/LOST 跳过（CO-314：立即投标不再触发 CRM 回调）。</li>
 *   <li>载荷符合 CRM POST /customer-chance/bidInfoSync 契约（bidInfoList 格式）。</li>
 *   <li>code 从 tender.crm_opportunity_id 解析（CC 前缀）。</li>
 *   <li>crmWebhookUrl 未配置时跳过。</li>
 *   <li>tender 不存在时跳过。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("WebhookEventListener — §4.1 bidInfoSync 格式")
class WebhookEventListenerTest {

    private static final String CRM_URL = "https://crm.example.com/api/bidInfoSync";
    private static final Long TENDER_ID = 254L;
    private static final String CRM_OPPORTUNITY_ID = "CC20260618267";
    private static final String CRM_OPPORTUNITY_NAME = "西域集团2026年度MRO采购招标";

    @Mock private WebhookDeliveryTaskRepository taskRepository;
    @Mock private TenderRepository tenderRepository;
    @Mock private CrmOpportunityCodeResolver crmOpportunityCodeResolver;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private WebhookEventListener listener() {
        configureDefaultCodeResolverAnswer();
        WebhookEventListener l = new WebhookEventListener(taskRepository, tenderRepository, objectMapper, crmOpportunityCodeResolver);
        ReflectionTestUtils.setField(l, "crmWebhookUrl", CRM_URL);
        return l;
    }

    private WebhookEventListener listenerWithoutUrl() {
        configureDefaultCodeResolverAnswer();
        WebhookEventListener l = new WebhookEventListener(taskRepository, tenderRepository, objectMapper, crmOpportunityCodeResolver);
        ReflectionTestUtils.setField(l, "crmWebhookUrl", "");
        return l;
    }

    private void configureDefaultCodeResolverAnswer() {
        // Default: return input as-is（CC 前缀 code 直接返回），null/blank 返回空字符串
        lenient().when(crmOpportunityCodeResolver.resolve(any()))
                .thenAnswer(inv -> {
                    String input = inv.getArgument(0);
                    return (input == null || input.isBlank()) ? "" : input;
                });
    }

    private TenderStatusChangedEvent event(Tender.Status newStatus, String abandonReason, String operatorName) {
        return TenderStatusChangedEvent.of(
                TENDER_ID, "CRM:254", Tender.Status.TRACKING, newStatus, "西域集团招标",
                abandonReason, 493L, operatorName, null, null);
    }

    private Tender mockTender() {
        Tender tender = new Tender();
        tender.setId(TENDER_ID);
        tender.setCrmOpportunityId(CRM_OPPORTUNITY_ID);
        tender.setCrmOpportunityName(CRM_OPPORTUNITY_NAME);
        return tender;
    }

    @Test
    @DisplayName("BIDDING -> 不入队（CO-314：立即投标不再触发 CRM 回调，仅放弃投标触发）")
    void bidding_notEnqueued() {
        WebhookEventListener l = listener();
        when(tenderRepository.findById(TENDER_ID)).thenReturn(Optional.of(mockTender()));

        l.onTenderStatusChanged(event(Tender.Status.BIDDING, null, "张三"));

        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("ABANDONED -> 入队，bidInfoList 格式，crmStatus=6，feedback 含 remark + systemName")
    void abandoned_enqueuesWithBidInfoSync() throws Exception {
        WebhookEventListener l = listener();
        when(tenderRepository.findById(TENDER_ID)).thenReturn(Optional.of(mockTender()));

        // CO-346: operatorName 现在是"姓名（工号）"格式（由 TenderEvaluationSubmissionService.formatOperatorDisplay 构造）
        l.onTenderStatusChanged(event(Tender.Status.ABANDONED, "客户预算过低，放弃投标", "李四（06100）"));

        WebhookDeliveryTask saved = captureSingleSaved();
        JsonNode root = objectMapper.readTree(saved.getPayload());
        JsonNode bidInfo = root.path("bidInfoList").get(0);
        assertThat(bidInfo.path("status").asInt()).isEqualTo(6);
        assertThat(bidInfo.path("statusEditor").asText()).isEqualTo("李四（06100）");

        JsonNode feedback = objectMapper.readTree(bidInfo.path("feedback").asText());
        assertThat(feedback.path("reason").asText()).isEqualTo("ABANDONED");
        assertThat(feedback.path("remark").asText()).isEqualTo("客户预算过低，放弃投标");
        assertThat(feedback.path("operator").asText()).isEqualTo("李四（06100）");
        // CO-346: 与 §4.2 对齐，feedback 带 systemName 标识来源系统
        assertThat(feedback.path("systemName").asText()).isEqualTo("投标管理系统");
    }

    @Test
    @DisplayName("EVALUATED -> 入队，status=null（CO-346），statusEditor + systemName 正常回传")
    void evaluated_enqueuesWithBidInfoSync_statusNull() throws Exception {
        WebhookEventListener l = listener();
        when(tenderRepository.findById(TENDER_ID)).thenReturn(Optional.of(mockTender()));

        l.onTenderStatusChanged(event(Tender.Status.EVALUATED, null, "王五（06234）"));

        WebhookDeliveryTask saved = captureSingleSaved();
        JsonNode root = objectMapper.readTree(saved.getPayload());
        JsonNode bidInfo = root.path("bidInfoList").get(0);
        // CO-346: EVALUATED 状态不再回调 status（null），避免 CRM 侧产生"跟进中"记录
        assertThat(bidInfo.path("status").isNull()).isTrue();
        // 操作人/操作时间/systemName 仍正常回传
        assertThat(bidInfo.path("statusEditor").asText()).isEqualTo("王五（06234）");
        assertThat(bidInfo.path("tenderId").asLong()).isEqualTo(TENDER_ID);  // CO-298: tenderId 字段

        JsonNode feedback = objectMapper.readTree(bidInfo.path("feedback").asText());
        assertThat(feedback.path("operator").asText()).isEqualTo("王五（06234）");
        assertThat(feedback.path("systemName").asText()).isEqualTo("投标管理系统");
    }

    @Test
    @DisplayName("EVALUATED + 无商机 -> 入队，code 为空，tenderId 存在")
    void evaluated_noCrmOpportunity_sendsEmptyCode() throws Exception {
        WebhookEventListener l = listener();
        Tender tender = new Tender();
        tender.setId(TENDER_ID);
        tender.setCrmOpportunityId(null);
        tender.setCrmOpportunityName(null);
        when(tenderRepository.findById(TENDER_ID)).thenReturn(Optional.of(tender));

        l.onTenderStatusChanged(event(Tender.Status.EVALUATED, null, "赵六"));

        WebhookDeliveryTask saved = captureSingleSaved();
        JsonNode root = objectMapper.readTree(saved.getPayload());
        JsonNode bidInfo = root.path("bidInfoList").get(0);
        assertThat(bidInfo.path("code").asText()).isEmpty();
        assertThat(bidInfo.path("name").asText()).isEmpty();
        assertThat(bidInfo.path("tenderId").asLong()).isEqualTo(TENDER_ID);
    }

    @Test
    @DisplayName("crmOpportunityId 为纯数字时，通过 CrmOpportunityCodeResolver 解析为 CC 前缀编号")
    void pureNumericId_resolvesToCcPrefix() throws Exception {
        WebhookEventListener l = listener();
        Tender tender = new Tender();
        tender.setId(TENDER_ID);
        tender.setCrmOpportunityId("321");
        when(tenderRepository.findById(TENDER_ID)).thenReturn(Optional.of(tender));
        when(crmOpportunityCodeResolver.resolve("321")).thenReturn("CC20260621321");

        l.onTenderStatusChanged(event(Tender.Status.ABANDONED, "放弃投标", "张三"));

        WebhookDeliveryTask saved = captureSingleSaved();
        JsonNode root = objectMapper.readTree(saved.getPayload());
        JsonNode bidInfo = root.path("bidInfoList").get(0);
        assertThat(bidInfo.path("code").asText()).isEqualTo("CC20260621321");
    }

    @Test
    @DisplayName("crmOpportunityId 为空时，code 为空字符串（CRM 接受）")
    void emptyCrmOpportunityId_sendsEmptyCode() throws Exception {
        WebhookEventListener l = listener();
        Tender tender = new Tender();
        tender.setId(TENDER_ID);
        tender.setCrmOpportunityId(null);
        when(tenderRepository.findById(TENDER_ID)).thenReturn(Optional.of(tender));

        l.onTenderStatusChanged(event(Tender.Status.ABANDONED, "放弃投标", "张三"));

        WebhookDeliveryTask saved = captureSingleSaved();
        JsonNode root = objectMapper.readTree(saved.getPayload());
        JsonNode bidInfo = root.path("bidInfoList").get(0);
        assertThat(bidInfo.path("code").asText()).isEmpty();
    }

    @Test
    @DisplayName("LOST -> 不入队（v3.8：LOST 改由 §4.2 项目结果确认回调承担）")
    void lost_notEnqueued() {
        WebhookEventListener l = listener();

        l.onTenderStatusChanged(event(Tender.Status.LOST, null, "王五"));

        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("WON -> 不入队（v3.8：WON 改由 §4.2 项目结果确认回调承担）")
    void won_notEnqueued() {
        WebhookEventListener l = listener();

        l.onTenderStatusChanged(event(Tender.Status.WON, null, "赵六"));

        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("TRACKING 中间态 -> 不入队")
    void tracking_notEnqueued() {
        WebhookEventListener l = listener();

        l.onTenderStatusChanged(event(Tender.Status.TRACKING, null, "张三"));

        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("crmWebhookUrl 未配置 -> 不入队")
    void emptyUrl_notEnqueued() {
        WebhookEventListener l = listenerWithoutUrl();

        l.onTenderStatusChanged(event(Tender.Status.ABANDONED, "放弃投标", "张三"));

        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("tender 不存在 -> 不入队")
    void tenderNotFound_notEnqueued() {
        WebhookEventListener l = listener();
        when(tenderRepository.findById(TENDER_ID)).thenReturn(Optional.empty());

        l.onTenderStatusChanged(event(Tender.Status.ABANDONED, "放弃投标", "张三"));

        verify(taskRepository, never()).save(any());
    }

    private WebhookDeliveryTask captureSingleSaved() {
        ArgumentCaptor<WebhookDeliveryTask> captor = ArgumentCaptor.forClass(WebhookDeliveryTask.class);
        verify(taskRepository).save(captor.capture());
        return captor.getValue();
    }
}