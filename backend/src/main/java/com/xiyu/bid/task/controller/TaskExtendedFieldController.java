// Input: HTTP 请求、认证上下文
// Output: /api/task-extended-fields 列表响应
// Pos: Controller/接口适配层
// 维护声明: 仅承担协议适配；查询逻辑下沉到 TaskExtendedFieldService。
package com.xiyu.bid.task.controller;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.task.dto.TaskExtendedFieldDTO;
import com.xiyu.bid.task.service.TaskExtendedFieldService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 任务扩展字段 REST 适配层（公开读取）。
 *
 * <p>当前只提供"列出启用字段"用例，供前端 TaskForm 动态渲染扩展字段输入控件。
 * 管理端 CRUD 写接口由后续任务引入，不在本控制器范围内。</p>
 */
@RestController
@RequestMapping("/api/task-extended-fields")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class TaskExtendedFieldController {

    private final TaskExtendedFieldService service;

    /**
     * 获取启用中的任务扩展字段 schema，按 {@code sort_order} 升序返回。
     *
     * <p>权限：类级 {@link PreAuthorize @PreAuthorize("isAuthenticated()")} 已足够。
     * 该接口语义为"公开读取全局字段 schema"（见类注释），供前端 TaskForm 动态渲染
     * 扩展字段输入控件，Service {@code listEnabled()} 是无身份维度的纯查询。</p>
     *
     * <p>历史：原方法级 {@code @PreAuthorize("hasAnyRole('ADMIN','MANAGER',...)")} 白名单
     * 不含 {@code BID_OTHERDEPT}/{@code BID_ADMINISTRATION}，导致 {@code bid-otherDept}/
     * {@code bid-administration} 角色用户 403（生产故障 traceId 50f8ae0e...，2026-07-02），
     * TaskForm 扩展字段无法渲染。按 Constitution VI「Authorization Unification」，
     * 公开读取 schema 不应用角色白名单收紧，回退到类级 {@code isAuthenticated()}。</p>
     *
     * @return 启用字段列表，封装在 {@link ApiResponse} 中
     */
    @GetMapping
    @Auditable(action = "READ", entityType = "TaskExtendedField",
        description = "列出已启用的任务扩展字段")
    public ResponseEntity<ApiResponse<List<TaskExtendedFieldDTO>>> list() {
        return ResponseEntity.ok(ApiResponse.success("ok", service.listEnabled()));
    }
}
