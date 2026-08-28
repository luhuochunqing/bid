// Input: ProjectIntegrationCommandService（构造注入 6 依赖）
// Output: 单测 — tenderId 定位链/一致性断言/applied=false 跳过/部分更新/客户信息/GAP 附件/锁与错误码
// Pos: Test/合约验证 — 接口六 §3.6 编排层（tenderId 定位修正版）
package com.xiyu.bid.integration.external;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.project.core.InitiationFieldPolicy;
import com.xiyu.bid.project.core.ProjectStage;
import com.xiyu.bid.project.dto.CustomerInfoRow;
import com.xiyu.bid.project.dto.InitiationDto;
import com.xiyu.bid.project.entity.ProjectInitiationDetails;
import com.xiyu.bid.project.repository.ProjectInitiationDetailsRepository;
import com.xiyu.bid.project.service.ProjectInitiationMapper;
import com.xiyu.bid.project.service.ProjectStageService;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.tender.dto.EvaluationBasicDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link ProjectIntegrationCommandService} 单测（tenderId 定位修正）。
 *
 * <p>覆盖计划要求的每个变更点：
 * <ul>
 *   <li>定位链：tenderId → 标讯 → 项目；projectId 优先 + 一致性断言；标讯不存在 404</li>
 *   <li>来源系统轻量校验：externalId 前缀不匹配 400、手工标讯（null）放行</li>
 *   <li>applied=false 跳过语义：标讯存在但未立项返回跳过响应而非 404</li>
 *   <li>部分更新：非 null 覆盖、null/未传保留；仅写项目数据，标讯实体不被修改</li>
 *   <li>客户信息传入即全量替换（含 TEL 映射）；projectPlanGapFiles 三态</li>
 *   <li>阶段锁 423、立项字段锁 423、立项缺失 404；半成品立项部分回写 200（不校验请求域外字段，2026-08-28 CRM 首联 422 根因回归）</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProjectIntegrationCommandServiceTest {

    private static final Long PROJECT_ID = 1L;
    private static final Long TENDER_ID = 9L;
    private static final Long API_USER_ID = 77L;

    @Mock private TenderRepository tenderRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectInitiationDetailsRepository initiationRepository;
    @Mock private ProjectInitiationMapper initiationMapper;
    @Mock private ProjectStageService projectStageService;
    @Mock private ProjectDocumentRepository projectDocumentRepository;

    private ProjectIntegrationCommandService service;

    private Tender tender;
    private Project project;
    private ProjectInitiationDetails details;

    @BeforeEach
    void setUp() {
        service = new ProjectIntegrationCommandService(
                tenderRepository, projectRepository, initiationRepository,
                initiationMapper, projectStageService, projectDocumentRepository);

        tender = Tender.builder().id(TENDER_ID).title("某单位采购项目")
                .externalId("CRM:" + TENDER_ID).build();
        project = Project.builder().id(PROJECT_ID).name("旧项目名").build();
        details = ProjectInitiationDetails.builder()
                .id(10L).projectId(PROJECT_ID).locked(Boolean.FALSE).build();

        when(tenderRepository.findById(TENDER_ID)).thenReturn(Optional.of(tender));
        when(projectRepository.findByTenderId(TENDER_ID)).thenReturn(List.of(project));
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(projectStageService.currentStage(PROJECT_ID)).thenReturn(ProjectStage.INITIATED);
        when(initiationRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.of(details));
        when(initiationRepository.save(any(ProjectInitiationDetails.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        // 合并链路默认走通：现值输入 → 合并输入（含必填项，通过 validate）→ DTO
        InitiationFieldPolicy.InitiationInput validInput = validInitiationInput();
        when(initiationMapper.toInput(details)).thenReturn(validInput);
        when(initiationMapper.mergeForUpdate(any(), any())).thenReturn(validInput);
        when(initiationMapper.toDto(any())).thenReturn(InitiationDto.builder().build());
    }

    /** 通过 InitiationFieldPolicy.validate 的完整输入（必填项齐全）。 */
    private InitiationFieldPolicy.InitiationInput validInitiationInput() {
        return new InitiationFieldPolicy.InitiationInput(
                "测试采购单位", 3, 12, null, null,
                null, null,
                LocalDateTime.of(2026, 9, 1, 10, 0),
                5L, "电商事业部",
                null, null, null, null,
                null, null, "风险预判旧值", "兜底旧值", "流程旧值", "支持旧值",
                null, null, null, null, null, null, null, null, null, null);
    }

    private ProjectUpdateRequest.ProjectUpdateRequestBuilder baseRequest() {
        return ProjectUpdateRequest.builder();
    }

    // ========== tenderId 定位链 ==========

    @Test
    @DisplayName("按 (sourceSystem, tenderId) 定位标讯再定位项目，applied=true 返回更新响应")
    void resolveByTenderId_happyPath() {
        ProjectUpdateResponse resp = service.updateByTenderId(
                "CRM", TENDER_ID, baseRequest().projectName("新项目名").build(), API_USER_ID);

        assertThat(resp.getProjectId()).isEqualTo(PROJECT_ID);
        assertThat(resp.getProjectName()).isEqualTo("新项目名");
        assertThat(resp.getUpdatedAt()).isNotBlank();
        assertThat(resp.getApplied()).isTrue();
        assertThat(project.getName()).isEqualTo("新项目名");
    }

    @Test
    @DisplayName("手工建标讯（externalId=null，无来源键）不被来源校验误伤，正常更新")
    void manualTenderWithoutExternalId_proceeds() {
        when(tenderRepository.findById(TENDER_ID))
                .thenReturn(Optional.of(Tender.builder().id(TENDER_ID).title("手工标讯").build()));

        ProjectUpdateResponse resp = service.updateByTenderId(
                "CRM", TENDER_ID, baseRequest().projectName("新项目名").build(), API_USER_ID);

        assertThat(resp.getApplied()).isTrue();
        assertThat(resp.getProjectId()).isEqualTo(PROJECT_ID);
    }

    @Test
    @DisplayName("定位过程不修改标讯任何字段（只写项目数据）")
    void tenderEntityNotModified() {
        String titleBefore = tender.getTitle();
        String externalIdBefore = tender.getExternalId();

        service.updateByTenderId("CRM", TENDER_ID,
                baseRequest().projectName("新项目名").build(), API_USER_ID);

        assertThat(tender.getTitle()).isEqualTo(titleBefore);
        assertThat(tender.getExternalId()).isEqualTo(externalIdBefore);
    }

    @Test
    @DisplayName("projectId 传入时优先按 ID 定位，不走标讯解析")
    void projectIdPresent_skipsTenderLookup() {
        service.updateByTenderId("CRM", TENDER_ID,
                baseRequest().projectId(PROJECT_ID).build(), API_USER_ID);

        verify(tenderRepository, never()).findById(any());
        verify(projectRepository).findById(PROJECT_ID);
    }

    @Test
    @DisplayName("projectId 不存在 → 404")
    void projectIdNotFound_throws404() {
        when(projectRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateByTenderId("CRM", TENDER_ID,
                baseRequest().projectId(999L).build(), API_USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("标讯不存在 → 404，消息含 tenderId 定位细节")
    void tenderNotFound_throws404() {
        when(tenderRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateByTenderId("CRM", 999L,
                baseRequest().build(), API_USER_ID))
                .isInstanceOfSatisfying(ResourceNotFoundException.class, ex ->
                        assertThat(ex.getMessage()).contains("id=999"));
    }

    // ========== projectId 与路径 tenderId 一致性断言（防越权写） ==========

    @Test
    @DisplayName("projectId 关联标讯与路径 tenderId 一致 → 放行更新")
    void projectIdMatchesTender_proceeds() {
        project.setTenderId(TENDER_ID);

        ProjectUpdateResponse resp = service.updateByTenderId("CRM", TENDER_ID,
                baseRequest().projectId(PROJECT_ID).projectName("新项目名").build(), API_USER_ID);

        assertThat(resp.getProjectId()).isEqualTo(PROJECT_ID);
    }

    @Test
    @DisplayName("projectId 关联标讯与路径 tenderId 不一致 → 400（IllegalArgumentException），不写任何数据")
    void projectIdTenderMismatch_throws400() {
        project.setTenderId(999L);

        assertThatThrownBy(() -> service.updateByTenderId("CRM", TENDER_ID,
                baseRequest().projectId(PROJECT_ID).build(), API_USER_ID))
                .isInstanceOfSatisfying(IllegalArgumentException.class, ex ->
                        assertThat(ex.getMessage()).contains("不一致"));
        verify(initiationRepository, never()).save(any());
    }

    @Test
    @DisplayName("projectId 未关联标讯（tenderId 为 null，手工立项早期数据）→ 跳过一致性断言放行")
    void projectIdWithoutTender_skipsAssertion() {
        project.setTenderId(null);

        ProjectUpdateResponse resp = service.updateByTenderId("CRM", TENDER_ID,
                baseRequest().projectId(PROJECT_ID).build(), API_USER_ID);

        assertThat(resp.getProjectId()).isEqualTo(PROJECT_ID);
        verifyNoInteractions(tenderRepository);
    }

    // ========== 来源系统轻量校验 ==========

    @Test
    @DisplayName("标讯来源键前缀与 sourceSystem 不一致（BID:88 ≠ CRM）→ 400")
    void sourceSystemMismatch_throws400() {
        Tender otherSystem = Tender.builder().id(TENDER_ID).externalId("BID:88").build();
        when(tenderRepository.findById(TENDER_ID)).thenReturn(Optional.of(otherSystem));

        assertThatThrownBy(() -> service.updateByTenderId("CRM", TENDER_ID,
                baseRequest().projectName("不该生效").build(), API_USER_ID))
                .isInstanceOfSatisfying(IllegalArgumentException.class, ex ->
                        assertThat(ex.getMessage()).contains("不一致"));
        verify(projectRepository, never()).save(any(Project.class));
        verify(initiationRepository, never()).save(any());
    }

    // ========== applied=false 跳过语义（立项时序差不再 404） ==========

    @Test
    @DisplayName("标讯存在但无关联项目 → 200 applied=false 跳过响应，不触碰阶段锁与立项")
    void noProjectLinked_returnsSkippedResponse() {
        when(projectRepository.findByTenderId(TENDER_ID)).thenReturn(List.of());

        ProjectUpdateResponse resp = service.updateByTenderId("CRM", TENDER_ID,
                baseRequest().projectName("投标中编辑").build(), API_USER_ID);

        assertThat(resp.getApplied()).isFalse();
        assertThat(resp.getProjectId()).isNull();
        assertThat(resp.getProjectName()).isNull();
        verify(projectRepository, never()).save(any(Project.class));
        verifyNoInteractions(projectStageService);
        verify(initiationRepository, never()).save(any());
    }

    @Test
    @DisplayName("多个项目关联同一标讯时取首个")
    void multipleProjectsLinked_usesFirst() {
        Project second = Project.builder().id(2L).name("第二项目").build();
        when(projectRepository.findByTenderId(TENDER_ID)).thenReturn(List.of(project, second));

        ProjectUpdateResponse resp = service.updateByTenderId("CRM", TENDER_ID,
                baseRequest().build(), API_USER_ID);

        assertThat(resp.getProjectId()).isEqualTo(PROJECT_ID);
    }

    // ========== 部分更新语义 ==========

    @Test
    @DisplayName("projectName 为 null 时不触碰项目实体（保留原值）")
    void nullProjectName_preserved() {
        service.updateByTenderId("CRM", TENDER_ID, baseRequest().build(), API_USER_ID);

        assertThat(project.getName()).isEqualTo("旧项目名");
        verify(projectRepository, never()).save(any(Project.class));
    }

    @Test
    @DisplayName("evaluationBasic 6 标量字段作为 patch 传入 mergeForUpdate")
    void evaluationBasicPatch_forwardedToMerge() {
        EvaluationBasicDTO basic = new EvaluationBasicDTO(
                null, new BigDecimal("100"), null, "风险", "兜底", "流程", "支持",
                new BigDecimal("50"), null);

        service.updateByTenderId("CRM", TENDER_ID,
                baseRequest().evaluationBasic(basic).build(), API_USER_ID);

        ArgumentCaptor<InitiationDto> patchCaptor = ArgumentCaptor.forClass(InitiationDto.class);
        verify(initiationMapper).mergeForUpdate(any(), patchCaptor.capture());
        InitiationDto patch = patchCaptor.getValue();
        assertThat(patch.getAnnualEcommerceAmount()).isEqualByComparingTo("100");
        assertThat(patch.getAnnualRevenue()).isEqualByComparingTo("50");
        assertThat(patch.getRiskAssessment()).isEqualTo("风险");
        assertThat(patch.getRiskMitigationPlan()).isEqualTo("兜底");
        assertThat(patch.getPmUnderstandsProcess()).isEqualTo("流程");
        assertThat(patch.getSupportNeeded()).isEqualTo("支持");
    }

    @Test
    @DisplayName("updatedBy 记录 API Key 对应用户")
    void updatedByRecorded() {
        service.updateByTenderId("CRM", TENDER_ID, baseRequest().build(), API_USER_ID);

        assertThat(details.getUpdatedBy()).isEqualTo(API_USER_ID);
    }

    // ========== 客户信息全量替换（含 TEL） ==========

    @Test
    @DisplayName("evaluationCustomerInfos 传入即全量替换（含 TEL 映射与键归一）")
    void customerInfosReplacedWithTelMapping() {
        Map<String, Object> row = Map.of(
                "NAME", "张三",
                "CONTACT", "13800138000",
                "TEL", "021-12345678",
                "CONTACTED", "是",
                "TENDENCY", "支持");

        service.updateByTenderId("CRM", TENDER_ID,
                baseRequest().evaluationCustomerInfos(List.of(row)).build(), API_USER_ID);

        ArgumentCaptor<InitiationDto> dtoCaptor = ArgumentCaptor.forClass(InitiationDto.class);
        verify(initiationMapper).applyInput(eq(details), dtoCaptor.capture());
        List<CustomerInfoRow> rows = dtoCaptor.getValue().getCustomerInfoRows();
        assertThat(rows).hasSize(1);
        CustomerInfoRow mapped = rows.get(0);
        assertThat(mapped.getRole()).isEqualTo("项目最高决策人");
        assertThat(mapped.getTel()).isEqualTo("021-12345678");
        assertThat(mapped.getContactInfo()).isEqualTo("13800138000");
        assertThat(mapped.getReached()).isEqualTo("true");
        assertThat(mapped.getPreference()).isEqualTo("支持");
    }

    @Test
    @DisplayName("evaluationCustomerInfos 未传（null）时不替换客户信息表")
    void customerInfosNull_preserved() {
        service.updateByTenderId("CRM", TENDER_ID, baseRequest().build(), API_USER_ID);

        ArgumentCaptor<InitiationDto> dtoCaptor = ArgumentCaptor.forClass(InitiationDto.class);
        verify(initiationMapper).applyInput(eq(details), dtoCaptor.capture());
        assertThat(dtoCaptor.getValue().getCustomerInfoRows()).isNull();
    }

    // ========== projectPlanGapFiles 三态 ==========

    @Test
    @DisplayName("evaluationBasic 不传 → GAP 附件保留（不触碰附件表）")
    void gapFilesBasicNull_untouched() {
        service.updateByTenderId("CRM", TENDER_ID, baseRequest().build(), API_USER_ID);

        verifyNoInteractions(projectDocumentRepository);
    }

    @Test
    @DisplayName("projectPlanGapFiles 不传（null）→ GAP 附件保留")
    void gapFilesNull_untouched() {
        EvaluationBasicDTO basic = new EvaluationBasicDTO(
                null, null, null, "风险", null, null, null, null, null);

        service.updateByTenderId("CRM", TENDER_ID,
                baseRequest().evaluationBasic(basic).build(), API_USER_ID);

        verifyNoInteractions(projectDocumentRepository);
    }

    @Test
    @DisplayName("projectPlanGapFiles 空数组 → 清空已有 GAP 附件")
    void gapFilesEmpty_clearsExisting() {
        ProjectDocument existingDoc = ProjectDocument.builder().id(100L).projectId(PROJECT_ID).build();
        when(projectDocumentRepository.findByProjectIdAndFiltersOrderByCreatedAtDesc(
                eq(PROJECT_ID), any(String.class), eq(null), eq(null)))
                .thenReturn(List.of(existingDoc));
        EvaluationBasicDTO basic = new EvaluationBasicDTO(
                null, null, null, null, null, null, null, null, List.of());

        service.updateByTenderId("CRM", TENDER_ID,
                baseRequest().evaluationBasic(basic).build(), API_USER_ID);

        verify(projectDocumentRepository).deleteAll(List.of(existingDoc));
        verify(projectDocumentRepository, never()).save(any(ProjectDocument.class));
    }

    @Test
    @DisplayName("projectPlanGapFiles 非空 → 全量替换（先删后建，fileUrl 空白行跳过）")
    void gapFilesNonEmpty_replaces() {
        when(projectDocumentRepository.findByProjectIdAndFiltersOrderByCreatedAtDesc(
                eq(PROJECT_ID), any(String.class), eq(null), eq(null)))
                .thenReturn(List.of());
        EvaluationBasicDTO basic = new EvaluationBasicDTO(
                null, null, null, null, null, null, null, null,
                List.of(new EvaluationBasicDTO.GapFileRef("方案.pdf", "https://crm.example.com/a.pdf"),
                        new EvaluationBasicDTO.GapFileRef("无URL跳过", "  ")));

        service.updateByTenderId("CRM", TENDER_ID,
                baseRequest().evaluationBasic(basic).build(), API_USER_ID);

        ArgumentCaptor<ProjectDocument> docCaptor = ArgumentCaptor.forClass(ProjectDocument.class);
        verify(projectDocumentRepository).save(docCaptor.capture());
        ProjectDocument savedDoc = docCaptor.getValue();
        assertThat(savedDoc.getProjectId()).isEqualTo(PROJECT_ID);
        assertThat(savedDoc.getName()).isEqualTo("方案.pdf");
        assertThat(savedDoc.getFileUrl()).isEqualTo("https://crm.example.com/a.pdf");
        assertThat(savedDoc.getDocumentCategory()).isEqualTo("EVALUATION_GAP");
        assertThat(savedDoc.getLinkedEntityType()).isEqualTo("EVALUATION_GAP");
        assertThat(savedDoc.getLinkedEntityId()).isEqualTo(PROJECT_ID);
        assertThat(savedDoc.getUploaderName()).isEqualTo("CRM-同步");
    }

    // ========== 锁与错误码 ==========

    @Test
    @DisplayName("项目已结项（CLOSED）→ 423 阶段锁")
    void closedStage_returns423() {
        when(projectStageService.currentStage(PROJECT_ID)).thenReturn(ProjectStage.CLOSED);

        assertThatThrownBy(() -> service.updateByTenderId("CRM", TENDER_ID,
                baseRequest().build(), API_USER_ID))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.LOCKED));
    }

    @Test
    @DisplayName("立项已锁定且触碰锁定字段 → 423 立项字段锁")
    void lockedInitiationWithLockedFieldChange_returns423() {
        details.setLocked(Boolean.TRUE);
        // 合并结果改动了锁定字段 ownerUnit → validateUpdate 拒绝
        InitiationFieldPolicy.InitiationInput changed = new InitiationFieldPolicy.InitiationInput(
                "被篡改的采购单位", 3, 12, null, null,
                null, null,
                LocalDateTime.of(2026, 9, 1, 10, 0),
                5L, "电商事业部",
                null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null);
        when(initiationMapper.mergeForUpdate(any(), any())).thenReturn(changed);

        assertThatThrownBy(() -> service.updateByTenderId("CRM", TENDER_ID,
                baseRequest().build(), API_USER_ID))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.LOCKED));
    }

    @Test
    @DisplayName("半成品立项（内部组织字段 NULL）+ 部分回写 → 200 落库（校验不约束请求域外字段）")
    void incompleteInitiationPartialWrite_succeeds() {
        // CRM 首联 422 死锁根因回归（lessons §173）：契约不含内部组织字段入口，合并校验不得约束请求域外字段。
        InitiationFieldPolicy.InitiationInput incomplete = new InitiationFieldPolicy.InitiationInput(
                null, null, null, null, null,
                null, null, null, null, null,
                null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null);
        when(initiationMapper.toInput(details)).thenReturn(incomplete);
        when(initiationMapper.mergeForUpdate(any(), any())).thenReturn(incomplete);

        ProjectUpdateResponse resp = service.updateByTenderId("CRM", TENDER_ID,
                baseRequest().projectName("投标中编辑").build(), API_USER_ID);

        assertThat(resp.getApplied()).isTrue();
        verify(initiationRepository).save(any(ProjectInitiationDetails.class));
    }

    @Test
    @DisplayName("立项数据不存在 → 404")
    void initiationMissing_throws404() {
        when(initiationRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateByTenderId("CRM", TENDER_ID,
                baseRequest().build(), API_USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(initiationRepository, never()).save(any());
    }
}
