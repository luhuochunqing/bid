package com.xiyu.bid.webhook.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.integration.external.ExternalSystemPrefix;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.webhook.domain.TenderStatusChangedEvent;
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
    @Mock private TenderCrmOpportunityCodeResolver tenderCrmOpportunityCodeResolver;
    @Mock private OperatorUsernameResolver operatorUsernameResolver;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private WebhookEventListener listener() {
        configureDefaultAnswers();
        WebhookEventListener l = new WebhookEventListener(taskRepository, tenderRepository, objectMapper, tenderCrmOpportunityCodeResolver, operatorUsernameResolver);
        ReflectionTestUtils.setField(l, "crmWebhookUrl", CRM_URL);
        return l;
    }

    private WebhookEventListener listenerWithoutUrl() {
        configureDefaultAnswers();
        WebhookEventListener l = new WebhookEventListener(taskRepository, tenderRepository, objectMapper, tenderCrmOpportunityCodeResolver, operatorUsernameResolver);
        ReflectionTestUtils.setField(l, "crmWebhookUrl", "");
        return l;
    }

    private void configureDefaultAnswers() {
        // Default: 按 tender.crm_opportunity_id 原样返回；外部推送兜底反查由单独测试覆盖
        lenient().when(tenderCrmOpportunityCodeResolver.resolveForTender(any(Tender.class), any()))
                .thenAnswer(inv -> {
                    Tender t = inv.getArgument(0);
                    String crmId = t.getCrmOpportunityId();
                    return (crmId == null || crmId.isBlank()) ? "" : crmId;
                });

        // CO-576: 默认 resolveForCrmLookup 返回非空 username，使大多数测试仍能入队
        lenient().when(operatorUsernameResolver.resolveForCrmLookup(any(Tender.class), any()))

                .thenReturn("default-operator");
    }

    private TenderStatusChangedEvent event(Tender.Status newStatus, String abandonReason, String operatorName) {
        return TenderStatusChangedEvent.of(
                TENDER_ID, ExternalSystemPrefix.CRM.formatExternalId("254"), Tender.Status.TRACKING, newStatus, "西域集团招标",
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
    @DisplayName("ABANDONED -> 入队，bidInfoList 格式，crmStatus=6，feedback remark 为空（CO-568）+ systemName")
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
        // CO-568: 弃标时 remark 置空，弃标原因改由 abandonmentReason 独立字段承载
        assertThat(feedback.path("remark").asText()).isEmpty();
        // CO-414: abandonmentReason 独立字段，值为用户填写的弃标原因
        assertThat(feedback.path("abandonmentReason").asText()).isEqualTo("客户预算过低，放弃投标");
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
        // CO-414: 非 弃标场景 abandonmentReason 为空字符串
        assertThat(feedback.path("abandonmentReason").asText()).isEmpty();
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
        when(tenderCrmOpportunityCodeResolver.resolveForTender(tender, "default-operator")).thenReturn("CC20260621321");

        l.onTenderStatusChanged(event(Tender.Status.ABANDONED, "放弃投标", "张三"));

        WebhookDeliveryTask saved = captureSingleSaved();
        JsonNode root = objectMapper.readTree(saved.getPayload());
        JsonNode bidInfo = root.path("bidInfoList").get(0);
        assertThat(bidInfo.path("code").asText()).isEqualTo("CC20260621321");
    }

    @Test
    @DisplayName("crmOpportunityId 为空但 externalId 是 CRM:sourceId 时，使用 resolveFromTender 兜底反查 code")
    void emptyCrmOpportunityId_withCrmExternalId_resolvesFromExternalId() throws Exception {
        WebhookEventListener l = listener();
        Tender tender = new Tender();
        tender.setId(TENDER_ID);
        tender.setExternalId(ExternalSystemPrefix.CRM.formatExternalId("17"));
        tender.setCrmOpportunityId(null);
        when(tenderRepository.findById(TENDER_ID)).thenReturn(Optional.of(tender));
        when(tenderCrmOpportunityCodeResolver.resolveForTender(tender, "default-operator")).thenReturn("CC2026070932");

        l.onTenderStatusChanged(event(Tender.Status.ABANDONED, "放弃投标", "张三"));

        WebhookDeliveryTask saved = captureSingleSaved();
        JsonNode root = objectMapper.readTree(saved.getPayload());
        JsonNode bidInfo = root.path("bidInfoList").get(0);
        assertThat(bidInfo.path("code").asText()).isEqualTo("CC2026070932");
    }

    @Test
    @DisplayName("CO-152: operatorId 命中用户 → resolveForTender 使用真实 username")
    void operatorResolved_passesUsernameToCodeResolver() {
        WebhookEventListener l = listener();
        Tender tender = new Tender();
        tender.setId(TENDER_ID);
        tender.setCrmOpportunityId(null);
        tender.setExternalId(ExternalSystemPrefix.CRM.formatExternalId("17"));
        when(tenderRepository.findById(TENDER_ID)).thenReturn(Optional.of(tender));
        when(operatorUsernameResolver.resolveForCrmLookup(tender, 493L)).thenReturn("zhangsan");

        l.onTenderStatusChanged(event(Tender.Status.ABANDONED, "放弃投标", "张三"));

        verify(tenderCrmOpportunityCodeResolver).resolveForTender(tender, "zhangsan");
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

    @Test
    @DisplayName("CO-576 Phase B: 无可用 username → 不入队（避免空 username 静默死信）")
    void evaluated_noUsername_doesNotEnqueue() {
        WebhookEventListener l = listener();
        Tender tender = mockTender();
        when(tenderRepository.findById(TENDER_ID)).thenReturn(Optional.of(tender));
        when(operatorUsernameResolver.resolveForCrmLookup(tender, 493L)).thenReturn(null);

        l.onTenderStatusChanged(event(Tender.Status.EVALUATED, null, "王五"));

        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("CO-576: event operator 为 admin 时优先用 PM 的 username（resolveForCrmLookup）")
    void evaluated_usesProjectManagerWhenCreatorIsAdmin() {

        WebhookEventListener l = listener();
        Tender tender = mockTender();
        tender.setProjectManagerId(100L);
        when(tenderRepository.findById(TENDER_ID)).thenReturn(Optional.of(tender));
        // resolveForCrmLookup 应被调用，返回 projectManager 的 username（PM 是 OSS 用户有 token）
        when(operatorUsernameResolver.resolveForCrmLookup(tender, 493L)).thenReturn("pm-user");

        l.onTenderStatusChanged(event(Tender.Status.EVALUATED, null, "admin"));

        verify(operatorUsernameResolver).resolveForCrmLookup(tender, 493L);
        verify(tenderCrmOpportunityCodeResolver).resolveForTender(tender, "pm-user");
    }

    private WebhookDeliveryTask captureSingleSaved() {
        ArgumentCaptor<WebhookDeliveryTask> captor = ArgumentCaptor.forClass(WebhookDeliveryTask.class);
        verify(taskRepository).save(captor.capture());
        return captor.getValue();
    }
}