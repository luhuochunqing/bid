// Input: 提交/更新立项请求 + 当前用户
// Output: InitiationViewDto；通过 InitiationFieldPolicy 校验 + 持久化 + 审计
// Pos: project/service/ - 编排层（不含纯规则），映射逻辑委托 ProjectInitiationMapper
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.service;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.project.entity.ProjectInitiationDetails;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.project.core.InitiationFieldPolicy;
import com.xiyu.bid.project.core.ProjectFieldLockPolicy;
import com.xiyu.bid.project.core.ProjectStage;
import com.xiyu.bid.project.dto.InitiationDto;
import com.xiyu.bid.project.dto.InitiationViewDto;
import com.xiyu.bid.project.notification.ProjectNotificationService;
import com.xiyu.bid.project.repository.ProjectInitiationDetailsRepository;
import com.xiyu.bid.project.repository.ProjectLeadAssignmentRepository;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.tender.dto.EvaluationBasicDTO;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ProjectInitiationService {

    private final ProjectInitiationDetailsRepository repository;
    private final ProjectRepository projectRepository;
    private final ProjectStageService projectStageService;
    private final ProjectAccessScopeService projectAccessScopeService;
    private final UserRepository userRepository;
    private final ProjectInitiationMapper mapper;
    private final ProjectLeadAssignmentRepository leadRepo;
    private final ProjectNotificationService notificationService;
    private final ProjectDocumentRepository projectDocumentRepository;

    @Auditable(action = "SUBMIT_INITIATION", entityType = "ProjectInitiationDetails", description = "提交项目立项审核")
    public InitiationViewDto submit(Long projectId, InitiationDto req, Long currentUserId) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        var project = mustGetProject(projectId);

        if (req.getOwnerUserId() == null) {
            req.setOwnerUserId(project.getManagerId() != null ? project.getManagerId() : currentUserId);
        }
        if (req.getDepartmentSnapshot() == null || req.getDepartmentSnapshot().isBlank()) {
            String dept = userRepository.findById(req.getOwnerUserId())
                    .map(User::getDepartmentName)
                    .filter(d -> !d.isBlank())
                    .orElse("项目部");
            req.setDepartmentSnapshot(dept);
        }

        var input = mapper.toInput(req);
        var decision = InitiationFieldPolicy.validate(input);
        if (!decision.allowed()) {
            var deny = (InitiationFieldPolicy.Decision.Deny) decision;
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, deny.reasonText());
        }
        ProjectInitiationDetails entity = repository.findByProjectId(projectId)
                .orElseGet(() -> ProjectInitiationDetails.builder()
                        .projectId(projectId)
                        .createdBy(currentUserId)
                        .locked(Boolean.FALSE)
                        .reviewStatus("DRAFT")
                        .build());
        if ("PENDING_REVIEW".equals(entity.getReviewStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "项目已提交审核，请勿重复提交");
        }
        if ("APPROVED".equals(entity.getReviewStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "项目已通过审核，不可重新提交");
        }
        mapper.applyInput(entity, req);
        entity.setReviewStatus("PENDING_REVIEW");
        entity.setLocked(Boolean.TRUE);
        entity.setRejectionReason(null);
        entity.setUpdatedBy(currentUserId);
        ProjectInitiationDetails saved = repository.save(entity);
        log.info("Initiation submitted for review: projectId={}, userId={}, reviewStatus={}",
                projectId, currentUserId, saved.getReviewStatus());

        // 通知 #1: 项目负责人提交立项审核 → admin/bid_admin/bid_lead/bid_senior
        notificationService.notifyInitiationSubmitted(projectId, currentUserId);

        return mapper.toView(saved);
    }

    @Auditable(action = "UPDATE_INITIATION", entityType = "ProjectInitiationDetails", description = "更新项目立项")
    public InitiationViewDto update(Long projectId, InitiationDto req, Long currentUserId) {
        return doUpdate(projectId, req, currentUserId);
    }

    private InitiationViewDto doUpdate(Long projectId, InitiationDto req, Long currentUserId) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        var project = mustGetProject(projectId);
        ProjectStage stage = projectStageService.currentStage(projectId);
        var lockDecision0 = ProjectFieldLockPolicy.assertWritable(stage, "initiation");
        if (!lockDecision0.allowed()) {
            var deny = (ProjectFieldLockPolicy.Decision.Deny) lockDecision0;
            throw new ResponseStatusException(HttpStatus.LOCKED, deny.reason());
        }
        ProjectInitiationDetails existing = repository.findByProjectId(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("ProjectInitiationDetails", String.valueOf(projectId)));

        if (req.getOwnerUserId() == null && existing.getOwnerUserId() == null) {
            req.setOwnerUserId(project.getManagerId() != null ? project.getManagerId() : currentUserId);
        }
        if ((req.getDepartmentSnapshot() == null || req.getDepartmentSnapshot().isBlank())
                && (existing.getDepartmentSnapshot() == null || existing.getDepartmentSnapshot().isBlank())) {
            Long ownerId = req.getOwnerUserId() != null ? req.getOwnerUserId() : existing.getOwnerUserId();
            String dept = userRepository.findById(ownerId)
                    .map(User::getDepartmentName)
                    .filter(d -> !d.isBlank())
                    .orElse("项目部");
            req.setDepartmentSnapshot(dept);
        }

        boolean lockedAlready = Boolean.TRUE.equals(existing.getLocked());
        var existingInput = mapper.toInput(existing);
        var requestedInput = mapper.mergeForUpdate(existingInput, req);
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
        mapper.applyInput(existing, mapper.toDto(requestedInput));
        existing.setUpdatedBy(currentUserId);
        ProjectInitiationDetails saved = repository.save(existing);
        log.info("Initiation updated: projectId={}, userId={}, reviewStatus={}",
                projectId, currentUserId, saved.getReviewStatus());
        return mapper.toView(saved);
    }

    @Transactional(readOnly = true)
    public Optional<InitiationViewDto> getByProject(Long projectId) {
        return repository.findByProjectId(projectId).map(det -> {
            InitiationViewDto dto = mapper.toView(det);
            leadRepo.findByProjectId(projectId).ifPresent(lead -> {
                dto.setPrimaryLeadUserId(lead.getPrimaryLeadUserId());
                dto.setSecondaryLeadUserId(lead.getSecondaryLeadUserId());
            });
            // CO-323: 回填评估表带入的 GAP 附件（project_documents，documentCategory=EVALUATION_GAP，
            // 对应 TenderEvaluationDocumentService.ENTITY_TYPE_EVALUATION_GAP）
            List<ProjectDocument> gapDocs = projectDocumentRepository
                    .findByProjectIdAndFiltersOrderByCreatedAtDesc(projectId, "EVALUATION_GAP", null, null);
            dto.setProjectPlanGapFiles(gapDocs.stream()
                    .map(d -> new EvaluationBasicDTO.GapFileRef(d.getName(), d.getFileUrl()))
                    .toList());
            return dto;
        });
    }

    private Project mustGetProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", String.valueOf(projectId)));
    }
}
