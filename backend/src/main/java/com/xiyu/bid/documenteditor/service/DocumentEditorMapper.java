package com.xiyu.bid.documenteditor.service;

import com.xiyu.bid.documenteditor.dto.DocumentReminderDTO;
import com.xiyu.bid.documenteditor.dto.DocumentSectionDTO;
import com.xiyu.bid.documenteditor.dto.DocumentStructureDTO;
import com.xiyu.bid.documenteditor.entity.DocumentReminder;
import com.xiyu.bid.documenteditor.entity.DocumentSection;
import com.xiyu.bid.documenteditor.entity.DocumentSectionAssignment;
import com.xiyu.bid.documenteditor.entity.DocumentSectionLock;
import com.xiyu.bid.documenteditor.entity.DocumentStructure;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

final class DocumentEditorMapper {

    private DocumentEditorMapper() {
    }

    static DocumentStructureDTO toStructureDTO(DocumentStructure entity) {
        return DocumentStructureDTO.builder()
                .id(entity.getId())
                .projectId(entity.getProjectId())
                .name(entity.getName())
                .rootSectionId(entity.getRootSectionId())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    static DocumentSectionDTO toSectionDTO(
            DocumentSection entity,
            DocumentSectionAssignment assignment,
            DocumentSectionLock lock
    ) {
        return DocumentSectionDTO.builder()
                .id(entity.getId())
                .structureId(entity.getStructureId())
                .parentId(entity.getParentId())
                .sectionType(entity.getSectionType())
                .title(entity.getTitle())
                .content(entity.getContent())
                .orderIndex(entity.getOrderIndex())
                .metadata(entity.getMetadata())
                .owner(assignment != null ? assignment.getOwner() : null)
                .dueDate(assignment != null ? assignment.getDueDate() : null)
                .locked(lock != null && Boolean.TRUE.equals(lock.getLocked()))
                .assignedBy(assignment != null ? assignment.getAssignedBy() : null)
                .lockedBy(lock != null ? lock.getLockedBy() : null)
                .lockedAt(lock != null ? lock.getLockedAt() : null)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .children(List.of())
                .build();
    }

    static DocumentReminderDTO toReminderDTO(DocumentReminder entity) {
        return DocumentReminderDTO.builder()
                .id(entity.getId())
                .projectId(entity.getProjectId())
                .sectionId(entity.getSectionId())
                .recipient(entity.getRecipient())
                .message(entity.getMessage())
                .remindedBy(entity.getRemindedBy())
                .remindedAt(entity.getRemindedAt())
                .build();
    }

    static List<DocumentSectionDTO> buildTree(
            List<DocumentSection> allSections,
            Map<Long, DocumentSectionAssignment> assignments,
            Map<Long, DocumentSectionLock> locks
    ) {
        if (allSections == null) {
            return List.of();
        }
        return buildTree(allSections, null, assignments, locks);
    }

    private static List<DocumentSectionDTO> buildTree(
            List<DocumentSection> allSections,
            Long parentId,
            Map<Long, DocumentSectionAssignment> assignments,
            Map<Long, DocumentSectionLock> locks
    ) {
        return allSections.stream()
                .filter(section -> section != null && Objects.equals(section.getParentId(), parentId))
                .map(section -> {
                    DocumentSectionDTO dto = toSectionDTO(section, assignments.get(section.getId()), locks.get(section.getId()));
                    if (section.getId() != null) {
                        List<DocumentSectionDTO> children = buildTree(allSections, section.getId(), assignments, locks);
                        if (!children.isEmpty()) {
                            dto.setChildren(children);
                        }
                    }
                    return dto;
                })
                .sorted(Comparator.comparing(DocumentSectionDTO::getOrderIndex, Comparator.nullsLast(Integer::compareTo)))
                .collect(Collectors.toList());
    }
}
