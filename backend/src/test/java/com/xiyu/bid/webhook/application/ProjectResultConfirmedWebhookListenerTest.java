package com.xiyu.bid.webhook.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.crm.infrastructure.dto.ProjectResultCallbackPayload;
import com.xiyu.bid.project.core.BidResultType;
import com.xiyu.bid.project.domain.ProjectResultConfirmedEvent;
import com.xiyu.bid.project.service.ProjectResultPayloadAssembler;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ProjectResultConfirmedWebhookListener 单元测试。
 * <p>覆盖：入队 PENDING、event_type=project.result_confirmed、payload 序列化正确、
 * url 未配置跳过、assembler 返回 null 跳过。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProjectResultConfirmedWebhookListener — §4.2 入队逻辑")
class ProjectResultConfirmedWebhookListenerTest {

    private static final String CRM_URL = "https://crm.example.com/api/result-callback";
    private static final Long PROJECT_ID = 9001L;
    private static final Long TENDER_ID = 254L;
    private static final Long USER_ID = 493L;
    private static final Long RESULT_ID = 7700L;

    @Mock private WebhookDeliveryTaskRepository taskRepository;
    @Mock private ProjectResultPayloadAssembler payloadAssembler;

    private ProjectResultConfirmedWebhookListener listener(String url) {
        ProjectResultConfirmedWebhookListener l = new ProjectResultConfirmedWebhookListener(
                taskRepository, payloadAssembler, new ObjectMapper());
        ReflectionTestUtils.setField(l, "crmWebhookUrl", url);
        return l;
    }

    private ProjectResultConfirmedEvent event() {
        return ProjectResultConfirmedEvent.of(
                PROJECT_ID, TENDER_ID, BidResultType.WON, List.of(1032L),
                List.of(new ProjectResultConfirmedEvent.CompetitorSnapshot(
                        "京东企业购", "95折", "月结60天", "含仓储")),
                USER_ID, RESULT_ID);
    }

    private ProjectResultCallbackPayload payload() {
        return new ProjectResultCallbackPayload(
                TENDER_ID, PROJECT_ID, "CRM-OPP-2026-0510-003", "WON",
                List.of(), List.of(), "西域数智化投标管理平台",
                "张三", "EMP001", "2026-06-17T14:30:00+08:00");
    }

    @Test
    @DisplayName("正常入队：event_type=project.result_confirmed, status=PENDING, targetUrl=配置值")
    void normalEnqueue() throws Exception {
        when(payloadAssembler.assemble(any())).thenReturn(payload());

        listener(CRM_URL).onProjectResultConfirmed(event());

        WebhookDeliveryTask saved = captureSaved();
        assertThat(saved.getStatus()).isEqualTo(WebhookDeliveryTaskStatus.PENDING);
        assertThat(saved.getEventType()).isEqualTo("project.result_confirmed");
        assertThat(saved.getTargetUrl()).isEqualTo(CRM_URL);
        assertThat(saved.getTenderId()).isEqualTo(TENDER_ID);
        assertThat(saved.getBusinessKey()).contains("WON");

        JsonNode root = new ObjectMapper().readTree(saved.getPayload());
        assertThat(root.has("tenderId")).isTrue();
        assertThat(root.has("projectId")).isTrue();
        assertThat(root.has("bidResult")).isTrue();
        assertThat(root.has("systemName")).isTrue();
        assertThat(root.path("systemName").asText()).isEqualTo("西域数智化投标管理平台");
    }

    @Test
    @DisplayName("webhook.crm.url 未配置 -> 不入队")
    void urlNotConfigured_skip() {
        listener("").onProjectResultConfirmed(event());

        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("assembler 返回 null（tender 查不到）-> 不入队")
    void assemblerReturnsNull_skip() {
        when(payloadAssembler.assemble(any())).thenReturn(null);

        listener(CRM_URL).onProjectResultConfirmed(event());

        verify(taskRepository, never()).save(any());
    }

    private WebhookDeliveryTask captureSaved() {
        ArgumentCaptor<WebhookDeliveryTask> captor = ArgumentCaptor.forClass(WebhookDeliveryTask.class);
        verify(taskRepository).save(captor.capture());
        return captor.getValue();
    }
}
