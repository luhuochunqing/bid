package com.xiyu.bid.webhook.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.integration.external.ExternalSystemPrefix;
import com.xiyu.bid.project.core.BidResultType;
import com.xiyu.bid.project.domain.ProjectResultConfirmedEvent;
import com.xiyu.bid.project.service.ProjectResultPayloadAssembler;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ProjectResultConfirmedWebhookListener 单元测试。
 * <p>覆盖：bidInfoList 格式入队、event_type=project.result_confirmed、
 * CRM status 映射正确（WON→2, LOST→3, FAILED→4）、url 未配置跳过、
 * tender 不存在跳过、operator_username 从 operatorUserId 反查写入。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ProjectResultConfirmedWebhookListener — §4.2 bidInfoList 入队逻辑")
class ProjectResultConfirmedWebhookListenerTest {

    private static final String CRM_URL = "https://crm.example.com/customer-chance/bidInfoSync";
    private static final Long PROJECT_ID = 9001L;
    private static final Long TENDER_ID = 254L;
    private static final Long USER_ID = 493L;
    private static final String OPERATOR_USERNAME = "06234";
    private static final String OPERATOR_NAME = "张三（06234）";
    private static final Long RESULT_ID = 7700L;
    private static final String CRM_OPPORTUNITY_CODE = "CC20260610180";
    private static final String CRM_OPPORTUNITY_NAME = "西域五金年度框架协议";

    @Mock private WebhookDeliveryTaskRepository taskRepository;
    @Mock private TenderRepository tenderRepository;
    @Mock private TenderCrmOpportunityCodeResolver tenderCrmOpportunityCodeResolver;
    @Mock private ProjectDocumentRepository projectDocumentRepository;
    @Mock private UserRepository userRepository;
    @Mock private OperatorUsernameResolver operatorUsernameResolver;

