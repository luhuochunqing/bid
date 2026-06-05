package com.xiyu.bid.documenteditor.service;

import com.xiyu.bid.documenteditor.dto.DocumentReminderDTO;
import com.xiyu.bid.documenteditor.dto.DocumentSectionDTO;
import com.xiyu.bid.documenteditor.dto.SectionAssignmentRequest;
import com.xiyu.bid.documenteditor.dto.SectionLockRequest;
import com.xiyu.bid.documenteditor.dto.SectionReminderRequest;
import com.xiyu.bid.documenteditor.entity.DocumentReminder;
import com.xiyu.bid.documenteditor.entity.DocumentSection;
import com.xiyu.bid.documenteditor.entity.DocumentSectionAssignment;
import com.xiyu.bid.documenteditor.entity.DocumentSectionLock;
import com.xiyu.bid.documenteditor.repository.DocumentReminderRepository;
import com.xiyu.bid.documenteditor.repository.DocumentSectionAssignmentRepository;
import com.xiyu.bid.documenteditor.repository.DocumentSectionLockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
class DocumentSectionCollaborationService {

    private final DocumentEditorGuard guard;
    private final DocumentSectionAssignmentRepository assignmentRepository;
    private final DocumentSectionLockRepository lockRepository;
    private final DocumentReminderRepository reminderRepository;

    DocumentSectionDTO assignSection(Long projectId, SectionAssignmentRequest request) {
        DocumentSection section = guard.requireProjectSection(projectId, request.getSectionId());
        DocumentSectionAssignment assignment = assignmentRepository.findBySectionId(section.getId())
                .orElseGet(() -> DocumentSectionAssignment.builder()
                        .projectId(projectId)
                        .sectionId(section.getId())
                        .build());

        assignment.setOwner(request.getOwner().trim());
        assignment.setAssignedBy(request.getAssignedBy());
        assignment.setDueDate(request.getDueDate());

        DocumentSectionAssignment savedAssignment = assignmentRepository.save(assignment);
        DocumentSectionLock lock = lockRepository.findBySectionId(section.getId()).orElse(null);
        return DocumentEditorMapper.toSectionDTO(section, savedAssignment, lock);
    }

    DocumentSectionDTO updateLock(Long projectId, SectionLockRequest request) {
        DocumentSection section = guard.requireProjectSection(projectId, request.getSectionId());
        DocumentSectionLock lock = lockRepository.findBySectionId(section.getId())
                .orElseGet(() -> DocumentSectionLock.builder()
                        .projectId(projectId)
                        .sectionId(section.getId())
                        .build());

        lock.setLocked(Boolean.TRUE.equals(request.getLocked()));
        lock.setLockedBy(request.getUserId());
        lock.setLockedAt(Boolean.TRUE.equals(request.getLocked()) ? LocalDateTime.now() : null);

        DocumentSectionLock savedLock = lockRepository.save(lock);
        DocumentSectionAssignment assignment = assignmentRepository.findBySectionId(section.getId()).orElse(null);
        return DocumentEditorMapper.toSectionDTO(section, assignment, savedLock);
    }

    DocumentReminderDTO createReminder(Long projectId, SectionReminderRequest request) {
        DocumentSection section = guard.requireProjectSection(projectId, request.getSectionId());
        DocumentReminder reminder = DocumentReminder.builder()
                .projectId(projectId)
                .sectionId(section.getId())
                .recipient(request.getRecipient().trim())
                .message(request.getMessage())
                .remindedBy(request.getRemindedBy())
                .remindedAt(LocalDateTime.now())
                .build();
        return DocumentEditorMapper.toReminderDTO(reminderRepository.save(reminder));
    }
}
