package com.xiyu.bid.webhook.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.webhook.domain.TenderStatusChangedEvent;
import com.xiyu.bid.webhook.domain.TenderStatusChangedPayload;
import com.xiyu.bid.webhook.infrastructure.WebhookDeliveryTask;
import com.xiyu.bid.webhook.infrastructure.WebhookDeliveryTaskRepository;
import com.xiyu.bid.webhook.infrastructure.WebhookDeliveryTaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * WebhookEventListener 单元测试（§4.1 v3.8 契约）。
 * <p>覆盖：
 * <ul>
 *   <li>触发时机：仅 BIDDING 和 ABANDONED 入队；WON/LOST/TRACKING 跳过（v3.8 变更）。</li>
 *   <li>载荷符合 §4.1 tender.status_changed 事件契约（event/tenderId/sourceId/oldStatus/newStatus/
 *       title/occurredAt/operatorId/operatorName/abandonReason/recommendation）。</li>
 *   <li>sourceId 从 externalId 冒号后提取。</li>
 *   <li>abandonReason 仅弃标时填值，其他状态省略。</li>
 *   <li>recommendation 仅有时填值，无评估时省略。</li>
 *   <li>occurredAt 为 ISO 8601 格式（yyyy-MM-ddTHH:mm:ss+08:00）。</li>
 *   <li>crmWebhookUrl 未配置时跳过。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WebhookEventListener — §4.1 v3.8 tender.status_changed 契约")
class WebhookEventListenerTest {

    private static final String CRM_URL = "https://crm.example.com/api/tender/status-callback";
    private static final Long TENDER_ID = 254L;
    private static final String EXTERNAL_ID = "CRM:OPP-2026-00918";
    private static final String TITLE = "西域集团2026年度MRO物料采购招标项目";

    @Mock private WebhookDeliveryTaskRepository taskRepository;

    private WebhookEventListener listener() {
        WebhookEventListener l = new WebhookEventListener(taskRepository, new ObjectMapper());
        ReflectionTestUtils.setField(l, "crmWebhookUrl", CRM_URL);
        return l;
    }

    private WebhookEventListener listenerWithoutUrl() {
        WebhookEventListener l = new WebhookEventListener(taskRepository, new ObjectMapper());
        ReflectionTestUtils.setField(l, "crmWebhookUrl", "");
        return l;
    }

    private TenderStatusChangedEvent event(Tender.Status newStatus, String abandonReason,
                                           String operatorName, Boolean shouldBid, String reason) {
        return TenderStatusChangedEvent.of(
                TENDER_ID, EXTERNAL_ID, Tender.Status.TRACKING, newStatus, TITLE,
                abandonReason, 493L, operatorName, shouldBid, reason);
    }

