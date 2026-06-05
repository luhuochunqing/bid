// Input: HTTP 请求、认证上下文（ADMIN 角色）
// Output: /api/admin/task-status-dict 管理端 CRUD + 重排响应
// Pos: Controller/管理端接口适配层
// 维护声明: 仅承担协议适配；业务规则与不变量下沉到 TaskStatusDictAdminService。
package com.xiyu.bid.task.controller;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.task.dto.TaskStatusDictAdminDTO;
import com.xiyu.bid.task.dto.TaskStatusDictReorderRequest;
import com.xiyu.bid.task.dto.TaskStatusDictUpsertRequest;
import com.xiyu.bid.task.service.TaskStatusDictAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 任务状态字典管理端 REST 适配层。
 *
 * <p>提供管理后台 CRUD + 批量重排能力，要求 {@code ADMIN} 角色。
 * 业务不变量（code 唯一、is_initial 全表至多一条、终态启用守恒等）由
 * {@link TaskStatusDictAdminService} 维护；本控制器仅做协议适配与审计埋点。</p>
 *
 * <p>该 controller 不承担项目级权限判定（任务状态字典是全平台主数据），
 * 因此在 {@code project-access-guard-baseline.txt} 中显式声明豁免。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/task-status-dict")
@RequiredArgsConstructor
public class TaskStatusDictAdminController {

    private final TaskStatusDictAdminService service;

    /** 列出全部字典项（含已停用），按 sortOrder 升序。 */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(action = "READ", entityType = "TaskStatusDict",
            description = "列出全部任务状态字典")
    public ResponseEntity<ApiResponse<List<TaskStatusDictAdminDTO>>> list() {
        return ResponseEntity.ok(ApiResponse.success("ok", service.listAll()));
    }

    /** 新建字典项。 */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(action = "CREATE", entityType = "TaskStatusDict",
            description = "新增任务状态字典")
    public ResponseEntity<ApiResponse<TaskStatusDictAdminDTO>> create(
            @Valid @RequestBody TaskStatusDictUpsertRequest req
    ) {
        return ResponseEntity.ok(ApiResponse.success("created", service.create(req)));
    }

    /** 更新字典项（PATCH 语义：null 字段保持原值）。 */
    @PutMapping("/{code}")
    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(action = "UPDATE", entityType = "TaskStatusDict",
            description = "更新任务状态字典")
    public ResponseEntity<ApiResponse<TaskStatusDictAdminDTO>> update(
            @PathVariable String code,
            @Valid @RequestBody TaskStatusDictUpsertRequest req
    ) {
        return ResponseEntity.ok(ApiResponse.success("updated", service.update(code, req)));
    }

    /** 停用字典项（service 层保证不能停用初始/唯一终态）。 */
    @PatchMapping("/{code}/disable")
    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(action = "DISABLE", entityType = "TaskStatusDict",
            description = "停用任务状态字典")
    public ResponseEntity<ApiResponse<TaskStatusDictAdminDTO>> disable(@PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.success("disabled", service.disable(code)));
    }

    /** 启用字典项。 */
    @PatchMapping("/{code}/enable")
    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(action = "ENABLE", entityType = "TaskStatusDict",
            description = "启用任务状态字典")
    public ResponseEntity<ApiResponse<TaskStatusDictAdminDTO>> enable(@PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.success("enabled", service.enable(code)));
    }

    /** 批量重排（单事务）。 */
    @PatchMapping("/reorder")
    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(action = "REORDER", entityType = "TaskStatusDict",
            description = "批量重排任务状态字典")
    public ResponseEntity<ApiResponse<Void>> reorder(@Valid @RequestBody TaskStatusDictReorderRequest req) {
        service.reorder(req);
        return ResponseEntity.ok(ApiResponse.success("reordered", null));
    }
}
