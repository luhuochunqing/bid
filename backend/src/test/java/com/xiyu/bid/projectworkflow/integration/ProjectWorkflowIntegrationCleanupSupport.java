package com.xiyu.bid.projectworkflow.integration;

import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.projectworkflow.repository.ProjectReminderRepository;
import com.xiyu.bid.projectworkflow.repository.ProjectShareLinkRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TaskRepository;
import com.xiyu.bid.repository.UserRepository;

final class ProjectWorkflowIntegrationCleanupSupport {

    private final ProjectShareLinkRepository projectShareLinkRepository;
    private final ProjectReminderRepository projectReminderRepository;
    private final ProjectDocumentRepository projectDocumentRepository;
    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    ProjectWorkflowIntegrationCleanupSupport(
            ProjectShareLinkRepository projectShareLinkRepository,
            ProjectReminderRepository projectReminderRepository,
            ProjectDocumentRepository projectDocumentRepository,
            TaskRepository taskRepository,
            ProjectRepository projectRepository,
            UserRepository userRepository
    ) {
        this.projectShareLinkRepository = projectShareLinkRepository;
        this.projectReminderRepository = projectReminderRepository;
        this.projectDocumentRepository = projectDocumentRepository;
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
    }

    void reset() {
        projectShareLinkRepository.deleteAll();
        projectReminderRepository.deleteAll();
        projectDocumentRepository.deleteAll();
        taskRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
    }
}