    private ProjectResultConfirmedWebhookListener listener(String url) {
        ObjectMapper objectMapper = new ObjectMapper();
        ProjectResultPayloadAssembler assembler = new ProjectResultPayloadAssembler(
                tenderRepository, userRepository, projectDocumentRepository, objectMapper);
        ProjectResultConfirmedWebhookListener l = new ProjectResultConfirmedWebhookListener(
                taskRepository, tenderRepository, objectMapper,
                tenderCrmOpportunityCodeResolver, assembler, operatorUsernameResolver);
        ReflectionTestUtils.setField(l, "crmWebhookUrl", url);
        return l;
    }

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        // Default: 按 tender.crm_opportunity_id 原样返回；外部推送兜底反查由单独测试覆盖
        lenient().when(tenderCrmOpportunityCodeResolver.resolveForTender(any(Tender.class), any()))
                .thenAnswer(inv -> {
                    Tender t = inv.getArgument(0);
                    String crmId = t.getCrmOpportunityId();
                    return (crmId == null || crmId.isBlank()) ? "" : crmId;
                });
        // CO-571 Phase B: 默认 resolveDeliveryUsername 返回非空 username
        lenient().when(operatorUsernameResolver.resolveDeliveryUsername(any(Tender.class), any()))
                .thenReturn(OPERATOR_USERNAME);
    }

    private Tender tender() {
        Tender t = new Tender();
        t.setId(TENDER_ID);
        t.setCrmOpportunityId(CRM_OPPORTUNITY_CODE);
        t.setCrmOpportunityName(CRM_OPPORTUNITY_NAME);
        return t;
    }

    private ProjectResultConfirmedEvent event(BidResultType resultType) {
        return ProjectResultConfirmedEvent.of(
                PROJECT_ID, TENDER_ID, resultType, "", List.of(1032L),
                List.of(new ProjectResultConfirmedEvent.CompetitorSnapshot(
                        "京东企业购", "95折", "月结60天", "含仓储")),
                USER_ID, OPERATOR_NAME, RESULT_ID);
    }

    @Test
    @DisplayName("WON → CRM status=2, bidInfoList 格式正确")
    void won_mapsToCrmStatus2() throws Exception {
        when(tenderRepository.findById(TENDER_ID)).thenReturn(Optional.of(tender()));
        ProjectDocument doc = ProjectDocument.builder()
                .id(1032L).projectId(PROJECT_ID)
                .name("中标通知书-中石化MRO采购框架协议.pdf")
                .size("2048000")
                .fileUrl("https://bid.xiyu.com/api/projects/128/documents/1032/download")
                .uploaderName("张三").build();
        when(projectDocumentRepository.findAllById(List.of(1032L))).thenReturn(List.of(doc));

        listener(CRM_URL).onProjectResultConfirmed(event(BidResultType.WON));

        WebhookDeliveryTask saved = captureSaved();
        assertThat(saved.getStatus()).isEqualTo(WebhookDeliveryTaskStatus.PENDING);
        assertThat(saved.getEventType()).isEqualTo("project.result_confirmed");
        assertThat(saved.getTargetUrl()).isEqualTo(CRM_URL);
        assertThat(saved.getTenderId()).isEqualTo(TENDER_ID);
        assertThat(saved.getBusinessKey()).contains("WON");
        // CO-152: §4.2 入队必须写入 operator_username，异步投递才能走用户 OSS token
        assertThat(saved.getOperatorUsername()).isEqualTo(OPERATOR_USERNAME);

        JsonNode root = new ObjectMapper().readTree(saved.getPayload());
        assertThat(root.has("bidInfoList")).isTrue();
        JsonNode inner = root.path("bidInfoList").get(0);
        assertThat(inner.path("code").asText()).isEqualTo(CRM_OPPORTUNITY_CODE);
        assertThat(inner.path("name").asText()).isEqualTo(CRM_OPPORTUNITY_NAME);
        assertThat(inner.path("status").asInt()).isEqualTo(2);
        assertThat(inner.path("statusEditor").asText()).isEqualTo(OPERATOR_NAME);
        assertThat(inner.path("statusEditTime").asText()).isNotEmpty();
        // feedback 包含 resultType 和竞争对手
        JsonNode feedback = new ObjectMapper().readTree(inner.path("feedback").asText());
        assertThat(feedback.path("reason").asText()).isEqualTo("WON");
        assertThat(feedback.path("vendor").asText()).isEqualTo("京东企业购");
        assertThat(feedback.path("operator").asText()).isEqualTo(OPERATOR_NAME);
        // CO-300: evidenceFiles, competitors, systemName
        assertThat(feedback.has("evidenceFiles")).isTrue();
        assertThat(feedback.path("evidenceFiles").isArray()).isTrue();
        assertThat(feedback.path("evidenceFiles").size()).isEqualTo(1);
        assertThat(feedback.path("evidenceFiles").get(0).path("fileName").asText())
                .isEqualTo("中标通知书-中石化MRO采购框架协议.pdf");
        assertThat(feedback.path("evidenceFiles").get(0).path("fileUrl").asText())
                .isEqualTo("https://bid.xiyu.com/api/projects/128/documents/1032/download");
        assertThat(feedback.path("evidenceFiles").get(0).path("fileSize").asLong()).isEqualTo(2048000L);
        assertThat(feedback.has("competitors")).isTrue();
        assertThat(feedback.path("competitors").isArray()).isTrue();
        assertThat(feedback.path("competitors").size()).isEqualTo(1);
        assertThat(feedback.path("competitors").get(0).path("name").asText()).isEqualTo("京东企业购");
        assertThat(feedback.path("competitors").get(0).path("discount").asText()).isEqualTo("95折");
        assertThat(feedback.path("competitors").get(0).path("paymentTerm").asText()).isEqualTo("月结60天");
        assertThat(feedback.path("competitors").get(0).path("notes").asText()).isEqualTo("含仓储");
        assertThat(feedback.path("systemName").asText()).isEqualTo("投标管理系统");
    }

    @Test
    @DisplayName("LOST → CRM status=3")
    void lost_mapsToCrmStatus3() throws Exception {
        when(tenderRepository.findById(TENDER_ID)).thenReturn(Optional.of(tender()));
        when(projectDocumentRepository.findAllById(List.of(1032L))).thenReturn(List.of());

        listener(CRM_URL).onProjectResultConfirmed(event(BidResultType.LOST));

        WebhookDeliveryTask saved = captureSaved();
        JsonNode root = new ObjectMapper().readTree(saved.getPayload());
        assertThat(root.path("bidInfoList").get(0).path("status").asInt()).isEqualTo(3);
    }

    @Test
    @DisplayName("FAILED → CRM status=4")
    void failed_mapsToCrmStatus4() throws Exception {
        when(tenderRepository.findById(TENDER_ID)).thenReturn(Optional.of(tender()));
        when(projectDocumentRepository.findAllById(List.of(1032L))).thenReturn(List.of());

        listener(CRM_URL).onProjectResultConfirmed(event(BidResultType.FAILED));

        WebhookDeliveryTask saved = captureSaved();
        JsonNode root = new ObjectMapper().readTree(saved.getPayload());
        assertThat(root.path("bidInfoList").get(0).path("status").asInt()).isEqualTo(4);
    }

    @Test
    @DisplayName("webhook.crm.url 未配置 → 不入队")
    void urlNotConfigured_skip() {
        listener("").onProjectResultConfirmed(event(BidResultType.WON));

        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("tender 不存在 → 不入队")
    void tenderNotFound_skip() {
        when(tenderRepository.findById(TENDER_ID)).thenReturn(Optional.empty());

        listener(CRM_URL).onProjectResultConfirmed(event(BidResultType.WON));

        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("tender 无 CRM 商机关联 → code/name 填空字符串，仍入队")
    void tenderWithoutCrmOpportunity_enqueuesWithEmptyCode() throws Exception {
        Tender t = tender();
        t.setCrmOpportunityId(null);
        t.setCrmOpportunityName(null);
        when(tenderRepository.findById(TENDER_ID)).thenReturn(Optional.of(t));
        when(projectDocumentRepository.findAllById(List.of(1032L))).thenReturn(List.of());

        listener(CRM_URL).onProjectResultConfirmed(event(BidResultType.WON));

        WebhookDeliveryTask saved = captureSaved();
        JsonNode root = new ObjectMapper().readTree(saved.getPayload());
        JsonNode inner = root.path("bidInfoList").get(0);
        assertThat(inner.path("code").asText()).isEmpty();
        assertThat(inner.path("name").asText()).isEmpty();
    }

    @Test
    @DisplayName("crm_opportunity_id 是纯数字 id → 使用 operatorUsername 调用 CRM 反查 code，payload 用 CC 前缀格式")
    void crmOpportunityIdIsNumeric_resolvesToCodeViaCrm() throws Exception {
        Tender t = tender();
        t.setCrmOpportunityId("20942"); // 数字 id（CO-277 场景）
        when(tenderRepository.findById(TENDER_ID)).thenReturn(Optional.of(t));
        when(tenderCrmOpportunityCodeResolver.resolveForTender(t, OPERATOR_USERNAME)).thenReturn(CRM_OPPORTUNITY_CODE);
        when(projectDocumentRepository.findAllById(List.of(1032L))).thenReturn(List.of());

        listener(CRM_URL).onProjectResultConfirmed(event(BidResultType.LOST));

        WebhookDeliveryTask saved = captureSaved();
        JsonNode root = new ObjectMapper().readTree(saved.getPayload());
        JsonNode inner = root.path("bidInfoList").get(0);
        // payload code 应该是反查到的 CC 前缀格式，而非数字 id
        assertThat(inner.path("code").asText()).isEqualTo(CRM_OPPORTUNITY_CODE);
        assertThat(inner.path("status").asInt()).isEqualTo(3); // LOST → 3
    }

    @Test
    @DisplayName("crm_opportunity_id 是纯数字 id 但 CRM 反查失败 → code 为空字符串，仍入队（避免 CRM 伪成功）")
    void crmOpportunityIdIsNumeric_crmLookupFails_sendsEmptyCode() throws Exception {
        Tender t = tender();
        t.setCrmOpportunityId("20942");
        when(tenderRepository.findById(TENDER_ID)).thenReturn(Optional.of(t));
        when(tenderCrmOpportunityCodeResolver.resolveForTender(t, OPERATOR_USERNAME)).thenReturn("");
        when(projectDocumentRepository.findAllById(List.of(1032L))).thenReturn(List.of());

        listener(CRM_URL).onProjectResultConfirmed(event(BidResultType.LOST));

        WebhookDeliveryTask saved = captureSaved();
        JsonNode root = new ObjectMapper().readTree(saved.getPayload());
        JsonNode inner = root.path("bidInfoList").get(0);
        // CO-277: webhook 发送侧不降级为数字 id，避免 CRM 伪成功
        assertThat(inner.path("code").asText()).isEmpty();
        assertThat(inner.path("status").asInt()).isEqualTo(3); // LOST → 3
    }

    @Test
    @DisplayName("crm_opportunity_id 为空但 externalId 是 CRM:sourceId 时，使用 operatorUsername 兜底反查 code")
    void crmOpportunityIdEmpty_withCrmExternalId_resolvesFromExternalId() throws Exception {
        Tender t = tender();
        t.setCrmOpportunityId(null);
        t.setExternalId(ExternalSystemPrefix.CRM.formatExternalId("17"));
        when(tenderRepository.findById(TENDER_ID)).thenReturn(Optional.of(t));
        when(tenderCrmOpportunityCodeResolver.resolveForTender(t, OPERATOR_USERNAME)).thenReturn("CC2026070932");
        when(projectDocumentRepository.findAllById(List.of(1032L))).thenReturn(List.of());

        listener(CRM_URL).onProjectResultConfirmed(event(BidResultType.WON));

        WebhookDeliveryTask saved = captureSaved();
        JsonNode root = new ObjectMapper().readTree(saved.getPayload());
        JsonNode inner = root.path("bidInfoList").get(0);
        assertThat(inner.path("code").asText()).isEqualTo("CC2026070932");
    }


    @Test
    @DisplayName("CO-152: operatorUserId 可反查 → operator_username 写入任务")
    void operatorUserIdResolved_setsOperatorUsername() {
        when(tenderRepository.findById(TENDER_ID)).thenReturn(Optional.of(tender()));
        when(projectDocumentRepository.findAllById(List.of(1032L))).thenReturn(List.of());

        listener(CRM_URL).onProjectResultConfirmed(event(BidResultType.WON));

        WebhookDeliveryTask saved = captureSaved();
        assertThat(saved.getOperatorUsername()).isEqualTo(OPERATOR_USERNAME);
    }

    @Test
    @DisplayName("CO-571 Phase B: operatorUserId 查不到用户 → 无可用 username → 不入队")
    void operatorUserNotFound_noUsername_doesNotEnqueue() {
        when(tenderRepository.findById(TENDER_ID)).thenReturn(Optional.of(tender()));
        when(operatorUsernameResolver.resolveDeliveryUsername(any(Tender.class), any())).thenReturn(null);
        when(projectDocumentRepository.findAllById(List.of(1032L))).thenReturn(List.of());

        listener(CRM_URL).onProjectResultConfirmed(event(BidResultType.WON));

        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("CO-571 Phase B: operatorUserId 为 null → 无可用 username → 不入队")
    void operatorUserIdNull_noUsername_doesNotEnqueue() {
        when(tenderRepository.findById(TENDER_ID)).thenReturn(Optional.of(tender()));
        when(operatorUsernameResolver.resolveDeliveryUsername(any(Tender.class), any())).thenReturn(null);
        when(projectDocumentRepository.findAllById(List.of(1032L))).thenReturn(List.of());

        ProjectResultConfirmedEvent noOperator = ProjectResultConfirmedEvent.of(
                PROJECT_ID, TENDER_ID, BidResultType.WON, "", List.of(1032L),
                List.of(), null, null, RESULT_ID);

        listener(CRM_URL).onProjectResultConfirmed(noOperator);

        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("CO-571 Phase B: event operator 缺失时优先用 creator 的 username")
    void usesCreatorWhenEventOperatorMissing() {
        Tender t = tender();
        t.setCreatorId(100L);
        when(tenderRepository.findById(TENDER_ID)).thenReturn(Optional.of(t));
        when(operatorUsernameResolver.resolveDeliveryUsername(t, null)).thenReturn("creator-user");
        when(projectDocumentRepository.findAllById(List.of(1032L))).thenReturn(List.of());

        ProjectResultConfirmedEvent noOperator = ProjectResultConfirmedEvent.of(
                PROJECT_ID, TENDER_ID, BidResultType.WON, "", List.of(1032L),
                List.of(), null, null, RESULT_ID);

        listener(CRM_URL).onProjectResultConfirmed(noOperator);

        verify(operatorUsernameResolver).resolveDeliveryUsername(t, null);
        verify(tenderCrmOpportunityCodeResolver).resolveForTender(t, "creator-user");
    }

    private WebhookDeliveryTask captureSaved() {
        ArgumentCaptor<WebhookDeliveryTask> captor = ArgumentCaptor.forClass(WebhookDeliveryTask.class);
        verify(taskRepository).save(captor.capture());
        return captor.getValue();
    }
}