    @Test
    @DisplayName("BIDDING -> 入队，载荷含 event/tenderId/sourceId/oldStatus/newStatus/title/occurredAt/operatorId/operatorName")
    void bidding_enqueuesWithFullPayload() throws Exception {
        WebhookEventListener l = listener();

        l.onTenderStatusChanged(event(Tender.Status.BIDDING, null, "张三", null, null));

        WebhookDeliveryTask saved = captureSingleSaved();
        assertThat(saved.getStatus()).isEqualTo(WebhookDeliveryTaskStatus.PENDING);
        assertThat(saved.getTargetUrl()).isEqualTo(CRM_URL);
        assertThat(saved.getEventType()).isEqualTo("tender.status_changed");

        TenderStatusChangedPayload payload = parsePayload(saved.getPayload());
        assertThat(payload.event()).isEqualTo("tender.status_changed");
        assertThat(payload.tenderId()).isEqualTo(TENDER_ID);
        assertThat(payload.sourceId()).isEqualTo("OPP-2026-00918");
        assertThat(payload.oldStatus()).isEqualTo("TRACKING");
        assertThat(payload.newStatus()).isEqualTo("BIDDING");
        assertThat(payload.title()).isEqualTo(TITLE);
        assertThat(payload.operatorId()).isEqualTo(493L);
        assertThat(payload.operatorName()).isEqualTo("张三");
        assertThat(payload.occurredAt()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\+08:00");
        // BIDDING 不含 abandonReason
        assertThat(payload.abandonReason()).isNull();
        // 无评估建议
        assertThat(payload.recommendation()).isNull();
    }

    @Test
    @DisplayName("ABANDONED -> 入队，载荷含 abandonReason")
    void abandoned_enqueuesWithAbandonReason() throws Exception {
        WebhookEventListener l = listener();

        l.onTenderStatusChanged(event(Tender.Status.ABANDONED, "客户预算过低，放弃投标", "李四", null, null));

        WebhookDeliveryTask saved = captureSingleSaved();
        TenderStatusChangedPayload payload = parsePayload(saved.getPayload());
        assertThat(payload.newStatus()).isEqualTo("ABANDONED");
        assertThat(payload.abandonReason()).isEqualTo("客户预算过低，放弃投标");
        assertThat(payload.operatorName()).isEqualTo("李四");
    }

    @Test
    @DisplayName("ABANDONED + 评估建议 -> 载荷含 recommendation（shouldBid + reason）")
    void abandonedWithRecommendation_includesRecommendation() throws Exception {
        WebhookEventListener l = listener();

        l.onTenderStatusChanged(event(Tender.Status.ABANDONED, "放弃", "张三", false, "项目风险过高"));

        WebhookDeliveryTask saved = captureSingleSaved();
        TenderStatusChangedPayload payload = parsePayload(saved.getPayload());
        assertThat(payload.recommendation()).isNotNull();
        assertThat(payload.recommendation().shouldBid()).isFalse();
        assertThat(payload.recommendation().reason()).isEqualTo("项目风险过高");
    }

    @Test
    @DisplayName("BIDDING + 评估建议 -> 载荷含 recommendation（shouldBid=true）")
    void biddingWithRecommendation_includesRecommendation() throws Exception {
        WebhookEventListener l = listener();

        l.onTenderStatusChanged(event(Tender.Status.BIDDING, null, "张三", true, "项目匹配度高"));

        WebhookDeliveryTask saved = captureSingleSaved();
        TenderStatusChangedPayload payload = parsePayload(saved.getPayload());
        assertThat(payload.recommendation()).isNotNull();
        assertThat(payload.recommendation().shouldBid()).isTrue();
        assertThat(payload.recommendation().reason()).isEqualTo("项目匹配度高");
    }

    @Test
    @DisplayName("LOST -> 不入队（v3.8：LOST 改由 §4.2 项目结果确认回调承担）")
    void lost_notEnqueued() {
        WebhookEventListener l = listener();

        l.onTenderStatusChanged(event(Tender.Status.LOST, null, "王五", null, null));

        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("WON -> 不入队（v3.8：WON 改由 §4.2 项目结果确认回调承担）")
    void won_notEnqueued() {
        WebhookEventListener l = listener();

        l.onTenderStatusChanged(event(Tender.Status.WON, null, "赵六", null, null));

        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("TRACKING 中间态 -> 不入队")
    void tracking_notEnqueued() {
        WebhookEventListener l = listener();

        l.onTenderStatusChanged(event(Tender.Status.TRACKING, null, "张三", null, null));

        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("crmWebhookUrl 未配置 -> 不入队")
    void emptyUrl_notEnqueued() {
        WebhookEventListener l = listenerWithoutUrl();

        l.onTenderStatusChanged(event(Tender.Status.ABANDONED, "放弃投标", "张三", null, null));

        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("sourceId 从 externalId 冒号后提取")
    void sourceId_extractedFromExternalId() throws Exception {
        WebhookEventListener l = listener();

        l.onTenderStatusChanged(event(Tender.Status.BIDDING, null, "张三", null, null));

        WebhookDeliveryTask saved = captureSingleSaved();
        TenderStatusChangedPayload payload = parsePayload(saved.getPayload());
        assertThat(payload.sourceId()).isEqualTo("OPP-2026-00918");
    }

    @Test
    @DisplayName("externalId 无冒号 -> sourceId 为空字符串")
    void noColonInExternalId_emptySourceId() throws Exception {
        WebhookEventListener l = listener();
        var event = TenderStatusChangedEvent.of(
                TENDER_ID, "NO_COLON_HERE", Tender.Status.TRACKING, Tender.Status.BIDDING, TITLE,
                null, 493L, "张三", null, null);

        l.onTenderStatusChanged(event);

        WebhookDeliveryTask saved = captureSingleSaved();
        TenderStatusChangedPayload payload = parsePayload(saved.getPayload());
        assertThat(payload.sourceId()).isEmpty();
    }

    @Test
    @DisplayName("序列化后的 JSON 包含所有 §4.1 字段（abandonReason/recommendation 按需出现）")
    void jsonPayload_containsAllFields() throws Exception {
        WebhookEventListener l = listener();

        l.onTenderStatusChanged(event(Tender.Status.ABANDONED, "弃标原因", "张三", false, "不建议"));

        WebhookDeliveryTask saved = captureSingleSaved();
        JsonNode root = new ObjectMapper().readTree(saved.getPayload());

        assertThat(root.has("event")).isTrue();
        assertThat(root.has("tenderId")).isTrue();
        assertThat(root.has("sourceId")).isTrue();
        assertThat(root.has("oldStatus")).isTrue();
        assertThat(root.has("newStatus")).isTrue();
        assertThat(root.has("title")).isTrue();
        assertThat(root.has("occurredAt")).isTrue();
        assertThat(root.has("operatorId")).isTrue();
        assertThat(root.has("operatorName")).isTrue();
        assertThat(root.has("abandonReason")).isTrue();
        assertThat(root.has("recommendation")).isTrue();
        // recommendation 子字段
        JsonNode rec = root.path("recommendation");
        assertThat(rec.has("shouldBid")).isTrue();
        assertThat(rec.has("reason")).isTrue();
    }

    @Test
    @DisplayName("BIDDING 序列化后不含 abandonReason（@JsonInclude.NON_NULL）")
    void biddingJson_omitsAbandonReason() throws Exception {
        WebhookEventListener l = listener();

        l.onTenderStatusChanged(event(Tender.Status.BIDDING, null, "张三", null, null));

        WebhookDeliveryTask saved = captureSingleSaved();
        JsonNode root = new ObjectMapper().readTree(saved.getPayload());
        assertThat(root.has("abandonReason")).isFalse();
        assertThat(root.has("recommendation")).isFalse();
    }

    private WebhookDeliveryTask captureSingleSaved() {
        ArgumentCaptor<WebhookDeliveryTask> captor = ArgumentCaptor.forClass(WebhookDeliveryTask.class);
        verify(taskRepository).save(captor.capture());
        return captor.getValue();
    }

    private TenderStatusChangedPayload parsePayload(String json) throws Exception {
        return new ObjectMapper().readValue(json, TenderStatusChangedPayload.class);
    }
}
