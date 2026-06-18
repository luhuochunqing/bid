package com.xiyu.bid.webhook.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.crm.infrastructure.dto.BidInfoInnerDTO;
import com.xiyu.bid.crm.infrastructure.dto.BidInfoSyncDTO;
import com.xiyu.bid.entity.Tender;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WebhookEventListener 单元测试。
 * <p>覆盖：终态过滤（ABANDONED/WON/LOST 入队；中间态跳过）、payload 符合 BidInfoSyncDTO 契约、
 * crmWebhookUrl 未配置时跳过、入队 status=PENDING、code/name 取自 crm_opportunity_id/name。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WebhookEventListener — bidInfoSync contract + terminal-state filter")
class WebhookEventListenerTest {

    private static final String CRM_URL = "https://crm-test.ehsy.com/customer-chance/bidInfoSync";

    @Mock private WebhookDeliveryTaskRepository taskRepository;
    @Mock private TenderRepository tenderRepository;

    private WebhookEventListener listener() {
        WebhookEventListener l = new WebhookEventListener(taskRepository, tenderRepository, new ObjectMapper());
        ReflectionTestUtils.setField(l, "crmWebhookUrl", CRM_URL);
        return l;
    }

    private WebhookEventListener listenerWithoutUrl() {
        WebhookEventListener l = new WebhookEventListener(taskRepository, tenderRepository, new ObjectMapper());
        ReflectionTestUtils.setField(l, "crmWebhookUrl", "");
        return l;
    }

    private TenderStatusChangedEvent event(Tender.Status newStatus, String abandonReason, String operatorName) {
        return TenderStatusChangedEvent.of(
                254L, "CRM:224", Tender.Status.TRACKING, newStatus, "1728CRM商机",
                abandonReason, 493L, operatorName, null, null);
    }

    /**
     * mock tenderRepository.findById 返回指定商机关联的 tender。
     *
     * @param crmOpportunityId   商机编号（code），null 表示未关联商机
     * @param crmOpportunityName 商机名称，null 表示未关联
     */
    private void mockTender(String crmOpportunityId, String crmOpportunityName) {
        Tender tender = new Tender();
        tender.setId(254L);
        tender.setCrmOpportunityId(crmOpportunityId);
        tender.setCrmOpportunityName(crmOpportunityName);
        when(tenderRepository.findById(254L)).thenReturn(Optional.of(tender));
    }

    @Test
    @DisplayName("ABANDONED -> enqueue PENDING, payload status=6 (弃标), code/name 取自 crm_opportunity_id/name")
    void abandoned_enqueuesWithStatus6() throws Exception {
        WebhookEventListener l = listener();
        mockTender("CC20260610180", "海化集团年度MRO采购");
        var event = event(Tender.Status.ABANDONED, "放弃投标", "张三");

        l.onTenderStatusChanged(event);

        WebhookDeliveryTask saved = captureSingleSaved();
        assertThat(saved.getStatus()).isEqualTo(WebhookDeliveryTaskStatus.PENDING);
        assertThat(saved.getTargetUrl()).isEqualTo(CRM_URL);
        assertThat(saved.getEventType()).isEqualTo("tender.status_changed");
        BidInfoSyncDTO dto = new ObjectMapper().readValue(saved.getPayload(), BidInfoSyncDTO.class);
        BidInfoInnerDTO inner = dto.bidInfoList().get(0);
        assertThat(inner.status()).isEqualTo(6);
        assertThat(inner.code()).isEqualTo("CC20260610180");
        assertThat(inner.name()).isEqualTo("海化集团年度MRO采购");
        assertThat(inner.statusEditor()).isEqualTo("张三");
        assertThat(inner.feedback()).contains("放弃投标").contains("张三");
    }

