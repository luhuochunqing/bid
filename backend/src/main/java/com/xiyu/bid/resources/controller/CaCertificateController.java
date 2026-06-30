package com.xiyu.bid.resources.controller;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.resources.dto.*;
import com.xiyu.bid.resources.service.CaBorrowService;
import com.xiyu.bid.resources.service.CaCertificateImportAppService;
import com.xiyu.bid.resources.service.CaCertificateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api/ca-certificates")
@RequiredArgsConstructor
// CO-409: 类级放宽为 hasAuthority('resource') 兜底，让 bid-Team（持有 resource 权限点）能访问读操作和借用流程。
// 写操作方法级显式声明 ADMIN/MANAGER/ROLE_BID_TEAM；下架在 Service 层按 custodianId 二次校验。
@PreAuthorize("hasAuthority('resource')")
public class CaCertificateController {

    private final CaCertificateService caService;
    private final CaBorrowService caBorrowService;
    private final CaCertificateImportAppService importAppService;

    // ========== CA 证书 CRUD ==========

    @GetMapping
    @PreAuthorize("hasAuthority('resource')")
    public ResponseEntity<Page<CaCertificateDTO>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String borrowStatus,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String caType,
            @RequestParam(required = false) String sealType,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(caService.list(status, borrowStatus, keyword, caType, sealType, pageable));
    }

    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('resource')")
    public ResponseEntity<Map<String, Long>> overview() {
        return ResponseEntity.ok(caService.getOverview());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('resource')")
    public ResponseEntity<CaCertificateDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(caService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('ROLE_BID_TEAM')")
    @Auditable(action = "CREATE", entityType = "CaCertificate", description = "新增CA证书")
    public ResponseEntity<CaCertificateDTO> create(@Valid @RequestBody CaCertificateRequest request) {
        return ResponseEntity.ok(caService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('ROLE_BID_TEAM')")
    @Auditable(action = "UPDATE", entityType = "CaCertificate", description = "编辑CA证书")
    public ResponseEntity<CaCertificateDTO> update(@PathVariable Long id, @Valid @RequestBody CaCertificateRequest request) {
        return ResponseEntity.ok(caService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('ROLE_BID_TEAM')")
    @Auditable(action = "DEACTIVATE", entityType = "CaCertificate", description = "下架CA证书")
    public ResponseEntity<Void> deactivate(@PathVariable Long id,
                                           @AuthenticationPrincipal UserDetails currentUser) {
        caService.deactivate(id, currentUser);
        return ResponseEntity.ok().build();
    }

    // ========== CA 借用流程 ==========

    @PostMapping("/{id}/borrow")
    @Auditable(action = "BORROW_REQUEST", entityType = "CaBorrowApplication", description = "发起CA借用申请")
    public ResponseEntity<CaBorrowApplicationDTO> borrow(
            @PathVariable Long id,
            @Valid @RequestBody CaBorrowRequest request,
            @AuthenticationPrincipal UserDetails user) {
        request.setCaCertificateId(id);
        return ResponseEntity.ok(caBorrowService.borrow(user, request));
    }

    @PostMapping("/borrow-applications/{applicationId}/approve")
    @Auditable(action = "APPROVE", entityType = "CaBorrowApplication", description = "审批通过CA借用申请")
    public ResponseEntity<CaBorrowApplicationDTO> approve(
            @PathVariable Long applicationId,
            @Valid @RequestBody CaApprovalRequest request,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(caBorrowService.approve(applicationId, user, request.getComment()));
    }

    @PostMapping("/borrow-applications/{applicationId}/reject")
    @Auditable(action = "REJECT", entityType = "CaBorrowApplication", description = "驳回CA借用申请")
    public ResponseEntity<CaBorrowApplicationDTO> reject(
            @PathVariable Long applicationId,
            @Valid @RequestBody CaApprovalRequest request,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(caBorrowService.reject(applicationId, user, request.getComment()));
    }

    @PostMapping("/borrow-applications/{applicationId}/return")
    @Auditable(action = "RETURN", entityType = "CaBorrowApplication", description = "登记CA归还")
    public ResponseEntity<CaBorrowApplicationDTO> returnCertificate(
            @PathVariable Long applicationId,
            @Valid @RequestBody CaReturnRequest request,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(caBorrowService.returnCertificate(applicationId, user, request));
    }

    @PostMapping("/borrow-applications/{applicationId}/cancel")
    @Auditable(action = "CANCEL", entityType = "CaBorrowApplication", description = "取消CA借用申请")
    public ResponseEntity<CaBorrowApplicationDTO> cancelBorrow(
            @PathVariable Long applicationId,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(caBorrowService.cancelBorrow(applicationId, user));
    }

    // ========== 查询借用记录 ==========

    @GetMapping("/{id}/borrow-applications")
    public ResponseEntity<List<CaBorrowApplicationDTO>> getBorrowApplications(@PathVariable Long id) {
        return ResponseEntity.ok(caBorrowService.getBorrowApplicationsByCaId(id));
    }

    @GetMapping("/borrow-applications/{applicationId}/events")
    public ResponseEntity<List<CaBorrowEventDTO>> getBorrowEvents(@PathVariable Long applicationId) {
        return ResponseEntity.ok(caBorrowService.getBorrowEvents(applicationId));
    }

    @GetMapping("/pending-approvals")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<CaBorrowApplicationDTO>> getPendingApprovals(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(caBorrowService.getPendingApprovals(user));
    }

    // ── 批量导入 ────────────────────────────────────────────────────────────────

    /** 下载批量导入模板 */
    @GetMapping("/template")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('ROLE_BID_TEAM')")
    public ResponseEntity<byte[]> downloadTemplate() throws IOException {
        byte[] template = importAppService.generateTemplate();
        String filename = URLEncoder.encode("CA证书导入模板.xlsx", StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .body(template);
    }

    /** 触发批量导入，返回 taskId */
    @PostMapping("/import")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('ROLE_BID_TEAM')")
    public ResponseEntity<ApiResponse<?>> importCertificates(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails currentUser) throws IOException {
        Long taskId = importAppService.triggerImport(
                file.getBytes(), file.getOriginalFilename(), currentUser.getUsername());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("导入任务已创建", java.util.Map.of("taskId", taskId)));
    }

    /** 查询导入任务状态 */
    @GetMapping("/import/tasks/{taskId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('ROLE_BID_TEAM')")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> getImportTask(
            @PathVariable Long taskId) {
        java.util.Map<String, Object> task = importAppService.getTaskAsMap(taskId);
        if (task == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(ApiResponse.success(task));
    }

    /** 查询导入任务历史 */
    @GetMapping("/import/tasks")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('ROLE_BID_TEAM')")
    public ResponseEntity<ApiResponse<java.util.List<java.util.Map<String, Object>>>> listImportTasks(
            @AuthenticationPrincipal UserDetails currentUser) {
        java.util.List<java.util.Map<String, Object>> tasks =
                importAppService.listTasksAsMap(currentUser.getUsername());
        return ResponseEntity.ok(ApiResponse.success(tasks));
    }
}
