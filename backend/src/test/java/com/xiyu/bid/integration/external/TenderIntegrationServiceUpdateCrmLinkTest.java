package com.xiyu.bid.integration.external;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.tender.dto.TenderDTO;
import com.xiyu.bid.tender.repository.TenderAttachmentRepository;
import com.xiyu.bid.tender.repository.TenderEvaluationRepository;
import com.xiyu.bid.tender.service.TenderAutoAssignmentService;
import com.xiyu.bid.tender.service.TenderAssignmentNotifier;
import com.xiyu.bid.tender.service.TenderEvaluationSubmissionMapper;
import com.xiyu.bid.project.service.ProjectManagerDepartmentEnricher;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.tender.service.TenderMapper;
import com.xiyu.bid.tender.service.TenderAuditService;
import com.xiyu.bid.tender.service.TenderCrmOccupancyChecker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * CO-271: 验证 updateByExternalId 传入 crmId 时自动关联商机的兜底逻辑。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TenderIntegrationServiceUpdateCrmLinkTest {

    @Mock private TenderRepository tenderRepository;
    @Mock private UserRepository userRepository;
    @Mock private TenderMapper tenderMapper;
    @Mock private TenderAttachmentRepository attachmentRepository;
    @Mock private TenderEvaluationRepository tenderEvaluationRepository;
    @Mock private TenderEvaluationSubmissionMapper submissionMapper;
    @Mock private CrmTenderLinkService crmTenderLinkService;
    @Mock private ProjectDocumentRepository projectDocumentRepository;
    @Mock private TenderAutoAssignmentService autoAssignmentService;
    @Mock private TenderAssignmentNotifier assignmentNotifier;
    @Mock private TenderAuditService tenderAuditService;
    @Mock private TenderCrmOccupancyChecker crmOccupancyChecker;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private ProjectManagerIdResolver projectManagerIdResolver;
    @Mock private ProjectManagerDepartmentEnricher departmentEnricher;

    private TenderIntegrationCommandService commandService;

    @BeforeEach
    void setUp() {
        TenderEvaluationIntegrationMapper evaluationMapper = new TenderEvaluationIntegrationMapper(
                tenderEvaluationRepository, submissionMapper);
        TenderIntegrationMapper mapper = new TenderIntegrationMapper(
                tenderMapper, evaluationMapper, projectManagerIdResolver);
        TenderEvaluationIntegrationService evaluationService = new TenderEvaluationIntegrationService(
                tenderEvaluationRepository, evaluationMapper, projectDocumentRepository);
        TenderIntegrationResolver helper = new TenderIntegrationResolver(tenderRepository);
        TenderIntegrationCommandSupport support = new TenderIntegrationCommandSupport(
                crmTenderLinkService,
                autoAssignmentService,
                assignmentNotifier,
                eventPublisher,
                tenderRepository,
                projectManagerIdResolver,
                departmentEnricher,
                userRepository);
        commandService = new TenderIntegrationCommandService(
                tenderRepository, attachmentRepository, crmTenderLinkService, mapper, evaluationService, helper, support, eventPublisher,
                tenderAuditService, userRepository, crmOccupancyChecker,
                new com.xiyu.bid.webhook.application.OperatorUsernameResolver(userRepository));
        when(tenderRepository.save(any(Tender.class))).thenAnswer(inv -> inv.getArgument(0));
        TenderDTO stubDto = TenderDTO.builder().build();
        when(tenderMapper.toDTO(any(Tender.class))).thenReturn(stubDto);
        when(tenderMapper.buildContacts(any(Tender.class))).thenReturn(Collections.emptyList());
    }

    private Tender createExistingTender() {
        Tender t = new Tender();
        t.setId(1L);
        t.setExternalId("crm:test-001");
        t.setTitle("测试标讯");
        t.setStatus(Tender.Status.PENDING_ASSIGNMENT);
        return t;
    }

    @Test
    @DisplayName("CO-271: crmId 非空时 evaluationSource 和 status 应被正确设置")
    void updateByExternalId_withCrmId_setsEvaluationSourceAndStatus() {
        Tender tender = createExistingTender();
        when(tenderRepository.findByExternalId("crm:test-001")).thenReturn(Optional.of(tender));

        org.mockito.Mockito.doAnswer(inv -> {
            Tender t = inv.getArgument(0);
            t.setCrmOpportunityId("CHANCE_001");
            t.setCrmOpportunityName("商机A");
            t.setStatus(Tender.Status.EVALUATED);
            return null;
        }).when(crmTenderLinkService).linkIfPresent(any(Tender.class),
                org.mockito.ArgumentMatchers.eq("20916"), org.mockito.ArgumentMatchers.eq("CHANCE_001"), any());

        TenderUpdateRequest request = TenderUpdateRequest.builder()
                .crmId("20916")
                .crmOpportunityId("CHANCE_001")
                .build();

        commandService.updateByExternalId("crm", "test-001", request, null);

        assertThat(tender.getEvaluationSource()).isEqualTo(Tender.EvaluationSource.CRM_PUSH);
        assertThat(tender.getStatus()).isEqualTo(Tender.Status.EVALUATED);
        assertThat(tender.getCrmOpportunityId()).isEqualTo("CHANCE_001");
        assertThat(tender.getCrmOpportunityName()).isEqualTo("商机A");
    }

    @Test
    @DisplayName("CO-271: CRM 接口异常降级时 crmOpportunityId 用传入 code 兜底")
    void updateByExternalId_crmServiceThrows_fallbackSetsCrmOpportunityId() {
        Tender tender = createExistingTender();
        when(tenderRepository.findByExternalId("crm:test-001")).thenReturn(Optional.of(tender));

        org.mockito.Mockito.doNothing().when(crmTenderLinkService)
                .linkIfPresent(any(Tender.class), any(), any(), any());

        TenderUpdateRequest request = TenderUpdateRequest.builder()
                .crmOpportunityId("CC20260619285")
                .build();

        commandService.updateByExternalId("crm", "test-001", request, null);

        assertThat(tender.getCrmOpportunityId()).isEqualTo("CC20260619285");
        assertThat(tender.getEvaluationSource()).isEqualTo(Tender.EvaluationSource.CRM_PUSH);
        assertThat(tender.getStatus()).isEqualTo(Tender.Status.EVALUATED);
    }

    @Test
    @DisplayName("CO-271: crmId 为空时不触发兜底逻辑")
    void updateByExternalId_nullCrmId_noFallback() {
        Tender tender = createExistingTender();
        when(tenderRepository.findByExternalId("crm:test-001")).thenReturn(Optional.of(tender));

        TenderUpdateRequest request = TenderUpdateRequest.builder()
                .title("更新标题")
                .build();

        commandService.updateByExternalId("crm", "test-001", request, null);

        assertThat(tender.getEvaluationSource()).isNull();
        assertThat(tender.getStatus()).isEqualTo(Tender.Status.PENDING_ASSIGNMENT);
        assertThat(tender.getCrmOpportunityId()).isNull();
    }

    @Test
    @DisplayName("CO-276: 仅传 crmOpportunityId（不传 crmId）应触发关联")
    void updateByExternalId_onlyCrmOpportunityId_triggersLink() {
        Tender tender = createExistingTender();
        when(tenderRepository.findByExternalId("crm:test-001")).thenReturn(Optional.of(tender));

        org.mockito.Mockito.doAnswer(inv -> {
            Tender t = inv.getArgument(0);
            t.setCrmOpportunityId("CC20260619283");
            t.setCrmOpportunityName("测试商机");
            t.setStatus(Tender.Status.EVALUATED);
            return null;
        }).when(crmTenderLinkService).linkIfPresent(any(Tender.class),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq("CC20260619283"), any());

        TenderUpdateRequest request = TenderUpdateRequest.builder()
                .crmOpportunityId("CC20260619283")
                .crmOpportunityName("测试商机")
                .build();

        commandService.updateByExternalId("crm", "test-001", request, null);

        org.mockito.Mockito.verify(crmTenderLinkService)
                .linkIfPresent(any(Tender.class),
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers.eq("CC20260619283"), any());
        assertThat(tender.getCrmOpportunityId()).isEqualTo("CC20260619283");
        assertThat(tender.getEvaluationSource()).isEqualTo(Tender.EvaluationSource.CRM_PUSH);
        assertThat(tender.getStatus()).isEqualTo(Tender.Status.EVALUATED);
    }

    @Test
    @DisplayName("CO-276: crmId 和 crmOpportunityId 同时传时分别传递")
    void updateByExternalId_bothPresent_passesSeparately() {
        Tender tender = createExistingTender();
        when(tenderRepository.findByExternalId("crm:test-001")).thenReturn(Optional.of(tender));

        org.mockito.Mockito.doAnswer(inv -> {
            Tender t = inv.getArgument(0);
            t.setCrmOpportunityId("CC-PUBLIC");
            t.setStatus(Tender.Status.EVALUATED);
            return null;
        }).when(crmTenderLinkService).linkIfPresent(any(Tender.class),
                org.mockito.ArgumentMatchers.eq("20916"),
                org.mockito.ArgumentMatchers.eq("CC-PUBLIC"), any());

        TenderUpdateRequest request = TenderUpdateRequest.builder()
                .crmId("20916")
                .crmOpportunityId("CC-PUBLIC")
                .build();

        commandService.updateByExternalId("crm", "test-001", request, null);

        org.mockito.Mockito.verify(crmTenderLinkService)
                .linkIfPresent(any(Tender.class),
                        org.mockito.ArgumentMatchers.eq("20916"),
                        org.mockito.ArgumentMatchers.eq("CC-PUBLIC"), any());
    }

    @Test
    @DisplayName("CO-276: 仅传 crmOpportunityId + CRM 异常降级时用 crmOpportunityId 兜底落库")
    void updateByExternalId_onlyCrmOpportunityId_fallbackSetsOpportunityId() {
        Tender tender = createExistingTender();
        when(tenderRepository.findByExternalId("crm:test-001")).thenReturn(Optional.of(tender));

        org.mockito.Mockito.doNothing().when(crmTenderLinkService)
                .linkIfPresent(any(Tender.class), any(), any(), any());

        TenderUpdateRequest request = TenderUpdateRequest.builder()
                .crmOpportunityId("CC20260619283")
                .crmOpportunityName("测试商机")
                .build();

        commandService.updateByExternalId("crm", "test-001", request, null);

        assertThat(tender.getCrmOpportunityId()).isEqualTo("CC20260619283");
        assertThat(tender.getCrmOpportunityName()).isEqualTo("测试商机");
        assertThat(tender.getEvaluationSource()).isEqualTo(Tender.EvaluationSource.CRM_PUSH);
        assertThat(tender.getStatus()).isEqualTo(Tender.Status.EVALUATED);
    }

    @Test
    @DisplayName("字段分离: 仅传 crmId（数字主键）时, crm_opportunity_id 保持 null")
    void updateByExternalId_numericCrmId_only_doesNotSetOpportunityId() {
        // 场景：CRM 推送 crmId=20942（纯数字主键 id），不传 crmOpportunityId（code）
        // 新逻辑：crmId 只用于 findProjectLeaderByChanceId 查项目负责人，不会存入 crm_opportunity_id
        // 同时无 code 时不存入 crmOpportunityName，避免"半关联"状态导致去重校验失效
        Tender tender = createExistingTender();
        when(tenderRepository.findByExternalId("crm:test-001")).thenReturn(Optional.of(tender));

        // 模拟 CrmTenderLinkService.linkIfPresent 反查失败（什么都不做）
        org.mockito.Mockito.doNothing().when(crmTenderLinkService)
                .linkIfPresent(any(Tender.class),
                        org.mockito.ArgumentMatchers.eq("20942"),
                        org.mockito.ArgumentMatchers.isNull(), any());

        TenderUpdateRequest request = TenderUpdateRequest.builder()
                .crmId("20942")
                .crmOpportunityName("cye测试21对接人")
                .build();

        commandService.updateByExternalId("crm", "test-001", request, null);

        // 关键断言：crmId 是数字主键，不应被存入 crm_opportunity_id（字段分离设计）
        assertThat(tender.getCrmOpportunityId())
                .as("crmId（数字主键）不应被存入 crm_opportunity_id（字段分离设计）")
                .isNull();
        // 无 code 时不应写入 name，避免半关联
        assertThat(tender.getCrmOpportunityName()).isNull();
        assertThat(tender.getEvaluationSource()).isEqualTo(Tender.EvaluationSource.CRM_PUSH);
        assertThat(tender.getStatus()).isEqualTo(Tender.Status.EVALUATED);
    }

    @Test
    @DisplayName("字段分离: 仅传 crmOpportunityId（CC 格式）时, applyCrmFallback 直接存入 code")
    void updateByExternalId_codeFormatCrmId_crmLookupFails_fallbackSetsCode() {
        // 场景：CRM 推送 crmOpportunityId=CC20260621323（code 格式），CrmTenderLinkService 反查失败
        // 期望：code 格式仍走原逻辑直接存入（code 是 CRM 期望的格式，不会导致匹配失败）
        Tender tender = createExistingTender();
        when(tenderRepository.findByExternalId("crm:test-001")).thenReturn(Optional.of(tender));

        org.mockito.Mockito.doNothing().when(crmTenderLinkService)
                .linkIfPresent(any(Tender.class), any(), any(), any());

        TenderUpdateRequest request = TenderUpdateRequest.builder()
                .crmOpportunityId("CC20260621323")
                .crmOpportunityName("cye弃标111")
                .build();

        commandService.updateByExternalId("crm", "test-001", request, null);

        assertThat(tender.getCrmOpportunityId()).isEqualTo("CC20260621323");
        assertThat(tender.getCrmOpportunityName()).isEqualTo("cye弃标111");
        assertThat(tender.getEvaluationSource()).isEqualTo(Tender.EvaluationSource.CRM_PUSH);
        assertThat(tender.getStatus()).isEqualTo(Tender.Status.EVALUATED);
    }

    @Test
    @DisplayName("修改接口传入 projectManagerName 时同步更新负责人姓名与 user_id")
    void updateByExternalId_withProjectManagerName_setsManagerNameAndId() {
        Tender tender = createExistingTender();
        when(tenderRepository.findByExternalId("crm:test-001")).thenReturn(Optional.of(tender));
        when(projectManagerIdResolver.resolveByFullName("王五")).thenReturn(42L);

        TenderUpdateRequest request = TenderUpdateRequest.builder()
                .projectManagerName("王五")
                .build();

        commandService.updateByExternalId("crm", "test-001", request, null);

        assertThat(tender.getProjectManagerName()).isEqualTo("王五");
        assertThat(tender.getProjectManagerId()).isEqualTo(42L);
    }

    // ===== 根因行为集成测试：API Key 路径下 userId → resolveUsername → linkIfPresent(username) =====

    @Test
    @DisplayName("根因修复: userId 非 null 时 resolveUsername 返回 username 并透传到 linkIfPresent")
    void updateByExternalId_withUserId_passesResolvedUsernameToLinkIfPresent() {
        // 生产 bug 场景：CRM 推送走 API Key 认证，userId 是 API Key 创建者（如 admin）
        // 修复前：linkIfPresent 收到 username=null，CRM 反查失败，商机未关联
        // 修复后：resolveUsername(userId) 返回 "admin"，透传到 linkIfPresent
        Tender tender = createExistingTender();
        when(tenderRepository.findByExternalId("crm:test-001")).thenReturn(Optional.of(tender));

        // mock userRepository.findById(userId) 返回带 username 的 User
        User user = new User();
        user.setId(1L);
        user.setUsername("admin");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        org.mockito.Mockito.doNothing().when(crmTenderLinkService)
                .linkIfPresent(any(Tender.class), any(), any(), any());

        TenderUpdateRequest request = TenderUpdateRequest.builder()
                .crmOpportunityId("CC2026071244")
                .build();

        commandService.updateByExternalId("crm", "test-001", request, 1L);

        // 验证 linkIfPresent 收到的第 4 参数是 "admin"（而非 null）
        org.mockito.ArgumentCaptor<String> usernameCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(crmTenderLinkService)
                .linkIfPresent(any(Tender.class), any(), any(), usernameCaptor.capture());
        assertThat(usernameCaptor.getValue()).isEqualTo("admin");
    }
}
