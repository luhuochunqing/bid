package com.xiyu.bid.workflowform.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.workflowform.application.command.WorkflowFormAttachmentUploadCommand;
import com.xiyu.bid.workflowform.application.command.WorkflowFormSubmitCommand;
import com.xiyu.bid.workflowform.application.service.WorkflowFormAccessGuard;
import com.xiyu.bid.workflowform.application.service.WorkflowFormAttachmentUploadService;
import com.xiyu.bid.workflowform.application.service.WorkflowFormSubmissionService;
import com.xiyu.bid.workflowform.application.service.WorkflowFormTemplateQueryService;
import com.xiyu.bid.workflowform.application.view.WorkflowFormAttachmentView;
import com.xiyu.bid.workflowform.application.view.WorkflowFormInstanceView;
import com.xiyu.bid.workflowform.domain.WorkflowFormStatus;
import com.xiyu.bid.workflowform.dto.WorkflowFormSubmitRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import java.util.Map;

@RestController
@RequestMapping("/api/workflow-forms")
@RequiredArgsConstructor
public class WorkflowFormController {

    private final WorkflowFormSubmissionService submissionService;
    private final WorkflowFormTemplateQueryService templateQueryService;
    private final WorkflowFormAccessGuard accessGuard;
    private final WorkflowFormAttachmentUploadService attachmentUploadService;

    @GetMapping("/templates/{templateCode}/active")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> activeTemplate(@PathVariable String templateCode) {
        return ResponseEntity.ok(ApiResponse.success(templateQueryService.getActiveSchema(templateCode)));
    }

    @PostMapping("/instances")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<WorkflowFormInstanceView>> submit(@Valid @RequestBody WorkflowFormSubmitRequest request) {
        accessGuard.assertCanAccessProject(request.projectId());
        WorkflowFormInstanceView view = submissionService.submit(new WorkflowFormSubmitCommand(
                request.templateCode(), request.businessType(), request.projectId(), request.applicantName(), request.formData()));
        return ResponseEntity.ok(ApiResponse.success(submitMessage(view), view));
    }

    @PostMapping(value = "/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<WorkflowFormAttachmentView>> uploadAttachment(
            @RequestParam("templateCode") String templateCode,
            @RequestParam("fieldKey") String fieldKey,
            @RequestParam("projectId") Long projectId,
            @RequestParam(value = "fileName", required = false) String fileName,
            @RequestParam("file") MultipartFile file) {
        accessGuard.assertCanAccessProject(projectId);
        WorkflowFormAttachmentView view = attachmentUploadService.upload(new WorkflowFormAttachmentUploadCommand(
                templateCode,
                fieldKey,
                projectId,
                effectiveFileName(fileName, file),
                file.getContentType(),
                readBytes(file)
        ));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("附件已上传", view));
    }

    private String effectiveFileName(String fileName, MultipartFile file) {
        return fileName == null || fileName.isBlank() ? file.getOriginalFilename() : fileName;
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new IllegalStateException("附件读取失败");
        }
    }

    private String submitMessage(WorkflowFormInstanceView view) {
        if (view.status() == WorkflowFormStatus.OA_FAILED) {
            return "流程表单已保存，OA 发起失败，等待重试";
        }
        return "流程表单已提交 OA 审批";
    }
}
