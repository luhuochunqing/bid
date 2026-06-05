// Input: project workflow application services and request DTOs
// Output: project workflow orchestration facade for controllers
// Pos: Service/业务编排层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.projectworkflow.service;

import com.xiyu.bid.projectworkflow.dto.ProjectDocumentCreateRequest;
import com.xiyu.bid.projectworkflow.dto.ProjectDocumentDTO;
import com.xiyu.bid.projectworkflow.dto.ProjectReminderCreateRequest;
import com.xiyu.bid.projectworkflow.dto.ProjectReminderDTO;
import com.xiyu.bid.projectworkflow.dto.ProjectScoreDraftDTO;
import com.xiyu.bid.projectworkflow.dto.ProjectScoreDraftGenerateRequest;
import com.xiyu.bid.projectworkflow.dto.ProjectScoreDraftParseResponse;
import com.xiyu.bid.projectworkflow.dto.ProjectScoreDraftUpdateRequest;
import com.xiyu.bid.projectworkflow.dto.ProjectShareLinkCreateRequest;
import com.xiyu.bid.projectworkflow.dto.ProjectShareLinkDTO;
import com.xiyu.bid.projectworkflow.dto.ProjectTaskCreateRequest;
import com.xiyu.bid.projectworkflow.dto.ProjectTaskStatusUpdateRequest;
import com.xiyu.bid.projectworkflow.dto.ProjectTaskViewDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ProjectWorkflowService {

    private final ProjectTaskWorkflowService projectTaskWorkflowService;
    private final ProjectDocumentFacade projectDocumentFacade;
    private final ProjectReminderWorkflowService projectReminderWorkflowService;
    private final ProjectShareLinkWorkflowService projectShareLinkWorkflowService;
    private final ProjectScoreDraftWorkflowService projectScoreDraftWorkflowService;

    @Transactional(readOnly = true)
    public List<ProjectTaskViewDTO> getProjectTasks(Long projectId) {
        return projectTaskWorkflowService.getProjectTasks(projectId);
    }

    public ProjectTaskViewDTO createProjectTask(Long projectId, ProjectTaskCreateRequest request) {
        return createProjectTask(projectId, request, null);
    }

    public ProjectTaskViewDTO createProjectTask(
            Long projectId,
            ProjectTaskCreateRequest request,
            String creatorUsername
    ) {
        return projectTaskWorkflowService.createProjectTask(projectId, request, creatorUsername);
    }

    public ProjectTaskViewDTO updateProjectTaskStatus(
            Long projectId,
            Long taskId,
            ProjectTaskStatusUpdateRequest request
    ) {
        return updateProjectTaskStatus(projectId, taskId, request, null);
    }

    public ProjectTaskViewDTO updateProjectTaskStatus(
            Long projectId,
            Long taskId,
            ProjectTaskStatusUpdateRequest request,
            String actorUsername
    ) {
        return projectTaskWorkflowService.updateProjectTaskStatus(projectId, taskId, request, actorUsername);
    }

    @Transactional(readOnly = true)
    public List<ProjectDocumentDTO> getProjectDocuments(Long projectId) {
        return projectDocumentFacade.getProjectDocuments(projectId);
    }

    @Transactional(readOnly = true)
    public List<ProjectDocumentDTO> getProjectDocuments(
            Long projectId,
            String documentCategory,
            String linkedEntityType,
            Long linkedEntityId
    ) {
        return projectDocumentFacade.getProjectDocuments(
                projectId,
                documentCategory,
                linkedEntityType,
                linkedEntityId
        );
    }

    public ProjectDocumentDTO createProjectDocument(
            Long projectId,
            ProjectDocumentCreateRequest request
    ) {
        return projectDocumentFacade.createProjectDocument(projectId, request);
    }

    public ProjectDocumentDTO createUploadedProjectDocument(
            Long projectId,
            ProjectDocumentCreateRequest request,
            MultipartFile file
    ) {
        return projectDocumentFacade.createUploadedProjectDocument(projectId, request, file);
    }

    public void deleteProjectDocument(Long projectId, Long documentId) {
        projectDocumentFacade.deleteProjectDocument(projectId, documentId);
    }

    @Transactional(readOnly = true)
    public List<ProjectReminderDTO> getProjectReminders(Long projectId) {
        return projectReminderWorkflowService.getProjectReminders(projectId);
    }

    public ProjectReminderDTO createProjectReminder(
            Long projectId,
            ProjectReminderCreateRequest request
    ) {
        return projectReminderWorkflowService.createProjectReminder(projectId, request);
    }

    @Transactional(readOnly = true)
    public List<ProjectShareLinkDTO> getProjectShareLinks(Long projectId) {
        return projectShareLinkWorkflowService.getProjectShareLinks(projectId);
    }

    public ProjectShareLinkDTO createProjectShareLink(
            Long projectId,
            ProjectShareLinkCreateRequest request
    ) {
        return projectShareLinkWorkflowService.createProjectShareLink(projectId, request);
    }

    @Transactional(readOnly = true)
    public List<ProjectScoreDraftDTO> getProjectScoreDrafts(Long projectId) {
        return projectScoreDraftWorkflowService.getProjectScoreDrafts(projectId);
    }

    public ProjectScoreDraftParseResponse parseProjectScoreDrafts(
            Long projectId,
            MultipartFile file
    ) {
        return projectScoreDraftWorkflowService.parseProjectScoreDrafts(projectId, file);
    }

    public ProjectScoreDraftDTO updateProjectScoreDraft(
            Long projectId,
            Long draftId,
            ProjectScoreDraftUpdateRequest request
    ) {
        return projectScoreDraftWorkflowService.updateProjectScoreDraft(projectId, draftId, request);
    }

    public List<ProjectTaskViewDTO> generateTasksFromScoreDrafts(
            Long projectId,
            ProjectScoreDraftGenerateRequest request
    ) {
        return projectScoreDraftWorkflowService.generateTasksFromScoreDrafts(projectId, request);
    }

    public void clearNonGeneratedDrafts(Long projectId) {
        projectScoreDraftWorkflowService.clearNonGeneratedDrafts(projectId);
    }
}
