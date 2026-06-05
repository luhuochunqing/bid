// Input: projectworkflow service and request DTOs
// Output: Project Workflow REST API endpoints
// Pos: Controller/控制器层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.projectworkflow.controller;

import com.xiyu.bid.dto.ApiResponse;
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
import com.xiyu.bid.projectworkflow.service.ProjectTaskBreakdownService;
import com.xiyu.bid.projectworkflow.service.ProjectWorkflowService;
import com.xiyu.bid.task.dto.BidSubmissionResponse;
import com.xiyu.bid.task.dto.DeliverableCoverageDTO;
import com.xiyu.bid.task.dto.TaskDeliverableCreateRequest;
import com.xiyu.bid.task.dto.TaskDeliverableDTO;
import com.xiyu.bid.task.service.BidProcessService;
import com.xiyu.bid.task.service.TaskDeliverableService;
import com.xiyu.bid.util.InputSanitizer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}")
@RequiredArgsConstructor
public class ProjectWorkflowController {

    private static final int MAX_TASK_CONTENT_CHARS = 20_000;

    private final ProjectWorkflowService projectWorkflowService;
    private final ProjectTaskBreakdownService projectTaskBreakdownService;
    private final TaskDeliverableService taskDeliverableService;
    private final BidProcessService bidProcessService;

    @GetMapping("/tasks")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<ProjectTaskViewDTO>>> getProjectTasks(@PathVariable Long projectId) {
        return ResponseEntity.ok(ApiResponse.success(projectWorkflowService.getProjectTasks(projectId)));
    }

