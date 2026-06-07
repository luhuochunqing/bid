package com.xiyu.bid.documenteditor.service;

import com.xiyu.bid.documenteditor.dto.DocumentSectionDTO;
import com.xiyu.bid.documenteditor.dto.SectionCreateRequest;
import com.xiyu.bid.documenteditor.dto.SectionReorderRequest;
import com.xiyu.bid.documenteditor.dto.SectionUpdateRequest;
import com.xiyu.bid.documenteditor.entity.DocumentSection;
import com.xiyu.bid.documenteditor.entity.DocumentSectionAssignment;
import com.xiyu.bid.documenteditor.entity.DocumentSectionLock;
import com.xiyu.bid.documenteditor.repository.DocumentSectionAssignmentRepository;
import com.xiyu.bid.documenteditor.repository.DocumentSectionLockRepository;
import com.xiyu.bid.documenteditor.repository.DocumentSectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
class DocumentSectionCommandService {

    private final DocumentEditorGuard guard;
    private final DocumentSectionRepository sectionRepository;
    private final DocumentSectionAssignmentRepository assignmentRepository;
    private final DocumentSectionLockRepository lockRepository;

    DocumentSectionDTO addSection(Long projectId, SectionCreateRequest request) {
        if (request.getStructureId() == null) {
            throw new IllegalArgumentException("Structure ID is required");
        }
        if (request.getSectionType() == null) {
            throw new IllegalArgumentException("Section type is required");
        }
        if (request.getTitle() == null || request.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("Title is required");
        }

        Long projectStructureId = guard.requireStructureForProject(projectId).getId();
        if (!projectStructureId.equals(request.getStructureId())) {
            throw new IllegalArgumentException("Structure does not belong to the specified project");
        }
        if (request.getParentId() != null) {
            DocumentSection parentSection = guard.requireSection(request.getParentId());
            guard.requireSectionBelongsToStructure(
                    parentSection,
                    request.getStructureId(),
                    "Parent section does not belong to the specified structure"
            );
        }

        DocumentSection section = DocumentSection.builder()
                .structureId(request.getStructureId())
                .parentId(request.getParentId())
                .sectionType(request.getSectionType())
                .title(request.getTitle().trim())
                .content(request.getContent())
                .orderIndex(request.getOrderIndex())
                .metadata(request.getMetadata())
                .build();
        return DocumentEditorMapper.toSectionDTO(sectionRepository.save(section), null, null);
    }

    DocumentSectionDTO updateSection(Long projectId, Long sectionId, SectionUpdateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Update request is required");
        }

        DocumentSection section = guard.requireProjectSection(projectId, sectionId);
        if (request.getTitle() != null) {
            if (request.getTitle().trim().isEmpty()) {
                throw new IllegalArgumentException("Title cannot be empty");
            }
            section.setTitle(request.getTitle().trim());
        }
        if (request.getContent() != null) {
            section.setContent(request.getContent());
        }
        if (request.getMetadata() != null) {
            section.setMetadata(request.getMetadata());
        }
        if (request.getOrderIndex() != null) {
            section.setOrderIndex(request.getOrderIndex());
        }

        DocumentSection saved = sectionRepository.save(section);
        return buildSectionDTO(saved);
    }

    void deleteSection(Long projectId, Long sectionId) {
        if (sectionId == null) {
            throw new IllegalArgumentException("Section ID is required");
        }
        guard.requireProjectSection(projectId, sectionId);
        if (!sectionRepository.findByParentId(sectionId).isEmpty()) {
            throw new IllegalStateException("Cannot delete section with child sections. Please delete child sections first.");
        }
        sectionRepository.deleteById(sectionId);
    }

    void reorderSections(Long projectId, SectionReorderRequest request) {
        if (request.getStructureId() == null) {
            throw new IllegalArgumentException("Structure ID is required");
        }
        if (request.getSectionOrders() == null || request.getSectionOrders().isEmpty()) {
            throw new IllegalArgumentException("Section orders map is required");
        }

        Long projectStructureId = guard.requireStructureForProject(projectId).getId();
        if (!projectStructureId.equals(request.getStructureId())) {
            throw new IllegalArgumentException("Structure does not belong to the specified project");
        }
        List<DocumentSection> sectionsToUpdate = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : request.getSectionOrders().entrySet()) {
            DocumentSection section = guard.requireSection(entry.getKey());
            guard.requireSectionBelongsToStructure(
                    section,
                    request.getStructureId(),
                    "Section with id " + entry.getKey() + " does not belong to the specified structure"
            );
            section.setOrderIndex(entry.getValue());
            sectionsToUpdate.add(section);
        }
        sectionRepository.saveAll(sectionsToUpdate);
    }

    private DocumentSectionDTO buildSectionDTO(DocumentSection section) {
        DocumentSectionAssignment assignment = assignmentRepository.findBySectionId(section.getId()).orElse(null);
        DocumentSectionLock lock = lockRepository.findBySectionId(section.getId()).orElse(null);
        return DocumentEditorMapper.toSectionDTO(section, assignment, lock);
    }
}
