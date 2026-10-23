package com.xiyu.bid.webhook.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.project.domain.ProjectInitiationChangedEvent;
import com.xiyu.bid.project.entity.ProjectInitiationDetails;
import com.xiyu.bid.project.repository.ProjectInitiationDetailsRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.webhook.infrastructure.WebhookDeliveryTask;
import com.xiyu.bid.webhook.infrastructure.WebhookDeliveryTaskRepository;
import com.xiyu.bid.webhook.infrastructure.WebhookDeliveryTaskStatus;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProjectInitiationWebhookListener — 立项修改/审核通过实时同步 CRM")
class ProjectInitiationWebhookListenerTest {

    private static final String CRM_URL = "https://crm.example.com/customer-chance/bidInfoSync";
    private static final Long PROJECT_ID = 1001L;
    private static final Long TENDER_ID = 2001L;
    private static final Long OPERATOR_ID = 88L;
    private static final String OPERATOR_USERNAME = "08152";
    private static final String OPERATOR_NAME = "李四（08152）";
    private static final String CRM_OPPORTUNITY_CODE = "CC20260716001";
    private static final String CRM_OPPORTUNITY_NAME = "国家电网年度采购项目";

    @Mock private WebhookDeliveryTaskRepository taskRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private TenderRepository tenderRepository;
    @Mock private ProjectInitiationDetailsRepository initiationDetailsRepository;
    @Mock private TenderCrmOpportunityCodeResolver tenderCrmOpportunityCodeResolver;
    @Mock private OperatorUsernameResolver operatorUsernameResolver;
    @Mock private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ProjectInitiationWebhookListener listener(String url) {
        ProjectInitiationWebhookListener l = new ProjectInitiationWebhookListener(
                taskRepository,
                projectRepository,
                tenderRepository,
                initiationDetailsRepository,
                objectMapper,
                tenderCrmOpportunityCodeResolver,
                operatorUsernameResolver,
                userRepository
        );
        ReflectionTestUtils.setField(l, "crmWebhookUrl", url);
        return l;
    }

    @BeforeEach
    void setUp() {
        lenient().when(tenderCrmOpportunityCodeResolver.resolveForTender(any(Tender.class), any()))
                .thenAnswer(inv -> {
                    Tender t = inv.getArgument(0);
                    return t.getCrmOpportunityId() != null ? t.getCrmOpportunityId() : "";
                });
        lenient().when(operatorUsernameResolver.resolveForCrmLookup(any(Tender.class), any()))
                .thenReturn(OPERATOR_USERNAME);
        lenient().when(userRepository.findById(OPERATOR_ID))
                .thenReturn(Optional.of(User.builder().id(OPERATOR_ID).username(OPERATOR_USERNAME).fullName(OPERATOR_NAME).build()));
    }

    private Project mockProject() {
        return Project.builder().id(PROJECT_ID).tenderId(TENDER_ID).name("国网项目").build();
    }

    private Tender mockTender() {
        Tender t = new Tender();
        t.setId(TENDER_ID);
        t.setExternalId("CRM:2001");
        t.setCrmOpportunityId(CRM_OPPORTUNITY_CODE);
        t.setCrmOpportunityName(CRM_OPPORTUNITY_NAME);
        return t;
    }

    @Test
    @DisplayName("立项变更成功入队：status 为 null，feedback 包含 planSupplierCount 和 bidDocumentDisadvantage")
    void onInitiationChanged_enqueuesWebhookDeliveryTask() throws Exception {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(mockProject()));
        when(tenderRepository.findById(TENDER_ID)).thenReturn(Optional.of(mockTender()));

        ProjectInitiationDetails details = ProjectInitiationDetails.builder()
                .id(1L)
                .projectId(PROJECT_ID)
                .expectedBidders(4)
                .tenderAdverseItems("付款周期长达180天，且要求全额银行保函")
                .build();
        when(initiationDetailsRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.of(details));

        LocalDateTime now = LocalDateTime.of(2026, 10, 23, 11, 30, 0);
        ProjectInitiationChangedEvent event = new ProjectInitiationChangedEvent(PROJECT_ID, OPERATOR_ID, now);

        listener(CRM_URL).onInitiationChanged(event);

        ArgumentCaptor<WebhookDeliveryTask> captor = ArgumentCaptor.forClass(WebhookDeliveryTask.class);
        verify(taskRepository).save(captor.capture());
        WebhookDeliveryTask task = captor.getValue();

