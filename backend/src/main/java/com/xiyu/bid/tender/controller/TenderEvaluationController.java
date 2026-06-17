// Input: HTTP 请求、路径参数、认证上下文和 DTO
// Output: 标准化 API 响应和用例入口
// Pos: Controller/接口适配层
// 维护声明: 仅维护协议适配与参数校验；业务规则下沉到 service.
package com.xiyu.bid.tender.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.projectworkflow.dto.ProjectDocumentDTO;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.service.AuthService;
import com.xiyu.bid.tender.dto.TenderEvaluationDTO;
import com.xiyu.bid.tender.dto.TenderEvaluationSubmitRequest;
import com.xiyu.bid.tender.dto.TenderReviewRequest;
import com.xiyu.bid.tender.service.TenderEvaluationDocumentService;
import com.xiyu.bid.tender.service.TenderEvaluationReviewService;
import com.xiyu.bid.tender.service.TenderEvaluationService;
import com.xiyu.bid.task.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * 标讯评估与审核控制器
 * 处理项目经理提交评估和投标部管理员审核
 */
@RestController
@RequestMapping("/api/tenders")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("isAuthenticated()")
public class TenderEvaluationController {

    private final TenderEvaluationService tenderEvaluationService;
    private final TenderEvaluationDocumentService tenderEvaluationDocumentService;
    private final TenderEvaluationReviewService tenderEvaluationReviewService;
    private final TaskService taskService;
    private final AuthService authService;

    /**
     * 获取标讯评估详情（V119 新口径）：
     * <p>已存在 → 返回当前记录；不存在 → 返回空白 DRAFT（不持久化）。
     */
    @GetMapping("/{tenderId}/evaluation")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenderEvaluationDTO>> getEvaluation(
            @PathVariable Long tenderId,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("GET /api/tenders/{}/evaluation", tenderId);
        Long userId = currentUserId(userDetails);
        TenderEvaluationDTO evaluation = tenderEvaluationService.loadOrInitDraft(tenderId, userId);
        return ResponseEntity.ok(ApiResponse.success("ok", evaluation));
    }

    /**
     * 保存评估草稿（V119）。
     */
    @PutMapping("/{tenderId}/evaluation")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenderEvaluationDTO>> saveDraft(
            @PathVariable Long tenderId,
            @Valid @RequestBody TenderEvaluationSubmitRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("PUT /api/tenders/{}/evaluation - save draft", tenderId);
        Long userId = currentUserId(userDetails);
        TenderEvaluationDTO dto = tenderEvaluationService.saveDraft(tenderId, request, userId);
        return ResponseEntity.ok(ApiResponse.success("草稿已保存", dto));
    }

    /**
     * 提交评估表（V119）：经 Policy 校验后 DRAFT → SUBMITTED。
     */
    @PostMapping("/{tenderId}/evaluation/submit")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenderEvaluationDTO>> submitDraft(
            @PathVariable Long tenderId,
            @Valid @RequestBody TenderEvaluationSubmitRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("POST /api/tenders/{}/evaluation/submit", tenderId);
        Long userId = currentUserId(userDetails);
        TenderEvaluationDTO dto = tenderEvaluationService.submit(tenderId, request, userId);
        return ResponseEntity.ok(ApiResponse.success("评估已提交", dto));
    }

    /**
     * 决策标讯（投标 / 弃标）。
     * <p>实例级权限：调用方必须是 latest assigned-by（service 层 canDecide 守）。
     */
    @PostMapping("/{tenderId}/review")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<ApiResponse<TenderEvaluationDTO>> reviewTender(
            @PathVariable Long tenderId,
            @Valid @RequestBody TenderReviewRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("POST /api/tenders/{}/review - Reviewing tender, approved={}", tenderId, request.approved());
        Long userId = currentUserId(userDetails);
        TenderEvaluationDTO evaluation = tenderEvaluationService.reviewTender(tenderId, request, userId);
        return ResponseEntity.ok(ApiResponse.success("审核完成", evaluation));
    }

    /**
     * 投标立项：审核通过后创建项目并生成待办。
     * <p>实例级权限：调用方必须是 latest assigned-by（service 层 canDecide 守）。
     */
    @PostMapping("/{tenderId}/bid")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<TenderBidResult>> proceedToBid(
            @PathVariable Long tenderId,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("POST /api/tenders/{}/bid - Proceeding to bid", tenderId);
        Long userId = currentUserId(userDetails);
        TenderBidResult result = tenderEvaluationService.proceedToBid(tenderId, userId);
        return ResponseEntity.ok(ApiResponse.success("投标立项成功", result));
    }