    @PostMapping("/tasks")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<ProjectTaskViewDTO>> createProjectTask(
            @PathVariable Long projectId,
            @Valid @RequestBody ProjectTaskCreateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        sanitizeTaskRequest(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Project task created successfully",
                        projectWorkflowService.createProjectTask(projectId, request, currentUsername(userDetails))));
    }

    @PostMapping("/tasks/decompose")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<ProjectTaskViewDTO>>> decomposeProjectTasks(@PathVariable Long projectId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Project tasks decomposed from tender breakdown successfully",
                        projectTaskBreakdownService.decomposeProjectTasks(projectId)));
    }

    @PatchMapping("/tasks/{taskId}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<ProjectTaskViewDTO>> updateProjectTaskStatus(
            @PathVariable Long projectId,
            @PathVariable Long taskId,
            @Valid @RequestBody ProjectTaskStatusUpdateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success("Project task status updated successfully",
                projectWorkflowService.updateProjectTaskStatus(projectId, taskId, request, currentUsername(userDetails))));
    }

    @GetMapping("/reminders")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<ProjectReminderDTO>>> getProjectReminders(@PathVariable Long projectId) {
        return ResponseEntity.ok(ApiResponse.success(projectWorkflowService.getProjectReminders(projectId)));
    }

    @PostMapping("/reminders")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<ProjectReminderDTO>> createProjectReminder(
            @PathVariable Long projectId,
            @Valid @RequestBody ProjectReminderCreateRequest request) {
        sanitizeReminderRequest(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Project reminder created successfully",
                        projectWorkflowService.createProjectReminder(projectId, request)));
    }

    @GetMapping("/share-links")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<ProjectShareLinkDTO>>> getProjectShareLinks(@PathVariable Long projectId) {
        return ResponseEntity.ok(ApiResponse.success(projectWorkflowService.getProjectShareLinks(projectId)));
    }

    @PostMapping("/share-links")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<ProjectShareLinkDTO>> createProjectShareLink(
            @PathVariable Long projectId,
            @Valid @RequestBody ProjectShareLinkCreateRequest request) {
        sanitizeShareLinkRequest(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Project share link created successfully",
                        projectWorkflowService.createProjectShareLink(projectId, request)));
    }

    @PostMapping("/score-drafts/parse")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<ProjectScoreDraftParseResponse>> parseProjectScoreDrafts(
            @PathVariable Long projectId,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Project score drafts parsed successfully",
                        projectWorkflowService.parseProjectScoreDrafts(projectId, file)));
    }

    @GetMapping("/score-drafts")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<ProjectScoreDraftDTO>>> getProjectScoreDrafts(@PathVariable Long projectId) {
        return ResponseEntity.ok(ApiResponse.success(projectWorkflowService.getProjectScoreDrafts(projectId)));
    }

    @PatchMapping("/score-drafts/{draftId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<ProjectScoreDraftDTO>> updateProjectScoreDraft(
            @PathVariable Long projectId,
            @PathVariable Long draftId,
            @RequestBody ProjectScoreDraftUpdateRequest request) {
        sanitizeScoreDraftRequest(request);
        return ResponseEntity.ok(ApiResponse.success("Project score draft updated successfully",
                projectWorkflowService.updateProjectScoreDraft(projectId, draftId, request)));
    }

    @PostMapping("/score-drafts/generate-tasks")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<ProjectTaskViewDTO>>> generateProjectTasksFromScoreDrafts(
            @PathVariable Long projectId,
            @Valid @RequestBody ProjectScoreDraftGenerateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Project tasks generated from score drafts successfully",
                        projectWorkflowService.generateTasksFromScoreDrafts(projectId, request)));
    }

    @DeleteMapping("/score-drafts")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<Void>> clearProjectScoreDrafts(@PathVariable Long projectId) {
        projectWorkflowService.clearNonGeneratedDrafts(projectId);
        return ResponseEntity.ok(ApiResponse.success("Project score drafts cleared successfully", null));
    }

    // --- Deliverable endpoints ---

    @GetMapping("/tasks/{taskId}/deliverables")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<TaskDeliverableDTO>>> getTaskDeliverables(
            @PathVariable Long projectId,
            @PathVariable Long taskId) {
        return ResponseEntity.ok(ApiResponse.success(
                taskDeliverableService.getDeliverablesByTaskId(projectId, taskId)));
    }

    @PostMapping("/tasks/{taskId}/deliverables")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<TaskDeliverableDTO>> createTaskDeliverable(
            @PathVariable Long projectId,
            @PathVariable Long taskId,
            @Valid @RequestBody TaskDeliverableCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("交付物已上传",
                        taskDeliverableService.createDeliverable(projectId, taskId, request, "system")));
    }

    @DeleteMapping("/tasks/{taskId}/deliverables/{deliverableId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<Void>> deleteTaskDeliverable(
            @PathVariable Long projectId,
            @PathVariable Long taskId,
            @PathVariable Long deliverableId) {
        taskDeliverableService.deleteDeliverable(projectId, taskId, deliverableId);
        return ResponseEntity.ok(ApiResponse.success("交付物已删除", null));
    }

    @GetMapping("/tasks/{taskId}/deliverables/coverage")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<DeliverableCoverageDTO>> getDeliverableCoverage(
            @PathVariable Long projectId,
            @PathVariable Long taskId) {
        return ResponseEntity.ok(ApiResponse.success(
                taskDeliverableService.getDeliverableCoverage(taskId, null)));
    }

    // --- Bid submission endpoints ---

    @PostMapping("/submit-to-bid-document")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<BidSubmissionResponse>> submitToBidDocument(
            @PathVariable Long projectId) {
        return ResponseEntity.ok(ApiResponse.success("ok",
                bidProcessService.submitToBidDocument(projectId)));
    }

    @GetMapping("/bid-process-status")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<BidProcessService.BidProcessStatusDTO>> getBidProcessStatus(
            @PathVariable Long projectId) {
        return ResponseEntity.ok(ApiResponse.success(
                bidProcessService.getBidProcessStatus(projectId)));
    }

    private void sanitizeTaskRequest(ProjectTaskCreateRequest request) {
        request.setTitle(InputSanitizer.sanitizeString(request.getTitle(), 200));
        if (request.getDescription() != null) {
            request.setDescription(InputSanitizer.sanitizeString(request.getDescription(), 2000));
        }
        if (request.getContent() != null) {
            request.setContent(InputSanitizer.sanitizeMarkdown(request.getContent(), MAX_TASK_CONTENT_CHARS));
        }
        if (request.getAssigneeName() != null) {
            request.setAssigneeName(InputSanitizer.sanitizeString(request.getAssigneeName(), 100));
        }
    }

    private String currentUsername(UserDetails userDetails) {
        return userDetails == null ? null : userDetails.getUsername();
    }

    private void sanitizeReminderRequest(ProjectReminderCreateRequest request) {
        request.setTitle(InputSanitizer.sanitizeString(request.getTitle(), 200));
        if (request.getMessage() != null) {
            request.setMessage(InputSanitizer.sanitizeString(request.getMessage(), 1000));
        }
        if (request.getCreatedByName() != null) {
            request.setCreatedByName(InputSanitizer.sanitizeString(request.getCreatedByName(), 100));
        }
        if (request.getRecipient() != null) {
            request.setRecipient(InputSanitizer.sanitizeString(request.getRecipient(), 100));
        }
    }

    private void sanitizeShareLinkRequest(ProjectShareLinkCreateRequest request) {
        request.setBaseUrl(InputSanitizer.sanitizeString(request.getBaseUrl(), 500));
        if (request.getCreatedByName() != null) {
            request.setCreatedByName(InputSanitizer.sanitizeString(request.getCreatedByName(), 100));
        }
    }

    private void sanitizeScoreDraftRequest(ProjectScoreDraftUpdateRequest request) {
        if (request.getAssigneeName() != null) {
            request.setAssigneeName(InputSanitizer.sanitizeString(request.getAssigneeName(), 100));
        }
        if (request.getGeneratedTaskTitle() != null) {
            request.setGeneratedTaskTitle(InputSanitizer.sanitizeString(request.getGeneratedTaskTitle(), 255));
        }
        if (request.getGeneratedTaskDescription() != null) {
            request.setGeneratedTaskDescription(InputSanitizer.sanitizeString(request.getGeneratedTaskDescription(), 4000));
        }
        if (request.getSkipReason() != null) {
            request.setSkipReason(InputSanitizer.sanitizeString(request.getSkipReason(), 255));
        }
    }
}
