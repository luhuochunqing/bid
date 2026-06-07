package com.xiyu.bid.workflowform.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.workflowform.application.WorkflowFormConfigException;
import com.xiyu.bid.workflowform.application.command.WorkflowFormOaBindingCommand;
import com.xiyu.bid.workflowform.application.command.WorkflowFormTemplateDraftCommand;
import com.xiyu.bid.workflowform.application.service.WorkflowFormAdminService;
import com.xiyu.bid.workflowform.domain.FormBusinessType;
import com.xiyu.bid.workflowform.dto.WorkflowFormOaBindingRequest;
import com.xiyu.bid.workflowform.dto.WorkflowFormTemplateDraftRequest;
import com.xiyu.bid.workflowform.dto.WorkflowFormTrialSubmitRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/workflow-forms")
@RequiredArgsConstructor
public class WorkflowFormAdminController {

    private final WorkflowFormAdminService adminService;

    @GetMapping("/business-types")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<?>> businessTypes() {
        return ResponseEntity.ok(ApiResponse.success(Arrays.stream(FormBusinessType.values()).map(Enum::name).toList()));
    }

    @GetMapping("/templates")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<?>> templates() {
        return ResponseEntity.ok(ApiResponse.success(adminService.listTemplates()));
    }

    @GetMapping("/templates/{templateCode}/versions")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<?>> versions(@PathVariable String templateCode) {
        return ResponseEntity.ok(ApiResponse.success(adminService.listVersions(templateCode)));
    }

    @PostMapping("/templates")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<?>> createDraft(@Valid @RequestBody WorkflowFormTemplateDraftRequest request) {
        return ResponseEntity.ok(ApiResponse.success("流程表单草稿已保存", adminService.saveDraft(toCommand(request))));
    }

    @PutMapping("/templates/{templateCode}/draft")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<?>> updateDraft(
            @PathVariable String templateCode,
            @Valid @RequestBody WorkflowFormTemplateDraftRequest request
    ) {
        WorkflowFormTemplateDraftCommand command = new WorkflowFormTemplateDraftCommand(
                templateCode, request.name(), request.businessType(), request.enabled(), request.schema());
        return ResponseEntity.ok(ApiResponse.success("流程表单草稿已保存", adminService.saveDraft(command)));
    }

    @PostMapping("/templates/{templateCode}/publish")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<?>> publish(@PathVariable String templateCode, Authentication authentication) {
        String operator = authentication == null ? "system" : authentication.getName();
        return ResponseEntity.ok(ApiResponse.success("流程表单已发布", adminService.publish(templateCode, operator)));
    }

    @PostMapping("/templates/{templateCode}/versions/{version}/rollback")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<?>> rollback(
            @PathVariable String templateCode,
            @PathVariable int version,
            Authentication authentication
    ) {
        String operator = authentication == null ? "system" : authentication.getName();
        return ResponseEntity.ok(ApiResponse.success("流程表单已回滚到历史版本", adminService.rollback(templateCode, version, operator)));
    }

    @PutMapping("/templates/{templateCode}/oa-binding")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<?>> saveBinding(
            @PathVariable String templateCode,
            @Valid @RequestBody WorkflowFormOaBindingRequest request
    ) {
        WorkflowFormOaBindingCommand command = new WorkflowFormOaBindingCommand(
                templateCode, request.provider(), request.workflowCode(), withWorkflowCode(request), request.enabled());
        return ResponseEntity.ok(ApiResponse.success("OA 绑定已保存", adminService.saveOaBinding(command)));
    }

    @PostMapping("/templates/{templateCode}/oa/test-submit")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<?>> previewTrialSubmit(
            @PathVariable String templateCode,
            @Valid @RequestBody WorkflowFormTrialSubmitRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("试提交已发送到 OA 测试通道",
                adminService.previewTrialSubmit(templateCode, request.formData(), request.applicantName())));
    }

    @ExceptionHandler(WorkflowFormConfigException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleConfigException(WorkflowFormConfigException exception) {
        return ResponseEntity.badRequest().body(ApiResponse.<Map<String, Object>>builder()
                .success(false)
                .code(400)
                .message(exception.getMessage())
                .data(Map.of("errorCode", WorkflowFormConfigException.ERROR_CODE))
                .build());
    }

    private WorkflowFormTemplateDraftCommand toCommand(WorkflowFormTemplateDraftRequest request) {
        return new WorkflowFormTemplateDraftCommand(request.templateCode(), request.name(), request.businessType(),
                request.enabled(), request.schema());
    }

    private Map<String, Object> withWorkflowCode(WorkflowFormOaBindingRequest request) {
        java.util.LinkedHashMap<String, Object> mapping = new java.util.LinkedHashMap<>(request.fieldMapping());
        mapping.put("workflowCode", request.workflowCode());
        return mapping;
    }
}
