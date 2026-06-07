package com.xiyu.bid.documenteditor.service;

import com.xiyu.bid.documenteditor.entity.DocumentSection;
import com.xiyu.bid.documenteditor.entity.DocumentStructure;
import com.xiyu.bid.documenteditor.repository.DocumentSectionRepository;
import com.xiyu.bid.documenteditor.repository.DocumentStructureRepository;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.service.ProjectAccessScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
class DocumentEditorGuard {

    private final DocumentStructureRepository structureRepository;
    private final DocumentSectionRepository sectionRepository;
    private final ProjectAccessScopeService projectAccessScopeService;

    DocumentStructure requireStructureForProject(Long projectId) {
        if (projectId == null) {
            throw new IllegalArgumentException("Project ID is required");
        }
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        return structureRepository.findByProjectId(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("DocumentStructure", String.valueOf(projectId)));
    }

    void requireStructureExists(Long structureId) {
        if (!structureRepository.existsById(structureId)) {
            throw new ResourceNotFoundException("DocumentStructure", String.valueOf(structureId));
        }
    }

    DocumentSection requireSection(Long sectionId) {
        if (sectionId == null) {
            throw new IllegalArgumentException("Section ID is required");
        }
        return sectionRepository.findById(sectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Section", String.valueOf(sectionId)));
    }

    DocumentSection requireProjectSection(Long projectId, Long sectionId) {
        DocumentStructure structure = requireStructureForProject(projectId);
        DocumentSection section = requireSection(sectionId);
        requireSectionBelongsToStructure(section, structure.getId(), "Section does not belong to the specified project");
        return section;
    }

    void requireSectionBelongsToStructure(DocumentSection section, Long structureId, String message) {
        if (!Objects.equals(section.getStructureId(), structureId)) {
            throw new IllegalArgumentException(message);
        }
    }
}
