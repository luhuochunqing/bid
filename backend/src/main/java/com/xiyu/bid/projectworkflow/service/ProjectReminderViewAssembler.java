package com.xiyu.bid.projectworkflow.service;

import com.xiyu.bid.projectworkflow.dto.ProjectReminderDTO;
import com.xiyu.bid.projectworkflow.entity.ProjectReminder;
import org.springframework.stereotype.Component;

@Component
class ProjectReminderViewAssembler {

    ProjectReminderDTO toDto(ProjectReminder reminder) {
        return ProjectReminderDTO.builder()
                .id(reminder.getId())
                .projectId(reminder.getProjectId())
                .title(reminder.getTitle())
                .message(reminder.getMessage())
                .remindAt(reminder.getRemindAt())
                .createdBy(reminder.getCreatedBy())
                .createdByName(reminder.getCreatedByName())
                .recipient(reminder.getRecipient())
                .createdAt(reminder.getCreatedAt())
                .build();
    }
}
