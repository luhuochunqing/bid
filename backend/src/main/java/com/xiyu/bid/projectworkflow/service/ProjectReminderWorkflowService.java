package com.xiyu.bid.projectworkflow.service;

import com.xiyu.bid.projectworkflow.dto.ProjectReminderCreateRequest;
import com.xiyu.bid.projectworkflow.dto.ProjectReminderDTO;
import com.xiyu.bid.projectworkflow.entity.ProjectReminder;
import com.xiyu.bid.projectworkflow.repository.ProjectReminderRepository;
import com.xiyu.bid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
class ProjectReminderWorkflowService {

    private final ProjectWorkflowGuardService guardService;
    private final ProjectReminderRepository projectReminderRepository;
    private final UserRepository userRepository;
    private final ProjectReminderViewAssembler projectReminderViewAssembler;

    List<ProjectReminderDTO> getProjectReminders(Long projectId) {
        guardService.requireProject(projectId);
        return projectReminderRepository.findByProjectIdOrderByRemindAtDesc(projectId).stream()
                .map(projectReminderViewAssembler::toDto)
                .toList();
    }

    ProjectReminderDTO createProjectReminder(Long projectId, ProjectReminderCreateRequest request) {
        guardService.requireWorkflowMutationProject(projectId);
        ProjectReminder reminder = ProjectReminder.builder()
                .projectId(projectId)
                .title(request.getTitle().trim())
                .message(trimToNull(request.getMessage()))
                .remindAt(request.getRemindAt())
                .createdBy(request.getCreatedBy())
                .createdByName(resolveDisplayName(request.getCreatedBy(), request.getCreatedByName()))
                .recipient(defaultString(request.getRecipient(), "项目负责人"))
                .build();
        return projectReminderViewAssembler.toDto(projectReminderRepository.save(reminder));
    }

    private String resolveDisplayName(Long userId, String fallback) {
        if (userId != null) {
            var user = userRepository.findById(userId).orElse(null);
            if (user != null && user.getFullName() != null && !user.getFullName().isBlank()) {
                return user.getFullName();
            }
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback.trim();
        }
        return "未分配";
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String defaultString(String value, String fallback) {
        String normalized = trimToNull(value);
        return normalized != null ? normalized : fallback;
    }
}
