// Input: ProjectScoreDraft 实体 / DraftSeed
// Output: ProjectScoreDraftDTO / ProjectScoreDraftParseResponse
// Pos: projectworkflow/parser - 评分草稿共享映射工具

package com.xiyu.bid.projectworkflow.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.projectworkflow.dto.ProjectScoreDraftDTO;
import com.xiyu.bid.projectworkflow.dto.ProjectScoreDraftParseResponse;
import com.xiyu.bid.projectworkflow.entity.ProjectScoreDraft;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * 评分草稿共享映射工具，消除多个导入路径的重复代码。
 */
@Component
public final class ProjectScoreDraftMapper {

    private final ObjectMapper objectMapper;

    public ProjectScoreDraftMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ProjectScoreDraftDTO toDTO(ProjectScoreDraft draft) {
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

    public ProjectScoreDraftParseResponse toParseResponse(List<ProjectScoreDraftDTO> draftDTOs) {
        return ProjectScoreDraftParseResponse.builder()
                .drafts(draftDTOs)
                .totalCount(draftDTOs.size())
                .draftCount(countByStatus(draftDTOs, ProjectScoreDraftDTO.Status.DRAFT))
                .readyCount(countByStatus(draftDTOs, ProjectScoreDraftDTO.Status.READY))
                .skippedCount(countByStatus(draftDTOs, ProjectScoreDraftDTO.Status.SKIPPED))
                .build();
    }

    public String serializeDeliverables(List<String> deliverables) {
        try {
            return objectMapper.writeValueAsString(deliverables);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("评分草稿交付物序列化失败", ex);
        }
    }

    public ProjectScoreDraft buildDraft(Long projectId, String sourceFileName, String category,
                                         DraftSeed seed, int tableIndex, int rowIndex) {
        return ProjectScoreDraft.builder()
                .projectId(projectId)
                .sourceFileName(sourceFileName)
                .category(category)
                .scoreItemTitle(seed.scoreItemTitle())
                .scoreRuleText(seed.scoreRuleText())
                .scoreValueText(seed.scoreValueText())
                .taskAction(seed.taskAction())
                .generatedTaskTitle(seed.generatedTaskTitle())
                .generatedTaskDescription(seed.generatedTaskDescription())
                .suggestedDeliverables(serializeDeliverables(seed.deliverables()))
                .status(ProjectScoreDraft.Status.DRAFT)
                .sourcePage(null)
                .sourceTableIndex(tableIndex)
                .sourceRowIndex(rowIndex)
                .build();
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

    private long countByStatus(Collection<ProjectScoreDraftDTO> drafts, ProjectScoreDraftDTO.Status status) {
        return drafts.stream().filter(d -> d.getStatus() == status).count();
    }

    private ProjectScoreDraftDTO.Status toDtoStatus(ProjectScoreDraft.Status status) {
        if (status == null) {
            return null;
        }
        return ProjectScoreDraftDTO.Status.valueOf(status.name());
    }
}
