package com.xiyu.bid.projectworkflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.projectworkflow.core.ScoreDraftPolicy;
import com.xiyu.bid.projectworkflow.dto.ProjectScoreDraftDTO;
import com.xiyu.bid.projectworkflow.dto.ProjectScoreDraftGenerateRequest;
import com.xiyu.bid.projectworkflow.dto.ProjectScoreDraftParseResponse;
import com.xiyu.bid.projectworkflow.dto.ProjectScoreDraftUpdateRequest;
import com.xiyu.bid.projectworkflow.dto.ProjectTaskViewDTO;
import com.xiyu.bid.projectworkflow.entity.ProjectScoreDraft;
import com.xiyu.bid.projectworkflow.repository.ProjectScoreDraftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
class ProjectScoreDraftWorkflowService {

    private final ProjectWorkflowGuardService guardService;
    private final ProjectScoreDraftRepository projectScoreDraftRepository;
    private final ScoreDraftParserService scoreDraftParserService;
    private final ProjectTaskWorkflowService projectTaskWorkflowService;
    private final ObjectMapper objectMapper;

    List<ProjectScoreDraftDTO> getProjectScoreDrafts(Long projectId) {
        guardService.requireProject(projectId);
        return projectScoreDraftRepository.findByProjectIdOrderByCategoryAscSourceTableIndexAscSourceRowIndexAsc(projectId)
                .stream()
                .map(this::toScoreDraftDTO)
                .toList();
    }

    ProjectScoreDraftParseResponse parseProjectScoreDrafts(Long projectId, MultipartFile file) {
        guardService.requireWorkflowMutationProject(projectId);
        clearNonGeneratedDrafts(projectId);
        List<ProjectScoreDraftDTO> draftDTOs = projectScoreDraftRepository.saveAll(scoreDraftParserService.parse(projectId, file))
                .stream()
                .map(this::toScoreDraftDTO)
                .toList();
        return buildParseResponse(draftDTOs);
    }

    ProjectScoreDraftDTO updateProjectScoreDraft(Long projectId, Long draftId, ProjectScoreDraftUpdateRequest request) {
        guardService.requireWorkflowMutationProject(projectId);
        ProjectScoreDraft draft = guardService.requireDraft(projectId, draftId);
        ScoreDraftPolicy.UpdateDecision decision = ScoreDraftPolicy.decideUpdate(new ScoreDraftPolicy.UpdateCommand(
                toCoreStatus(draft.getStatus()),
                request.getAssigneeId(),
                request.getAssigneeName(),
                request.getDueDate(),
                request.getGeneratedTaskTitle(),
                request.getGeneratedTaskDescription(),
                toCoreStatus(request.getStatus()),
                request.getSkipReason()
        ));
        if (!decision.ok()) {
            throw toScoreDraftRuleException(decision.failure());
        }
        applyScoreDraftUpdate(draft, decision);
        return toScoreDraftDTO(projectScoreDraftRepository.save(draft));
    }

    List<ProjectTaskViewDTO> generateTasksFromScoreDrafts(Long projectId, ProjectScoreDraftGenerateRequest request) {
        guardService.requireWorkflowMutationProject(projectId);
        List<ProjectScoreDraft> drafts = request.getDraftIds().stream()
                .map(draftId -> guardService.requireDraft(projectId, draftId))
                .toList();
        ScoreDraftPolicy.GenerationDecision decision = ScoreDraftPolicy.decideGeneration(
                drafts.stream().map(draft -> toCoreStatus(draft.getStatus())).toList()
        );
        if (!decision.ok()) {
            throw toScoreDraftRuleException(decision.failure());
        }
        return drafts.stream()
                .map(this::createTaskFromDraft)
                .toList();
    }

    void clearNonGeneratedDrafts(Long projectId) {
        guardService.requireWorkflowMutationProject(projectId);
        projectScoreDraftRepository.deleteByProjectIdAndStatusIn(
                projectId,
                List.of(ProjectScoreDraft.Status.DRAFT, ProjectScoreDraft.Status.READY, ProjectScoreDraft.Status.SKIPPED)
        );
    }

    private ProjectTaskViewDTO createTaskFromDraft(ProjectScoreDraft draft) {
        ProjectTaskViewDTO createdTask = projectTaskWorkflowService.createTaskFromDraft(draft);
        draft.setGeneratedTaskId(createdTask.getId());
        draft.setStatus(ProjectScoreDraft.Status.GENERATED);
        projectScoreDraftRepository.save(draft);
        return createdTask;
    }