        assertThat(task.getTargetUrl()).isEqualTo(CRM_URL);
        assertThat(task.getTenderId()).isEqualTo(TENDER_ID);
        assertThat(task.getOperatorUsername()).isEqualTo(OPERATOR_USERNAME);
        assertThat(task.getEventType()).isEqualTo("project.initiation_updated");
        assertThat(task.getStatus()).isEqualTo(WebhookDeliveryTaskStatus.PENDING);

        // 解析并验证 payload JSON
        JsonNode payloadJson = objectMapper.readTree(task.getPayload());
        JsonNode bidInfoList = payloadJson.path("bidInfoList");
        assertThat(bidInfoList.isArray()).isTrue();
        assertThat(bidInfoList.size()).isEqualTo(1);

        JsonNode item = bidInfoList.get(0);
        assertThat(item.path("name").asText()).isEqualTo(CRM_OPPORTUNITY_NAME);
        assertThat(item.path("code").asText()).isEqualTo(CRM_OPPORTUNITY_CODE);
        assertThat(item.path("status").isNull()).isTrue();
        assertThat(item.path("statusEditor").asText()).isEqualTo(OPERATOR_NAME);
        assertThat(item.path("statusEditTime").asText()).isEqualTo("2026-10-23 11:30:00");
        assertThat(item.path("tenderId").asLong()).isEqualTo(TENDER_ID);

        // 解析并验证 feedback JSON
        JsonNode feedbackJson = objectMapper.readTree(item.path("feedback").asText());
        assertThat(feedbackJson.path("systemName").asText()).isEqualTo("西域数智化投标管理平台");
        assertThat(feedbackJson.path("operator").asText()).isEqualTo(OPERATOR_NAME);
        assertThat(feedbackJson.path("planSupplierCount").asInt()).isEqualTo(4);
        assertThat(feedbackJson.path("bidDocumentDisadvantage").asText()).isEqualTo("付款周期长达180天，且要求全额银行保函");
    }

    @Test
    @DisplayName("未配置 CRM webhook URL 时跳过入队")
    void onInitiationChanged_noUrl_skips() {
        ProjectInitiationChangedEvent event = new ProjectInitiationChangedEvent(PROJECT_ID, OPERATOR_ID, LocalDateTime.now());
        listener("").onInitiationChanged(event);
        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("关联标讯不存在时跳过入队")
    void onInitiationChanged_noTender_skips() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(mockProject()));
        when(tenderRepository.findById(TENDER_ID)).thenReturn(Optional.empty());

        ProjectInitiationChangedEvent event = new ProjectInitiationChangedEvent(PROJECT_ID, OPERATOR_ID, LocalDateTime.now());
        listener(CRM_URL).onInitiationChanged(event);

        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("标讯未关联 CRM 商机时跳过入队")
    void onInitiationChanged_noCrmOpportunity_skips() {
        Tender tenderWithoutCrm = new Tender();
        tenderWithoutCrm.setId(TENDER_ID);
        tenderWithoutCrm.setCrmOpportunityId(null);

        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(mockProject()));
        when(tenderRepository.findById(TENDER_ID)).thenReturn(Optional.of(tenderWithoutCrm));
        when(tenderCrmOpportunityCodeResolver.resolveForTender(any(), any())).thenReturn("");

        ProjectInitiationChangedEvent event = new ProjectInitiationChangedEvent(PROJECT_ID, OPERATOR_ID, LocalDateTime.now());
        listener(CRM_URL).onInitiationChanged(event);

        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("标讯有关联ID但反查 CRM 商机 code 失败时跳过入队")
    void onInitiationChanged_crmCodeResolutionFailed_skips() {
        Tender tenderWithId = new Tender();
        tenderWithId.setId(TENDER_ID);
        tenderWithId.setCrmOpportunityId("OPP-123");

        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(mockProject()));
        when(tenderRepository.findById(TENDER_ID)).thenReturn(Optional.of(tenderWithId));
        when(tenderCrmOpportunityCodeResolver.resolveForTender(any(), any())).thenReturn("");

        ProjectInitiationChangedEvent event = new ProjectInitiationChangedEvent(PROJECT_ID, OPERATOR_ID, LocalDateTime.now());
        listener(CRM_URL).onInitiationChanged(event);

        verify(taskRepository, never()).save(any());
    }
}
