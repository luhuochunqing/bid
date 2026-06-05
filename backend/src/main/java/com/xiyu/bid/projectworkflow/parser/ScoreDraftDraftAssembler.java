package com.xiyu.bid.projectworkflow.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.projectworkflow.entity.ProjectScoreDraft;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ScoreDraftDraftAssembler {

    private final ObjectMapper objectMapper;

    public List<ProjectScoreDraft> assemble(Long projectId, String fileName, List<ParsedSection> sections) {
        List<ProjectScoreDraft> drafts = new ArrayList<>();
        for (ParsedSection section : sections) {
            int rowIndex = 0;
            for (DraftSeed seed : section.seeds()) {
                drafts.add(buildDraft(projectId, fileName, section.category(), seed, section.sectionIndex(), rowIndex++));
            }
        }
        return drafts;
    }

    private ProjectScoreDraft buildDraft(Long projectId,
                                         String fileName,
                                         String category,
                                         DraftSeed seed,
                                         int tableIndex,
                                         int rowIndex) {
        return ProjectScoreDraft.builder()
                .projectId(projectId)
                .sourceFileName(fileName)
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

    private String serializeDeliverables(List<String> deliverables) {
        try {
            return objectMapper.writeValueAsString(deliverables);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("评分草稿交付物序列化失败", ex);
        }
    }
}
