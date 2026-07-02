// Input: HTTP 请求、路径参数、认证上下文和 DTO
// Output: 标准化 API 响应和用例入口
// Pos: Controller/接口适配层
// 维护声明: 仅维护项目转移协议适配与参数校验；业务规则下沉到 service.
package com.xiyu.bid.project.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.project.dto.ProjectTransferRequest;
import com.xiyu.bid.project.dto.ProjectTransferResponse;
import com.xiyu.bid.project.service.ProjectTransferService;
import com.xiyu.bid.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 项目转移控制器。
 * <p>处理项目负责人的转移操作。仅投标管理员（/bidAdmin）与系统管理员（admin，对应 OSS
 * bid-SystemAdmin）可操作。对应 FR-001 ~ FR-008。
 * </p>
 * <p>对齐 TenderTransferController 的协议风格；操作权限比 TenderTransfer 更严格——
 * 投标组长（bid-TeamLeader）不可操作项目转移。
 * </p>
 */
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("isAuthenticated()")
public class ProjectTransferController {

    private final ProjectTransferService projectTransferService;
    private final AuthService authService;

    /**
     * 转移项目给新负责人。仅投标管理员（/bidAdmin）与系统管理员（admin）可操作。
     * <p>FR-001 ~ FR-008：在任何项目状态下都可更改项目负责人，
     * 新负责人承接旧负责人的所有内容（权限/文档/状态不变），
     * 旧负责人立即失去所有权限（通过 ProjectAccessScopeService 实时计算）。
     * </p>
     *
     * @param projectId   项目 ID
     * @param request     转移请求（新负责人 ID + 可选原因）
     * @param userDetails 当前认证用户
     * @return 转移结果
     */
    @PostMapping("/{projectId}/transfer")
    @PreAuthorize("hasAnyRole('ADMIN', 'BIDADMIN')")
    public ResponseEntity<ApiResponse<ProjectTransferResponse>> transferProject(
            @PathVariable Long projectId,
            @Valid @RequestBody ProjectTransferRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("POST /api/projects/{}/transfer - Transferring to newOwnerUserId: {}",
                projectId, request.getNewOwnerUserId());
        Long operatorId = resolveUserId(userDetails);
        ProjectTransferResponse response = projectTransferService.transfer(
                projectId, request.getNewOwnerUserId(), operatorId, request.getReason());
        return ResponseEntity.ok(ApiResponse.success("项目转移成功", response));
    }

    private Long resolveUserId(UserDetails userDetails) {
        if (userDetails == null || userDetails.getUsername() == null || userDetails.getUsername().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "无法识别当前用户");
        }
        return authService.resolveUserIdByUsername(userDetails.getUsername().trim());
    }
}
