// Input: HTTP 请求、认证上下文（ADMIN 角色）
// Output: /api/admin/task-extended-fields 管理端 CRUD + 重排响应
// Pos: Controller/管理端接口适配层
// 维护声明: 仅承担协议适配；业务规则与不变量下沉到 TaskExtendedFieldAdminService。
package com.xiyu.bid.task.controller;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.task.dto.TaskExtendedFieldAdminDTO;
import com.xiyu.bid.task.dto.TaskExtendedFieldReorderRequest;
import com.xiyu.bid.task.dto.TaskExtendedFieldUpsertRequest;
import com.xiyu.bid.task.service.TaskExtendedFieldAdminService;
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
 * 任务扩展字段管理端 REST 适配层。
 *
 * <p>提供管理后台 CRUD + 批量重排能力，要求 {@code ADMIN} 角色。
 * 业务不变量（key 格式、key 唯一且不可变、select 必须提供 options 等）由
 * {@link TaskExtendedFieldAdminService} 维护；本控制器仅做协议适配与审计埋点。</p>
 *
 * <p>该 controller 不承担项目级权限判定（任务扩展字段是全平台主数据），
 * 与 {@link TaskStatusDictAdminController} 同构，均以 {@code @PreAuthorize("hasRole('ADMIN')")}
 * 作为唯一访问门禁。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/task-extended-fields")
@RequiredArgsConstructor
public class TaskExtendedFieldAdminController {

    private final TaskExtendedFieldAdminService service;

    /** 列出全部扩展字段（含已停用），按 sortOrder 升序。 */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(action = "READ", entityType = "TaskExtendedField",
            description = "列出全部任务扩展字段")
    public ResponseEntity<ApiResponse<List<TaskExtendedFieldAdminDTO>>> list() {
        return ResponseEntity.ok(ApiResponse.success("ok", service.listAll()));
    }

    /** 新建扩展字段。 */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(action = "CREATE", entityType = "TaskExtendedField",
            description = "新增任务扩展字段")
    public ResponseEntity<ApiResponse<TaskExtendedFieldAdminDTO>> create(
            @Valid @RequestBody TaskExtendedFieldUpsertRequest req
    ) {
        return ResponseEntity.ok(ApiResponse.success("created", service.create(req)));
    }

    /** 更新扩展字段（PATCH 语义：null 字段保持原值；key 不可变）。 */
    @PutMapping("/{key}")
    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(action = "UPDATE", entityType = "TaskExtendedField",
            description = "更新任务扩展字段")
    public ResponseEntity<ApiResponse<TaskExtendedFieldAdminDTO>> update(
            @PathVariable String key,
            @Valid @RequestBody TaskExtendedFieldUpsertRequest req
    ) {
        return ResponseEntity.ok(ApiResponse.success("updated", service.update(key, req)));
    }

    /** 停用扩展字段。 */
    @PatchMapping("/{key}/disable")
    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(action = "DISABLE", entityType = "TaskExtendedField",
            description = "停用任务扩展字段")
    public ResponseEntity<ApiResponse<TaskExtendedFieldAdminDTO>> disable(@PathVariable String key) {
        return ResponseEntity.ok(ApiResponse.success("disabled", service.disable(key)));
    }

    /** 启用扩展字段。 */
    @PatchMapping("/{key}/enable")
    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(action = "ENABLE", entityType = "TaskExtendedField",
            description = "启用任务扩展字段")
    public ResponseEntity<ApiResponse<TaskExtendedFieldAdminDTO>> enable(@PathVariable String key) {
        return ResponseEntity.ok(ApiResponse.success("enabled", service.enable(key)));
    }

    /** 批量重排（单事务）。 */
    @PatchMapping("/reorder")
    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(action = "REORDER", entityType = "TaskExtendedField",
            description = "批量重排任务扩展字段")
    public ResponseEntity<ApiResponse<List<TaskExtendedFieldAdminDTO>>> reorder(
            @Valid @RequestBody TaskExtendedFieldReorderRequest req
    ) {
        return ResponseEntity.ok(ApiResponse.success("reordered", service.reorder(req)));
    }
}
