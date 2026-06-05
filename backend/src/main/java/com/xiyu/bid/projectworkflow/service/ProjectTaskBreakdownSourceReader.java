package com.xiyu.bid.projectworkflow.service;

import com.xiyu.bid.documenteditor.entity.DocumentSection;
import com.xiyu.bid.documenteditor.repository.DocumentSectionRepository;
import com.xiyu.bid.documenteditor.repository.DocumentStructureRepository;
import com.xiyu.bid.projectworkflow.core.TaskBreakdownPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
class ProjectTaskBreakdownSourceReader {

    private final ProjectTaskRequirementSourceGateway requirementSourceGateway;
    private final DocumentStructureRepository documentStructureRepository;
    private final DocumentSectionRepository documentSectionRepository;

    List<TaskBreakdownPolicy.SourceSnapshot> collectRequirementSources(Long projectId) {
        return requirementSourceGateway.latestRequirementSourcesForProject(projectId);
    }

    List<TaskBreakdownPolicy.SourceSnapshot> collectSectionSources(Long projectId) {
        return documentStructureRepository.findByProjectId(projectId)
                .map(structure -> collectSectionSourcesForStructure(structure.getId()))
                .orElse(List.of());
    }

    private List<TaskBreakdownPolicy.SourceSnapshot> collectSectionSourcesForStructure(Long structureId) {
        List<DocumentSection> sections = documentSectionRepository.findByStructureId(structureId);
        Set<Long> topLevelIds = sections.stream()
                .filter(section -> section.getParentId() == null)
                .map(DocumentSection::getId)
                .collect(Collectors.toSet());
        return sections.stream()
                .filter(section -> isTopOrSecondLevel(section, topLevelIds))
                .sorted(Comparator.comparing(
                        DocumentSection::getOrderIndex,
                        Comparator.nullsLast(Integer::compareTo)
                ))
                .map(section -> new TaskBreakdownPolicy.SourceSnapshot(
                        "section",
                        section.getTitle(),
                        section.getContent()
                ))
                .toList();
    }

    private boolean isTopOrSecondLevel(DocumentSection section, Set<Long> topLevelIds) {
        if (section.getParentId() == null) {
            return true;
        }
        return topLevelIds.contains(section.getParentId());
    }
}
