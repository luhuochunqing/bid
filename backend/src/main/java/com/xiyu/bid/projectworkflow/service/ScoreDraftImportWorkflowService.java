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
import com.xiyu.bid.projectworkflow.parser.ProjectScoreDraftMapper;
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
    private final ProjectScoreDraftMapper draftMapper;

    ProjectScoreDraftParseResponse importFromAiAnalysis(Long projectId) {
        guardService.requireWorkflowMutationProject(projectId);
        var result = scoringCriteriaClassificationService.classifyForProject(projectId);
        if (!result.isStructured()) {
            throw new BusinessException("该项目暂无可导入的 AI 评分标准分析结果，请先进行 AI 评分标准解析");
        }
        List<ScoringCriterion> criteria = result.structuredItems();
        String sourceFileName = result.sourceFileName();
        clearNonGeneratedDrafts(projectId);
        List<ProjectScoreDraftDTO> draftDTOs = projectScoreDraftRepository
                .saveAll(scoreDraftFromProfileAssembler.assemble(projectId, sourceFileName, criteria))
                .stream()
                .map(draftMapper::toDTO)
                .toList();
        return draftMapper.toParseResponse(draftDTOs);
    }

    private void clearNonGeneratedDrafts(Long projectId) {
        projectScoreDraftRepository.deleteByProjectIdAndStatusIn(
                projectId,
                List.of(ProjectScoreDraft.Status.DRAFT, ProjectScoreDraft.Status.READY, ProjectScoreDraft.Status.SKIPPED)
        );
    }
}
