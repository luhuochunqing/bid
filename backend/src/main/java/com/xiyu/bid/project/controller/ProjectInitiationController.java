// Input: HTTP 请求 (submit/update/get) + 当前用户
// Output: ApiResponse<InitiationViewDto>
// Pos: project/controller/
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.project.dto.InitiationApprovalRequest;
import com.xiyu.bid.project.dto.InitiationDto;
import com.xiyu.bid.project.dto.InitiationRejectionRequest;
import com.xiyu.bid.project.dto.InitiationViewDto;
import com.xiyu.bid.project.service.ProjectCurrentUserLookupService;
import com.xiyu.bid.project.service.ProjectInitiationApprovalService;
import com.xiyu.bid.project.service.ProjectInitiationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/projects/{projectId}/initiation")
@RequiredArgsConstructor
@Slf4j
public class ProjectInitiationController {

    private final ProjectInitiationService service;
    private final ProjectInitiationApprovalService approvalService;
    private final ProjectCurrentUserLookupService currentUserLookupService;

    /** 提交立项：SALES/BID_LEAD（映射到 MANAGER/STAFF/ADMIN）。 */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','STAFF')")
    public ResponseEntity<ApiResponse<InitiationViewDto>> submit(
            @PathVariable Long projectId,
            @Valid @RequestBody InitiationDto req,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = currentUserId(userDetails);
        InitiationViewDto dto = service.submit(projectId, req, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Initiation submitted", dto));
    }

    /** 更新立项：触碰 lockedFields 返回 423。 */
    @PatchMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','STAFF')")
    public ResponseEntity<ApiResponse<InitiationViewDto>> update(
            @PathVariable Long projectId,
            @Valid @RequestBody InitiationDto req,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = currentUserId(userDetails);
        InitiationViewDto dto = service.update(projectId, req, userId);
        return ResponseEntity.ok(ApiResponse.success("Initiation updated", dto));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','STAFF')")
    public ResponseEntity<ApiResponse<InitiationViewDto>> get(@PathVariable Long projectId) {
        InitiationViewDto dto = service.getByProject(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "立项未提交"));
        return ResponseEntity.ok(ApiResponse.success("ok", dto));
    }

    /** 审核通过：ADMIN/MANAGER 限定。分配团队后推进 INITIATED→DRAFTING。 */
    @PostMapping("/approve")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<Void>> approve(
            @PathVariable Long projectId,
            @Valid @RequestBody InitiationApprovalRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = currentUserId(userDetails);
        approvalService.approve(projectId, req, userId);
        return ResponseEntity.ok(ApiResponse.success("Initiation approved", null));
    }

    /** 审核驳回：ADMIN/MANAGER 限定。必须填写驳回原因。 */
    @PostMapping("/reject")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<Void>> reject(
            @PathVariable Long projectId,
            @Valid @RequestBody InitiationRejectionRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = currentUserId(userDetails);
        approvalService.reject(projectId, req, userId);
        return ResponseEntity.ok(ApiResponse.success("Initiation rejected", null));
    }

    private Long currentUserId(UserDetails userDetails) {
        if (userDetails == null || userDetails.getUsername() == null || userDetails.getUsername().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "无法识别当前用户");
        }
        return currentUserLookupService.requireUserId(userDetails);
    }
}
