// Input: ProjectUpdateRequest（接口六 §3.6）+ (sourceSystem, sourceId) 路径参数 + API Key 用户
// Output: ProjectUpdateResponse（projectId/projectName/updatedAt）；定位失败抛 ResourceNotFoundException
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

/**
 * 接口六：项目修改（v3.12 新增）命令服务。
 *
 * <p>按 (sourceSystem, sourceId) 先定位标讯再定位其关联项目（或按 projectId 直接定位），
 * 部分更新项目名称、立项投标信息与客户信息表。<b>仅写入项目数据，不修改标讯任何字段。</b>
 *
 * <p>更新语义：非 null 字段覆盖现值，null/未传保留；{@code evaluationCustomerInfos}
 * 传入即全量替换；{@code projectPlanGapFiles} 空数组=清空、不传=保留、非空=替换。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectIntegrationCommandService {

    private static final DateTimeFormatter RESPONSE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final TenderIntegrationResolver tenderResolver;
    private final ProjectRepository projectRepository;
    private final ProjectInitiationDetailsRepository initiationRepository;
    private final ProjectInitiationMapper initiationMapper;
    private final ProjectStageService projectStageService;
    private final ProjectDocumentRepository projectDocumentRepository;

    /**
     * 按 (sourceSystem, sourceId) 定位项目并部分更新。
     *
     * @throws ResourceNotFoundException 标讯不存在 / 来源系统下未找到该标讯关联的项目 / 立项数据不存在（→ 404）
     * @throws ResponseStatusException   423 阶段锁或立项字段锁；422 合并后字段校验失败
     */
    @Transactional
    @Auditable(action = "UPDATE_PROJECT_FROM_INTEGRATION", entityType = "ProjectInitiationDetails",
            description = "外部集成接口六：项目修改", projectScoped = true)
    public ProjectUpdateResponse updateByExternalId(String sourceSystem, String sourceId,
                                                    ProjectUpdateRequest request, Long userId) {
        Project project = resolveProject(sourceSystem, sourceId, request.getProjectId());
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
                .build();
    }

    /**
     * 定位项目：{@code projectId} 传入时优先按 ID 定位；否则按 (sourceSystem, sourceId)
     * 定位标讯再取其关联项目。项目与标讯一一对应是文档前提，多关联时取首个并告警。
     */
    private Project resolveProject(String sourceSystem, String sourceId, Long projectId) {
        if (projectId != null) {
            return projectRepository.findById(projectId)
                    .orElseThrow(() -> new ResourceNotFoundException("Project", String.valueOf(projectId)));
        }
        Tender tender = tenderResolver.resolveTender(sourceSystem, sourceId, null);
        List<Project> projects = projectRepository.findByTenderId(tender.getId());
        if (projects.isEmpty()) {
            throw new ResourceNotFoundException(
                    "来源系统下未找到该标讯关联的项目: sourceSystem=" + sourceSystem + ", sourceId=" + sourceId);
        }
        if (projects.size() > 1) {
            log.warn("Multiple projects ({}) linked to tender {}, using first projectId={}",
                    projects.size(), tender.getId(), projects.get(0).getId());
        }
        return projects.get(0);
    }

    /**
     * 立项字段合并：复用 {@link ProjectInitiationMapper} 的 merge + Policy 校验，
     * 口径与 {@code ProjectInitiationService.doUpdate} 一致（null 字段保留原值）。
     * 客户信息表传入即全量替换（空数组 = 清空为空表）。
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
        var fullDecision = InitiationFieldPolicy.validate(requestedInput);
        if (!fullDecision.allowed()) {
            var deny = (InitiationFieldPolicy.Decision.Deny) fullDecision;
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, deny.reasonText());
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
