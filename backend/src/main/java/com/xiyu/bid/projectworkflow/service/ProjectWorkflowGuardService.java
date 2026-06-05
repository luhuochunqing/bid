package com.xiyu.bid.projectworkflow.service;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Task;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.projectworkflow.entity.ProjectScoreDraft;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.projectworkflow.repository.ProjectScoreDraftRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TaskRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class ProjectWorkflowGuardService {

    private final ProjectRepository projectRepository;
    private final ProjectAccessScopeService projectAccessScopeService;
    private final TaskRepository taskRepository;
    private final ProjectDocumentRepository projectDocumentRepository;
    private final ProjectScoreDraftRepository projectScoreDraftRepository;

    Project requireProject(Long projectId) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", String.valueOf(projectId)));
    }

    Project requireWorkflowMutationProject(Long projectId) {
        Project project = requireProject(projectId);
        if (project.getStatus().isTerminal()) {
            throw new IllegalStateException("Project is not in a valid state for workflow operations: " + project.getStatus());
        }
        return project;
    }

    Task requireTask(Long projectId, Long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", String.valueOf(taskId)));
        if (!projectId.equals(task.getProjectId())) {
            throw new IllegalArgumentException("Task does not belong to the specified project");
        }
        return task;
    }

    ProjectDocument requireDocument(Long projectId, Long documentId) {
        ProjectDocument document = projectDocumentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("ProjectDocument", String.valueOf(documentId)));
        if (!projectId.equals(document.getProjectId())) {
            throw new IllegalArgumentException("Document does not belong to the specified project");
        }
        return document;
    }

    ProjectScoreDraft requireDraft(Long projectId, Long draftId) {
        ProjectScoreDraft draft = projectScoreDraftRepository.findById(draftId)
                .orElseThrow(() -> new ResourceNotFoundException("ProjectScoreDraft", String.valueOf(draftId)));
        if (!projectId.equals(draft.getProjectId())) {
            throw new IllegalArgumentException("Score draft does not belong to the specified project");
        }
        return draft;
    }
}
