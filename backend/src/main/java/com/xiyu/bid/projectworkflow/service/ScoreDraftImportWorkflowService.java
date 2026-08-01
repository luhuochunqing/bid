// Input: ScoringCriteriaClassificationAppService + ScoreDraftFromProfileAssembler
// Output: 从 AI 分析结果导入评分草稿
// Pos: projectworkflow/service - 评分草稿导入工作流服务

package com.xiyu.bid.projectworkflow.service;

import com.xiyu.bid.biddraftagent.application.ScoringCriteriaClassificationAppService;
import com.xiyu.bid.biddraftagent.domain.ScoringCriterion;
import com.xiyu.bid.exception.BusinessException;
import com.xiyu.bid.projectworkflow.dto.ProjectScoreDraftDTO;
import com.xiyu.bid.projectworkflow.dto.ProjectScoreDraftParseResponse;
import com.xiyu.bid.projectworkflow.entity.ProjectScoreDraft;
import com.xiyu.bid.projectworkflow.parser.ScoreDraftFromProfileAssembler;
import com.xiyu.bid.projectworkflow.repository.ProjectScoreDraftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 从 AI 评分标准分析结果导入评分草稿的工作流服务。
 * 独立于 ProjectScoreDraftWorkflowService 以控制 collaborator 数量。
 */
@Service
@RequiredArgsConstructor
class ScoreDraftImportWorkflowService {

    private final ProjectWorkflowGuardService guardService;
    private final ProjectScoreDraftRepository projectScoreDraftRepository;
    private final ScoringCriteriaClassificationAppService scoringCriteriaClassificationService;
    private final ScoreDraftFromProfileAssembler scoreDraftFromProfileAssembler;

    ProjectScoreDraftParseResponse importFromAiAnalysis(Long projectId) {
        guardService.requireWorkflowMutationProject(projectId);
        var result = scoringCriteriaClassificationService.classifyForProject(projectId);
        if (!result.isStructured()) {
            throw new BusinessException("该项目暂无可导入的 AI 评分标准分析结果，请先进行 AI 评分标准解析");
        }
        List<ScoringCriterion> criteria = result.structuredItems();
        String sourceFileName = scoringCriteriaClassificationService.getSnapshotFileName(projectId);
        clearNonGeneratedDrafts(projectId);
        List<ProjectScoreDraftDTO> draftDTOs = projectScoreDraftRepository
                .saveAll(scoreDraftFromProfileAssembler.assemble(projectId, sourceFileName, criteria))
                .stream()
                .map(this::toScoreDraftDTO)
                .toList();
        return buildParseResponse(draftDTOs);
    }

    private void clearNonGeneratedDrafts(Long projectId) {
        projectScoreDraftRepository.deleteByProjectIdAndStatusIn(
                projectId,
                List.of(ProjectScoreDraft.Status.DRAFT, ProjectScoreDraft.Status.READY, ProjectScoreDraft.Status.SKIPPED)
        );
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
                .suggestedDeliverables(List.of())
                .assigneeId(draft.getAssigneeId())
                .assigneeName(draft.getAssigneeName())
                .dueDate(draft.getDueDate())
                .status(ProjectScoreDraftDTO.Status.valueOf(draft.getStatus().name()))
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

    private long countByStatus(List<ProjectScoreDraftDTO> drafts, ProjectScoreDraftDTO.Status status) {
        return drafts.stream().filter(d -> d.getStatus() == status).count();
    }
}
