package com.xiyu.bid.dashboard.controller;

import com.xiyu.bid.dashboard.dto.ResourcePendingApprovalDTO;
import com.xiyu.bid.dashboard.service.DashboardResourcePendingService;
import com.xiyu.bid.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 工作台角色化改造：资源待审批聚合接口（spec.md §3 模块4）。
 * 路径前缀 /api/dashboard，与 DashboardLayoutController（/api/dashboard/layout）平级。
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("isAuthenticated()")
public class DashboardResourceController {

    private final DashboardResourcePendingService dashboardResourcePendingService;

    /**
     * 获取当前用户待审批的资源借用申请（账户 + CA 合并）。
     * 管理员查全部，保管员查自己负责的，其他角色返回空列表。
     * 合并后按 createdAt 倒序，取前 4 条。
     */
    @GetMapping("/resource-pending-approvals")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<ResourcePendingApprovalDTO>>> getResourcePendingApprovals(
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("GET /api/dashboard/resource-pending-approvals - Fetching pending approvals for {}",
                userDetails != null ? userDetails.getUsername() : "anonymous");
        List<ResourcePendingApprovalDTO> list = dashboardResourcePendingService.getPendingApprovals(userDetails);
        return ResponseEntity.ok(ApiResponse.success("Successfully retrieved pending approvals", list));
    }
}
