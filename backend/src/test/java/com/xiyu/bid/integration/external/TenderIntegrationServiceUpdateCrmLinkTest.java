package com.xiyu.bid.integration.external;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.tender.dto.TenderDTO;
import com.xiyu.bid.tender.repository.TenderAttachmentRepository;
import com.xiyu.bid.tender.repository.TenderEvaluationRepository;
import com.xiyu.bid.tender.service.TenderAutoAssignmentService;
import com.xiyu.bid.tender.service.TenderAssignmentNotifier;
import com.xiyu.bid.tender.service.TenderEvaluationSubmissionMapper;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.tender.service.TenderMapper;
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
import static org.mockito.Mockito.when;

/**
 * CO-271: 验证 updateByExternalId 传入 crmId 时自动关联商机的兜底逻辑。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TenderIntegrationServiceUpdateCrmLinkTest {

    @Mock private TenderRepository tenderRepository;
    @Mock private TenderMapper tenderMapper;
    @Mock private TenderAttachmentRepository attachmentRepository;
    @Mock private TenderEvaluationRepository tenderEvaluationRepository;
    @Mock private TenderEvaluationSubmissionMapper submissionMapper;
    @Mock private CrmTenderLinkService crmTenderLinkService;
    @Mock private ProjectDocumentRepository projectDocumentRepository;
    @Mock private TenderAutoAssignmentService autoAssignmentService;
    @Mock private TenderAssignmentNotifier assignmentNotifier;
    @Mock private ApplicationEventPublisher eventPublisher;

    private TenderIntegrationCommandService commandService;

    @BeforeEach
    void setUp() {
        TenderEvaluationIntegrationMapper evaluationMapper = new TenderEvaluationIntegrationMapper(
                tenderEvaluationRepository, submissionMapper);
        TenderIntegrationMapper mapper = new TenderIntegrationMapper(
                tenderMapper, evaluationMapper);
        TenderEvaluationIntegrationService evaluationService = new TenderEvaluationIntegrationService(
                tenderEvaluationRepository, evaluationMapper, projectDocumentRepository);
        TenderIntegrationResolver helper = new TenderIntegrationResolver(tenderRepository);
        commandService = new TenderIntegrationCommandService(
                tenderRepository, attachmentRepository, crmTenderLinkService, mapper, evaluationService, helper,
                autoAssignmentService, assignmentNotifier, eventPublisher);
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
        }).when(crmTenderLinkService).linkIfPresent(any(Tender.class), org.mockito.ArgumentMatchers.eq("CHANCE_001"));

        TenderUpdateRequest request = TenderUpdateRequest.builder()
                .crmId("CHANCE_001")
                .build();

        commandService.updateByExternalId("crm", "test-001", request);

        assertThat(tender.getEvaluationSource()).isEqualTo(Tender.EvaluationSource.CRM_PUSH);
        assertThat(tender.getStatus()).isEqualTo(Tender.Status.EVALUATED);
        assertThat(tender.getCrmOpportunityId()).isEqualTo("CHANCE_001");
        assertThat(tender.getCrmOpportunityName()).isEqualTo("商机A");
    }

    @Test
    @DisplayName("CO-271: CRM 接口异常降级时 crmOpportunityId 用传入 crmId 兜底")
    void updateByExternalId_crmServiceThrows_fallbackSetsCrmOpportunityId() {
        Tender tender = createExistingTender();
        when(tenderRepository.findByExternalId("crm:test-001")).thenReturn(Optional.of(tender));

        org.mockito.Mockito.doNothing().when(crmTenderLinkService).linkIfPresent(any(Tender.class), any());

        TenderUpdateRequest request = TenderUpdateRequest.builder()
                .crmId("CHANCE_002")
                .build();

        commandService.updateByExternalId("crm", "test-001", request);

        assertThat(tender.getCrmOpportunityId()).isEqualTo("CHANCE_002");
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

        commandService.updateByExternalId("crm", "test-001", request);

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
        }).when(crmTenderLinkService).linkIfPresent(any(Tender.class), org.mockito.ArgumentMatchers.eq("CC20260619283"));

        TenderUpdateRequest request = TenderUpdateRequest.builder()
                .crmOpportunityId("CC20260619283")
                .crmOpportunityName("测试商机")
                .build();

        commandService.updateByExternalId("crm", "test-001", request);

        org.mockito.Mockito.verify(crmTenderLinkService)
                .linkIfPresent(any(Tender.class), org.mockito.ArgumentMatchers.eq("CC20260619283"));
        assertThat(tender.getCrmOpportunityId()).isEqualTo("CC20260619283");
        assertThat(tender.getEvaluationSource()).isEqualTo(Tender.EvaluationSource.CRM_PUSH);
        assertThat(tender.getStatus()).isEqualTo(Tender.Status.EVALUATED);
    }

    @Test
    @DisplayName("CO-276: crmOpportunityId 与 crmId 同时传时优先用 crmOpportunityId")
    void updateByExternalId_bothPresent_prefersCrmOpportunityId() {
        Tender tender = createExistingTender();
        when(tenderRepository.findByExternalId("crm:test-001")).thenReturn(Optional.of(tender));

        org.mockito.Mockito.doAnswer(inv -> {
            Tender t = inv.getArgument(0);
            t.setCrmOpportunityId("CC-PUBLIC");
            t.setStatus(Tender.Status.EVALUATED);
            return null;
        }).when(crmTenderLinkService).linkIfPresent(any(Tender.class), org.mockito.ArgumentMatchers.eq("CC-PUBLIC"));

        TenderUpdateRequest request = TenderUpdateRequest.builder()
                .crmId("CC-LEGACY")
                .crmOpportunityId("CC-PUBLIC")
                .build();

        commandService.updateByExternalId("crm", "test-001", request);

        org.mockito.Mockito.verify(crmTenderLinkService)
                .linkIfPresent(any(Tender.class), org.mockito.ArgumentMatchers.eq("CC-PUBLIC"));
        org.mockito.Mockito.verify(crmTenderLinkService,
                org.mockito.Mockito.never())
                .linkIfPresent(any(Tender.class), org.mockito.ArgumentMatchers.eq("CC-LEGACY"));
    }

    @Test
    @DisplayName("CO-276: 仅传 crmOpportunityId + CRM 异常降级时用 crmOpportunityId 兜底落库")
    void updateByExternalId_onlyCrmOpportunityId_fallbackSetsOpportunityId() {
        Tender tender = createExistingTender();
        when(tenderRepository.findByExternalId("crm:test-001")).thenReturn(Optional.of(tender));

        org.mockito.Mockito.doNothing().when(crmTenderLinkService).linkIfPresent(any(Tender.class), any());

        TenderUpdateRequest request = TenderUpdateRequest.builder()
                .crmOpportunityId("CC20260619283")
                .crmOpportunityName("测试商机")
                .build();

        commandService.updateByExternalId("crm", "test-001", request);

        assertThat(tender.getCrmOpportunityId()).isEqualTo("CC20260619283");
        assertThat(tender.getCrmOpportunityName()).isEqualTo("测试商机");
        assertThat(tender.getEvaluationSource()).isEqualTo(Tender.EvaluationSource.CRM_PUSH);
        assertThat(tender.getStatus()).isEqualTo(Tender.Status.EVALUATED);
    }

    @Test
    @DisplayName("CO-277 接收侧根因修复: crmId 是纯数字 id 且反查失败时, applyCrmFallback 不应把 id 存入 crm_opportunity_id")
    void updateByExternalId_numericIdCrmId_crmLookupFails_fallbackDoesNotSetId() {
        // 场景：CRM 推送 crmOpportunityId=20942（纯数字 id），CrmTenderLinkService 反查失败（token 异常等）
        // CO-277 修复：applyCrmLinkAndAssignment 异常 catch 分支保持 null
        // 本修复：applyCrmFallback 不应把纯数字 id 存入，保持 null 让 linkByChanceIdIfPresent 兜底
        Tender tender = createExistingTender();
        when(tenderRepository.findByExternalId("crm:test-001")).thenReturn(Optional.of(tender));

        // 模拟 CrmTenderLinkService.linkIfPresent 反查失败（什么都不做，保持 crmOpportunityId=null）
        org.mockito.Mockito.doNothing().when(crmTenderLinkService).linkIfPresent(any(Tender.class), any());

        TenderUpdateRequest request = TenderUpdateRequest.builder()
                .crmOpportunityId("20942")
                .crmOpportunityName("cye测试21对接人")
                .build();

        commandService.updateByExternalId("crm", "test-001", request);

        // 关键断言：纯数字 id 不应被存入 crm_opportunity_id
        assertThat(tender.getCrmOpportunityId())
                .as("纯数字 id 不应被存入 crm_opportunity_id（CO-277 接收侧根因修复）")
                .isNull();
        // 但 crmOpportunityName 仍应被设置（name 不影响 CRM 匹配）
        assertThat(tender.getCrmOpportunityName()).isEqualTo("cye测试21对接人");
        assertThat(tender.getEvaluationSource()).isEqualTo(Tender.EvaluationSource.CRM_PUSH);
        assertThat(tender.getStatus()).isEqualTo(Tender.Status.EVALUATED);
    }

    @Test
    @DisplayName("CO-277 接收侧根因修复: crmId 是 code 格式（CC...）时, applyCrmFallback 保持原逻辑直接存入")
    void updateByExternalId_codeFormatCrmId_crmLookupFails_fallbackSetsCode() {
        // 场景：CRM 推送 crmOpportunityId=CC20260621323（code 格式），CrmTenderLinkService 反查失败
        // 期望：code 格式仍走原逻辑直接存入（code 是 CRM 期望的格式，不会导致匹配失败）
        Tender tender = createExistingTender();
        when(tenderRepository.findByExternalId("crm:test-001")).thenReturn(Optional.of(tender));

        org.mockito.Mockito.doNothing().when(crmTenderLinkService).linkIfPresent(any(Tender.class), any());

        TenderUpdateRequest request = TenderUpdateRequest.builder()
                .crmOpportunityId("CC20260621323")
                .crmOpportunityName("cye弃标111")
                .build();

        commandService.updateByExternalId("crm", "test-001", request);

        assertThat(tender.getCrmOpportunityId()).isEqualTo("CC20260621323");
        assertThat(tender.getCrmOpportunityName()).isEqualTo("cye弃标111");
        assertThat(tender.getEvaluationSource()).isEqualTo(Tender.EvaluationSource.CRM_PUSH);
        assertThat(tender.getStatus()).isEqualTo(Tender.Status.EVALUATED);
    }
}
