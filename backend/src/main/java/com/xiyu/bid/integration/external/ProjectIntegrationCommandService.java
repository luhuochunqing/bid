// Input: ProjectUpdateRequest（接口六 §3.6）+ (sourceSystem, tenderId) 路径参数 + API Key 用户
// Output: ProjectUpdateResponse（applied=true 全量/部分更新；applied=false 跳过）；定位失败抛 ResourceNotFoundException
// Pos: integration/external/ - 对外集成接口六编排层（纯核心映射见 ProjectUpdateMapper）
// 维护声明: 立项合并校验口径与 ProjectInitiationService.doUpdate 对齐；
//          GAP 附件三态语义与 TenderEvaluationGapFilesSync（CO-262 P1-3）对齐。
package com.xiyu.bid.integration.external;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.project.core.InitiationFieldPolicy;
import com.xiyu.bid.project.core.ProjectFieldLockPolicy;
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
import com.xiyu.bid.tender.service.TenderEvaluationDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * 接口六：项目修改（v3.12 新增）命令服务。
 *
 * <p>按 (sourceSystem, tenderId) 先按标讯主键定位标讯再定位其关联项目
 * （或按 projectId 直接定位并做一致性断言），部分更新项目名称、立项投标信息与客户信息表。
 * <b>仅写入项目数据，不修改标讯任何字段。</b>
 *
 * <p>定位键为平台内部主键 tenderId：与推送标讯时的 external_id 幂等键彻底解耦，
 * 手工建标讯（无 external_id）与推送建标讯均可被外部触达。
 *
 * <p>更新语义：非 null 字段覆盖现值，null/未传保留；{@code evaluationCustomerInfos}
 * 传入即全量替换；{@code projectPlanGapFiles} 空数组=清空、不传=保留、非空=替换。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectIntegrationCommandService {

    private static final DateTimeFormatter RESPONSE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final TenderRepository tenderRepository;
    private final ProjectRepository projectRepository;
    private final ProjectInitiationDetailsRepository initiationRepository;
    private final ProjectInitiationMapper initiationMapper;
    private final ProjectStageService projectStageService;
    private final ProjectDocumentRepository projectDocumentRepository;

    /**
     * 按 (sourceSystem, tenderId) 定位项目并部分更新。
     *
     * @throws ResourceNotFoundException 标讯不存在 / 立项数据不存在（→ 404）
     * @throws IllegalArgumentException  projectId 与路径 tenderId 不一致 / 来源系统与标讯不符（→ 400）
     * @throws ResponseStatusException   423 阶段锁或立项字段锁；422 合并后字段校验失败
     */
    @Transactional
    @Auditable(action = "UPDATE_PROJECT_FROM_INTEGRATION", entityType = "ProjectInitiationDetails",
            description = "外部集成接口六：项目修改", projectScoped = true)
    public ProjectUpdateResponse updateByTenderId(String sourceSystem, Long tenderId,
                                                  ProjectUpdateRequest request, Long userId) {
        Optional<Project> resolved = resolveProject(sourceSystem, tenderId, request.getProjectId());
        if (resolved.isEmpty()) {
            // 标讯存在但尚未立项：不视为错误（立项时序差由调用方按 applied=false 感知），避免 404 误报
            log.warn("INTEGRATION project update skipped: sourceSystem={}, tenderId={}, 无关联项目(未立项), applied=false",
                    sourceSystem, tenderId);
            return ProjectUpdateResponse.builder()
                    .applied(false)
                    .build();
        }
        Project project = resolved.get();
        Long projectId = project.getId();

        // 阶段锁：与前端立项保存同策略（ProjectInitiationService.doUpdate）
        ProjectStage stage = projectStageService.currentStage(projectId);
        var stageLock = ProjectFieldLockPolicy.assertWritable(stage, "initiation");
        if (!stageLock.allowed()) {
            var deny = (ProjectFieldLockPolicy.Decision.Deny) stageLock;
            throw new ResponseStatusException(HttpStatus.LOCKED, deny.reason());
        }

        // 项目名称（项目公共信息）：非空时覆盖
        if (request.getProjectName() != null && !request.getProjectName().isBlank()) {
            project.setName(request.getProjectName().trim());
            projectRepository.save(project);
        }

        ProjectInitiationDetails existing = initiationRepository.findByProjectId(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("ProjectInitiationDetails", String.valueOf(projectId)));

        InitiationDto mergedDto = mergeInitiation(existing, request);
        initiationMapper.applyInput(existing, mergedDto);
        existing.setUpdatedBy(userId);
        ProjectInitiationDetails saved = initiationRepository.save(existing);

        applyGapFiles(projectId, request.getEvaluationBasic());

        log.info("INTEGRATION project updated: projectId={}, projectNameChanged={}, basicPresent={}, customerInfosReplaced={}",
                projectId,
                request.getProjectName() != null && !request.getProjectName().isBlank(),
                request.getEvaluationBasic() != null,
                request.getEvaluationCustomerInfos() != null);

        LocalDateTime updatedAt = saved.getUpdatedAt() != null ? saved.getUpdatedAt() : LocalDateTime.now();
        return ProjectUpdateResponse.builder()
                .projectId(projectId)
                .projectName(project.getName())
                .updatedAt(updatedAt.format(RESPONSE_TIME_FMT))
                .applied(true)
                .build();
    }

    /**
     * 定位项目：{@code projectId} 传入时优先按 ID 定位（并断言其关联标讯即路径 tenderId）；
     * 否则按标讯主键查标讯再取其关联项目。
     *
     * <p>返回空表示"标讯存在但未立项"，由调用方转为 applied=false 跳过响应。
     * 项目与标讯一一对应是文档前提，多关联时取首个并告警。
     */
    private Optional<Project> resolveProject(String sourceSystem, Long tenderId, Long projectId) {
        if (projectId != null) {
            Project project = projectRepository.findById(projectId)
                    .orElseThrow(() -> new ResourceNotFoundException("Project", String.valueOf(projectId)));
            assertProjectMatchesTender(project, tenderId);
            return Optional.of(project);
        }
        Tender tender = tenderRepository.findById(tenderId)
                .orElseThrow(() -> new ResourceNotFoundException("标讯不存在: id=" + tenderId));
        assertSourceSystemMatches(sourceSystem, tender);

        List<Project> projects = projectRepository.findByTenderId(tender.getId());
        if (projects.isEmpty()) {
            return Optional.empty();
        }
        if (projects.size() > 1) {
            log.warn("Multiple projects ({}) linked to tender {}, using first projectId={}",
                    projects.size(), tender.getId(), projects.get(0).getId());
        }
        return projects.stream().findFirst();
    }

    /**
     * 一致性断言（防越权写）：projectId 与路径 tenderId 同时传入时，
     * 项目实际关联的标讯必须就是该 tenderId，不等拒绝（400）。
     * 项目尚未关联标讯（tenderId=null，如手工立项早期数据）时跳过校验放行。
     */
    private void assertProjectMatchesTender(Project project, Long tenderId) {
        if (project.getTenderId() != null && !project.getTenderId().equals(tenderId)) {
            throw new IllegalArgumentException(
                    "projectId=" + project.getId() + " 关联的标讯 id=" + project.getTenderId()
                            + " 与路径 tenderId=" + tenderId + " 不一致");
        }
    }

    /**
     * 来源系统轻量校验：仅约束"有来源键"的标讯（推送建）。手工建标讯 externalId 为 null 时跳过，
     * 避免 M 链路数据被误伤。标讯来自其他系统而路径声称 CRM 时拒绝（400），防止跨系统串写。
     */
    private void assertSourceSystemMatches(String sourceSystem, Tender tender) {
        String externalId = tender.getExternalId();
        if (externalId == null) {
            return;
        }
        if (!externalId.startsWith(sourceSystem + ":")) {
            throw new IllegalArgumentException(
                    "tender " + tender.getId() + " 的来源键 " + externalId
                            + " 与路径 sourceSystem=" + sourceSystem + " 不一致");
        }
    }

    /**
     * 立项字段合并：复用 {@link ProjectInitiationMapper} 的 merge + 字段锁校验
     * （null 字段保留原值）。客户信息表传入即全量替换（空数组 = 清空为空表）。
     *
     * <p>不做合并后全量必填校验：本接口契约只开放评估字段回写（ownerUserId/
     * departmentSnapshot 等内部组织字段不在请求域，patch 恒 null），全量校验会
     * 因既有立项行残缺而拒绝与调用方行为无关的请求（2026-08-28 CRM 首联 422
     * 死锁根因）；立项完整性由平台「提交审核」环节的同一 Policy 把关。
     */
    private InitiationDto mergeInitiation(ProjectInitiationDetails existing, ProjectUpdateRequest request) {
        InitiationDto patch = ProjectUpdateMapper.toInitiationPatch(request.getEvaluationBasic());
        var existingInput = initiationMapper.toInput(existing);
        var requestedInput = initiationMapper.mergeForUpdate(existingInput, patch);

        boolean lockedAlready = Boolean.TRUE.equals(existing.getLocked());
        var lockDecision = InitiationFieldPolicy.validateUpdate(existingInput, requestedInput, lockedAlready);
        if (!lockDecision.allowed()) {
            var deny = (InitiationFieldPolicy.Decision.Deny) lockDecision;
            throw new ResponseStatusException(HttpStatus.LOCKED, deny.reasonText());
        }

        InitiationDto mergedDto = initiationMapper.toDto(requestedInput);
        if (request.getEvaluationCustomerInfos() != null) {
            List<CustomerInfoRow> rows = ProjectUpdateMapper.toCustomerInfoRows(request.getEvaluationCustomerInfos());
            mergedDto.setCustomerInfoRows(rows);
        }
        return mergedDto;
    }

    /**
     * GAP 附件三态（CO-262 P1-3 语义，落地口径与 {@code InitiationPrefillService.copyGapAttachments} 一致）：
     * <ul>
     *   <li>{@code basic == null} 或 {@code projectPlanGapFiles == null} → 保留已有附件</li>
     *   <li>空数组 → 清空项目下 EVALUATION_GAP 附件</li>
     *   <li>非空 → 全量替换（先删后建，linkedEntityId=projectId）</li>
     * </ul>
     */
    private void applyGapFiles(Long projectId, EvaluationBasicDTO basic) {
        if (basic == null || basic.projectPlanGapFiles() == null) {
            return;
        }
        String gapType = TenderEvaluationDocumentService.ENTITY_TYPE_EVALUATION_GAP;
        List<ProjectDocument> existingDocs =
                projectDocumentRepository.findByProjectIdAndFiltersOrderByCreatedAtDesc(projectId, gapType, null, null);
        if (!existingDocs.isEmpty()) {
            projectDocumentRepository.deleteAll(existingDocs);
            projectDocumentRepository.flush();
        }
        if (basic.projectPlanGapFiles().isEmpty()) {
            return;
        }
        for (EvaluationBasicDTO.GapFileRef ref : basic.projectPlanGapFiles()) {
            if (ref == null || ref.fileUrl() == null || ref.fileUrl().isBlank()) {
                continue;
            }
            projectDocumentRepository.save(ProjectDocument.builder()
                    .projectId(projectId)
                    .name(ref.fileName() != null && !ref.fileName().isBlank() ? ref.fileName() : "GAP附件")
                    .documentCategory(gapType)
                    .linkedEntityType(gapType)
                    .linkedEntityId(projectId)
                    .fileUrl(ref.fileUrl())
                    .uploaderName("CRM-同步")
                    .build());
        }
    }
}
