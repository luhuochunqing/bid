// Input: task deliverable CRUD and download operations
// Output: REST endpoints for task deliverables
// Pos: Controller - Task deliverable REST API
package com.xiyu.bid.task.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.projectworkflow.service.ProjectTaskAuthorizationGuard;
import com.xiyu.bid.task.dto.DeliverableCoverageDTO;
import com.xiyu.bid.task.dto.TaskDeliverableCreateRequest;
import com.xiyu.bid.task.dto.TaskDeliverableDTO;
import com.xiyu.bid.task.service.TaskDeliverableService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Task deliverable endpoints.
 * Handles task deliverable CRUD and download operations.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/tasks/{taskId}/deliverables")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class TaskDeliverableController {

    private final TaskDeliverableService taskDeliverableService;
    private final ProjectTaskAuthorizationGuard taskAuthzGuard;

    private String currentUsername(UserDetails userDetails) {
        return userDetails != null ? userDetails.getUsername() : null;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<TaskDeliverableDTO>>> getTaskDeliverables(
            @PathVariable Long projectId,
            @PathVariable Long taskId,
            @AuthenticationPrincipal UserDetails userDetails) {
        taskAuthzGuard.assertCanViewTask(projectId, taskId, currentUsername(userDetails));
        return ResponseEntity.ok(ApiResponse.success(
                taskDeliverableService.getDeliverablesByTaskId(projectId, taskId)));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TaskDeliverableDTO>> createTaskDeliverable(
            @PathVariable Long projectId,
            @PathVariable Long taskId,
            @Valid @RequestBody TaskDeliverableCreateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("交付物已上传",
                        taskDeliverableService.createDeliverable(projectId, taskId, request,
                                currentUsername(userDetails))));
    }

    @GetMapping("/{deliverableId}/download")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<org.springframework.core.io.Resource> downloadTaskDeliverable(
            @PathVariable Long projectId,
            @PathVariable Long taskId,
            @PathVariable Long deliverableId,
            @AuthenticationPrincipal UserDetails userDetails) {
        taskAuthzGuard.assertCanViewTask(projectId, taskId, currentUsername(userDetails));
        TaskDeliverableService.DeliverableDownloadFile file =
                taskDeliverableService.getDeliverableFile(projectId, taskId, deliverableId);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.contentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(file.resource());
    }

    @DeleteMapping("/{deliverableId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> deleteTaskDeliverable(
            @PathVariable Long projectId,
            @PathVariable Long taskId,
            @PathVariable Long deliverableId,
            @AuthenticationPrincipal UserDetails userDetails) {
        taskAuthzGuard.assertCanManageTask(projectId, currentUsername(userDetails));
        taskDeliverableService.deleteDeliverable(projectId, taskId, deliverableId);
        return ResponseEntity.ok(ApiResponse.success("交付物已删除", null));
    }

    @GetMapping("/coverage")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<DeliverableCoverageDTO>> getDeliverableCoverage(
            @PathVariable Long projectId,
            @PathVariable Long taskId,
            @AuthenticationPrincipal UserDetails userDetails) {
        taskAuthzGuard.assertCanViewTask(projectId, taskId, currentUsername(userDetails));
        return ResponseEntity.ok(ApiResponse.success(
                taskDeliverableService.getDeliverableCoverage(taskId, null)));
    }
}
