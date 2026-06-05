package com.xiyu.bid.documenteditor.service;

import com.xiyu.bid.documenteditor.dto.DocumentSectionDTO;
import com.xiyu.bid.documenteditor.entity.DocumentSection;
import com.xiyu.bid.documenteditor.entity.DocumentSectionAssignment;
import com.xiyu.bid.documenteditor.entity.DocumentSectionLock;
import com.xiyu.bid.documenteditor.repository.DocumentSectionAssignmentRepository;
import com.xiyu.bid.documenteditor.repository.DocumentSectionLockRepository;
import com.xiyu.bid.documenteditor.repository.DocumentSectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class DocumentSectionTreeService {

    private final DocumentEditorGuard guard;
    private final DocumentSectionRepository sectionRepository;
    private final DocumentSectionAssignmentRepository assignmentRepository;
    private final DocumentSectionLockRepository lockRepository;

    List<DocumentSectionDTO> getSectionTree(Long projectId) {
        List<DocumentSection> allSections = sectionRepository.findByStructureId(
                guard.requireStructureForProject(projectId).getId()
        );
        List<Long> sectionIds = allSections.stream().map(DocumentSection::getId).toList();
        Map<Long, DocumentSectionAssignment> assignments = assignmentRepository.findBySectionIdIn(sectionIds)
                .stream()
                .collect(Collectors.toMap(DocumentSectionAssignment::getSectionId, Function.identity()));
        Map<Long, DocumentSectionLock> locks = lockRepository.findBySectionIdIn(sectionIds)
                .stream()
                .collect(Collectors.toMap(DocumentSectionLock::getSectionId, Function.identity()));
        return DocumentEditorMapper.buildTree(allSections, assignments, locks);
    }
}
