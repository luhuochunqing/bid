// Input: HTTP 请求、认证上下文
// Output: /api/task-status-dict 列表响应
// Pos: Controller/接口适配层
// 维护声明: 仅承担协议适配；查询逻辑下沉到 TaskStatusDictService。
package com.xiyu.bid.task.controller;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.task.dto.TaskStatusDictDTO;
import com.xiyu.bid.task.service.TaskStatusDictService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 任务状态字典 REST 适配层。
 *
 * <p>当前只提供"读取启用字典"用例，供前端看板列动态化使用。
 * 写接口（管理端 CRUD）由后续任务引入，不在本控制器范围内。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/task-status-dict")
@RequiredArgsConstructor
public class TaskStatusDictController {

    private final TaskStatusDictService taskStatusDictService;

    /**
     * 获取启用中的任务状态字典，按 {@code sort_order} 升序返回。
     *
     * @return 启用字典项列表，封装在 {@link ApiResponse} 中
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Auditable(action = "READ", entityType = "TaskStatusDict", description = "获取任务状态字典")
    public ResponseEntity<ApiResponse<List<TaskStatusDictDTO>>> listEnabled() {
        List<TaskStatusDictDTO> statuses = taskStatusDictService.listEnabled();
        return ResponseEntity.ok(ApiResponse.success("Task status dictionary retrieved successfully", statuses));
    }
}
