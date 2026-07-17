package com.xiyu.bid.workbench.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.project.dto.ProjectDTO;
import com.xiyu.bid.workbench.dto.ResourcePendingApprovalDTO;
import com.xiyu.bid.workbench.service.WorkbenchProjectTodoQueryService;
import com.xiyu.bid.workbench.service.WorkbenchResourcePendingQueryService;
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
 * 工作台角色化改造：待办聚合 Controller（spec.md §3 模块3+4）。
 * 路径前缀 /api/workbench，与 WorkbenchDeadlineController / WorkbenchScheduleController 对齐。
 *
 * 端点：
 * - GET /api/workbench/project-todos：按角色返回项目待办（从 ProjectController 迁入）
 * - GET /api/workbench/resource-pending-approvals：按角色返回资源待审批（从 DashboardResourceController 迁入）
 *
 * 改进点：
 * 1. P0-2.1：合并两个工作台端点到统一的 workbench 命名空间
 * 2. P0-1：方法级 @PreAuthorize 冗余（类级已声明），按 FP-Java 风格只保留类级
 * 3. URL 路径从 /api/projects/workbench-todos 改为 /api/workbench/project-todos（更符合 RESTful）
 */
@RestController
@RequestMapping("/api/workbench")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("isAuthenticated()")
public class WorkbenchTodoController {

    private final WorkbenchProjectTodoQueryService projectTodoQueryService;
    private final WorkbenchResourcePendingQueryService resourcePendingQueryService;

    /**
     * 获取当前用户角色对应的项目待办列表。
     * 后端按角色分支返回（admin_lead / bid-Team / bid-projectLeader / 其他）。
     */
    @GetMapping("/project-todos")
    public ResponseEntity<ApiResponse<List<ProjectDTO>>> getProjectTodos(
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("GET /api/workbench/project-todos - Fetching project todos by role for {}",
                userDetails != null ? userDetails.getUsername() : "anonymous");
        List<ProjectDTO> projects = projectTodoQueryService.getWorkbenchTodos(userDetails);
        return ResponseEntity.ok(ApiResponse.success("Successfully retrieved workbench project todos", projects));
    }

    /**
     * 获取当前用户待审批的资源借用申请（账户 + CA 合并）。
     * 管理员查全部，保管员查自己负责的，其他角色返回空列表。
     * 合并后按 createdAt 倒序，取前 4 条（数据库层面分页）。
     */
    @GetMapping("/resource-pending-approvals")
    public ResponseEntity<ApiResponse<List<ResourcePendingApprovalDTO>>> getResourcePendingApprovals(
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("GET /api/workbench/resource-pending-approvals - Fetching pending approvals for {}",
                userDetails != null ? userDetails.getUsername() : "anonymous");
        List<ResourcePendingApprovalDTO> list = resourcePendingQueryService.getPendingApprovals(userDetails);
        return ResponseEntity.ok(ApiResponse.success("Successfully retrieved pending approvals", list));
    }
}