    @Test
    @DisplayName("WON -> enqueue, payload status=2 (中标)")
    void won_enqueuesWithStatus2() throws Exception {
        WebhookEventListener l = listener();
        mockTender("CC20260610180", "商机名");

        l.onTenderStatusChanged(event(Tender.Status.WON, null, "李四"));

        WebhookDeliveryTask saved = captureSingleSaved();
        BidInfoInnerDTO inner = new ObjectMapper()
                .readValue(saved.getPayload(), BidInfoSyncDTO.class).bidInfoList().get(0);
        assertThat(inner.status()).isEqualTo(2);
        assertThat(inner.statusEditor()).isEqualTo("李四");
    }

    @Test
    @DisplayName("LOST -> enqueue, payload status=3 (丢标)")
    void lost_enqueuesWithStatus3() throws Exception {
        WebhookEventListener l = listener();
        mockTender("CC20260610180", "商机名");

        l.onTenderStatusChanged(event(Tender.Status.LOST, null, "王五"));

        WebhookDeliveryTask saved = captureSingleSaved();
        BidInfoInnerDTO inner = new ObjectMapper()
                .readValue(saved.getPayload(), BidInfoSyncDTO.class).bidInfoList().get(0);
        assertThat(inner.status()).isEqualTo(3);
    }

    @Test
    @DisplayName("BIDDING 中间态 -> 不入队")
    void bidding_notEnqueued() {
        WebhookEventListener l = listener();

        l.onTenderStatusChanged(event(Tender.Status.BIDDING, null, "张三"));

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
    @DisplayName("statusEditTime 格式 yyyy-MM-dd HH:mm:ss")
    void statusEditTime_format() throws Exception {
        WebhookEventListener l = listener();
        mockTender("CC20260610180", "商机名");
        LocalDateTime occurred = LocalDateTime.of(2026, 6, 18, 17, 29, 54);
        var event = TenderStatusChangedEvent.of(
                254L, "CRM:224", Tender.Status.TRACKING, Tender.Status.ABANDONED, "标题",
                "放弃投标", 493L, "张三", null, null);
        // occurredAt 在 of() 内部取 now()，无法注入；此处仅校验格式为 yyyy-MM-dd HH:mm:ss
        l.onTenderStatusChanged(event);

        WebhookDeliveryTask saved = captureSingleSaved();
        BidInfoInnerDTO inner = new ObjectMapper()
                .readValue(saved.getPayload(), BidInfoSyncDTO.class).bidInfoList().get(0);
        // 格式应为 yyyy-MM-dd HH:mm:ss（19 字符，含日期+时间，无 T 和纳秒）
        assertThat(inner.statusEditTime()).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");
    }

    @Test
    @DisplayName("crm_opportunity_id 为 NULL（未关联商机）-> code/name 填空，被 CRM 接受")
    void noCrmOpportunity_codeAndNameEmpty() throws Exception {
        WebhookEventListener l = listener();
        mockTender(null, null);

        l.onTenderStatusChanged(event(Tender.Status.ABANDONED, "测试", "郑蓉蓉"));

        WebhookDeliveryTask saved = captureSingleSaved();
        BidInfoInnerDTO inner = new ObjectMapper()
                .readValue(saved.getPayload(), BidInfoSyncDTO.class).bidInfoList().get(0);
        assertThat(inner.code()).isEmpty();
        assertThat(inner.name()).isEmpty();
        assertThat(inner.status()).isEqualTo(6);
    }

    @Test
    @DisplayName("tender 不存在 -> 不入队（防御性：避免 NPE）")
    void tenderNotFound_notEnqueued() {
        WebhookEventListener l = listener();
        when(tenderRepository.findById(anyLong())).thenReturn(Optional.empty());

        l.onTenderStatusChanged(event(Tender.Status.ABANDONED, "放弃", "张三"));

        verify(taskRepository, never()).save(any());
    }

    private WebhookDeliveryTask captureSingleSaved() {
        ArgumentCaptor<WebhookDeliveryTask> captor = ArgumentCaptor.forClass(WebhookDeliveryTask.class);
        verify(taskRepository).save(captor.capture());
        return captor.getValue();
    }
}