    private void applyScoreDraftUpdate(ProjectScoreDraft draft, ScoreDraftPolicy.UpdateDecision decision) {
        draft.setAssigneeId(decision.assigneeId());
        draft.setAssigneeName(decision.assigneeName());
        draft.setDueDate(decision.dueDate());
        if (decision.generatedTaskTitle() != null) {
            draft.setGeneratedTaskTitle(decision.generatedTaskTitle());
        }
        if (decision.generatedTaskDescription() != null) {
            draft.setGeneratedTaskDescription(decision.generatedTaskDescription());
        }
        draft.setStatus(toEntityStatus(decision.status()));
        draft.setSkipReason(decision.skipReason());
    }

    private ProjectScoreDraftDTO toScoreDraftDTO(ProjectScoreDraft draft) {
        return ProjectScoreDraftDTO.builder()
                .id(draft.getId())
                .projectId(draft.getProjectId())
                .sourceFileName(draft.getSourceFileName())
                .category(draft.getCategory())
                .scoreItemTitle(draft.getScoreItemTitle())
                .scoreRuleText(draft.getScoreRuleText())
                .scoreValueText(draft.getScoreValueText())
                .taskAction(draft.getTaskAction())
                .generatedTaskTitle(draft.getGeneratedTaskTitle())
                .generatedTaskDescription(draft.getGeneratedTaskDescription())
                .suggestedDeliverables(readDeliverables(draft.getSuggestedDeliverables()))
                .assigneeId(draft.getAssigneeId())
                .assigneeName(draft.getAssigneeName())
                .dueDate(draft.getDueDate())
                .status(toDtoStatus(draft.getStatus()))
                .skipReason(draft.getSkipReason())
                .sourcePage(draft.getSourcePage())
                .sourceTableIndex(draft.getSourceTableIndex())
                .sourceRowIndex(draft.getSourceRowIndex())
                .generatedTaskId(draft.getGeneratedTaskId())
                .createdAt(draft.getCreatedAt())
                .updatedAt(draft.getUpdatedAt())
                .build();
    }

    private ProjectScoreDraftParseResponse buildParseResponse(List<ProjectScoreDraftDTO> draftDTOs) {
        return ProjectScoreDraftParseResponse.builder()
                .drafts(draftDTOs)
                .totalCount(draftDTOs.size())
                .draftCount(countByStatus(draftDTOs, ProjectScoreDraftDTO.Status.DRAFT))
                .readyCount(countByStatus(draftDTOs, ProjectScoreDraftDTO.Status.READY))
                .skippedCount(countByStatus(draftDTOs, ProjectScoreDraftDTO.Status.SKIPPED))
                .build();
    }

    private long countByStatus(
            Collection<ProjectScoreDraftDTO> drafts,
            ProjectScoreDraftDTO.Status status
    ) {
        return drafts.stream().filter(draft -> draft.getStatus() == status).count();
    }

    private List<String> readDeliverables(String serializedValue) {
        if (serializedValue == null || serializedValue.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(serializedValue, new TypeReference<>() {});
        } catch (JsonProcessingException ex) {
            return List.of(serializedValue);
        }
    }

    private RuntimeException toScoreDraftRuleException(ScoreDraftPolicy.RuleFailure failure) {
        return switch (failure) {
            case GENERATED_NOT_EDITABLE -> new IllegalStateException("已生成正式任务的草稿不可修改");
            case READY_REQUIRES_ASSIGNEE -> new IllegalArgumentException("生成正式任务前必须指定责任人");
            case ONLY_READY_DRAFTS_CAN_GENERATE_TASKS -> new IllegalArgumentException("仅 READY 状态的评分草稿可生成正式任务");
        };
    }

    private ScoreDraftPolicy.DraftStatus toCoreStatus(ProjectScoreDraft.Status status) {
        if (status == null) {
            return null;
        }
        return ScoreDraftPolicy.DraftStatus.valueOf(status.name());
    }

    private ScoreDraftPolicy.DraftStatus toCoreStatus(ProjectScoreDraftUpdateRequest.Status status) {
        if (status == null) {
            return null;
        }
        return ScoreDraftPolicy.DraftStatus.valueOf(status.name());
    }

    private ProjectScoreDraft.Status toEntityStatus(ScoreDraftPolicy.DraftStatus status) {
        if (status == null) {
            return null;
        }
        return ProjectScoreDraft.Status.valueOf(status.name());
    }

    private ProjectScoreDraftDTO.Status toDtoStatus(ProjectScoreDraft.Status status) {
        if (status == null) {
            return null;
        }
        return ProjectScoreDraftDTO.Status.valueOf(status.name());
    }
}