    /**
     * V130: 确认审核评估表（review evaluation）。
     * <p>当评估表 requires_review=true 时，投标管理员/组长调用此接口确认审核，
     * 将 requires_review 设回 false，恢复「已评估」状态，允许执行「立即投标/放弃投标」。
     * <p>对应 FR-008 / AC5 审核确认流程。
     */
    @PostMapping("/{evaluationId}/evaluation/review")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<TenderEvaluationDTO>> reviewEvaluation(
            @PathVariable Long evaluationId,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("POST /api/tenders/{}/evaluation/review", evaluationId);
        Long userId = currentUserId(userDetails);
        TenderEvaluationDTO dto = tenderEvaluationReviewService.reviewEvaluation(evaluationId, userId);
        return ResponseEntity.ok(ApiResponse.success("审核确认完成", dto));
    }

    /**
     * V150: 上传评估表附件（如项目计划 GAP 相关文件）。
     */
    @PostMapping(value = "/{tenderId}/evaluation/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ProjectDocumentDTO>> uploadEvaluationDocument(
            @PathVariable Long tenderId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("POST /api/tenders/{}/evaluation/documents - uploading document", tenderId);
        User uploader = authService.resolveUserByUsername(currentUsername(userDetails));
        String uploaderName = uploader != null
                ? (uploader.getFullName() != null ? uploader.getFullName() : uploader.getUsername())
                : "未知用户";
        ProjectDocument doc = tenderEvaluationDocumentService.uploadDocument(tenderId, file, uploaderName);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("附件上传成功", toDto(doc)));
    }

    /**
     * V150: 获取评估表附件列表。
     */
    @GetMapping("/{tenderId}/evaluation/documents")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<ProjectDocumentDTO>>> getEvaluationDocuments(
            @PathVariable Long tenderId) {
        log.info("GET /api/tenders/{}/evaluation/documents", tenderId);
        List<ProjectDocument> docs = tenderEvaluationDocumentService.getDocuments(tenderId);
        return ResponseEntity.ok(ApiResponse.success("ok", docs.stream().map(this::toDto).toList()));
    }

    /**
     * V150: 删除评估表附件。
     */
    @DeleteMapping("/{tenderId}/evaluation/documents/{documentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteEvaluationDocument(
            @PathVariable Long tenderId,
            @PathVariable Long documentId) {
        log.info("DELETE /api/tenders/{}/evaluation/documents/{}", tenderId, documentId);
        tenderEvaluationDocumentService.deleteDocument(documentId);
        return ResponseEntity.ok(ApiResponse.success("附件已删除", null));
    }

    private String currentUsername(UserDetails userDetails) {
        if (userDetails == null || userDetails.getUsername() == null || userDetails.getUsername().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "无法识别当前用户");
        }
        return userDetails.getUsername().trim();
    }

    private ProjectDocumentDTO toDto(ProjectDocument doc) {
        return ProjectDocumentDTO.builder()
                .id(doc.getId())
                .projectId(doc.getProjectId())
                .name(doc.getName())
                .size(doc.getSize())
                .fileType(doc.getFileType())
                .documentCategory(doc.getDocumentCategory())
                .linkedEntityType(doc.getLinkedEntityType())
                .linkedEntityId(doc.getLinkedEntityId())
                .uploader(doc.getUploaderName())
                .createdAt(doc.getCreatedAt())
                .build();
    }

    private Long currentUserId(UserDetails userDetails) {
        if (userDetails == null || userDetails.getUsername() == null || userDetails.getUsername().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "无法识别当前用户");
        }
        return authService.resolveUserIdByUsername(userDetails.getUsername().trim());
    }

    /**
     * 将 {@link ResponseStatusException} 转换为标准 ApiResponse JSON 体。
     * <p>在 standalone MockMvc 中默认不会序列化 ResponseStatusException 的 reason
     * 到 body；此 handler 保证 service 层抛出的状态异常能附带可读 message。
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatus(ResponseStatusException ex) {
        log.warn("ResponseStatusException - status: {}, reason: {}", ex.getStatusCode(), ex.getReason());
        return ResponseEntity
                .status(ex.getStatusCode())
                .body(ApiResponse.error(ex.getStatusCode().value(),
                        ex.getReason() == null ? "" : ex.getReason()));
    }

    /**
     * 投标结果 DTO
     */
    public record TenderBidResult(
            Long projectId,
            String projectName,
            Long taskId,
            String taskTitle
    ) {}
}
