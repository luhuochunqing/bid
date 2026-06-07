// Input: HTTP 请求、路径参数、认证上下文和 DTO
// Output: 标准化 API 响应和用例入口
// Pos: Controller/接口适配层
// 维护声明: 仅维护协议适配与参数校验；业务规则下沉到 service.
package com.xiyu.bid.task.controller;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.task.dto.TaskActivityDTO;
import com.xiyu.bid.task.dto.TaskAssignmentCandidateDTO;
import com.xiyu.bid.task.dto.TaskAssignmentRequest;
import com.xiyu.bid.task.dto.TaskCommentCreateRequest;
import com.xiyu.bid.task.dto.TaskDTO;
import com.xiyu.bid.task.dto.TeamTaskWorkloadDTO;
import com.xiyu.bid.task.service.TaskActivityService;
import com.xiyu.bid.task.service.TaskService;
import com.xiyu.bid.util.InputSanitizer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final TaskActivityService taskActivityService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Auditable(action = "CREATE", entityType = "Task", description = "创建新任务")
    public ResponseEntity<ApiResponse<TaskDTO>> createTask(@Valid @RequestBody TaskDTO taskDTO) {
        sanitizeTaskDTO(taskDTO);
        TaskDTO createdTask = taskService.createTask(taskDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Task created successfully", createdTask));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Auditable(action = "READ", entityType = "Task", description = "获取所有任务")
    public ResponseEntity<ApiResponse<List<TaskDTO>>> getAllTasks() {
        List<TaskDTO> tasks = taskService.getAllTasks();
        return ResponseEntity.ok(ApiResponse.success("Tasks retrieved successfully", tasks));
    }

    @GetMapping("/{id:\\d+}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Auditable(action = "READ", entityType = "Task", description = "根据ID获取任务")
    public ResponseEntity<ApiResponse<TaskDTO>> getTaskById(@PathVariable Long id) {
        TaskDTO task = taskService.getTaskById(id);
        return ResponseEntity.ok(ApiResponse.success("Task retrieved successfully", task));
    }

    @PutMapping("/{id:\\d+}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Auditable(action = "UPDATE", entityType = "Task", description = "更新任务")
    public ResponseEntity<ApiResponse<TaskDTO>> updateTask(
            @PathVariable Long id,
            @Valid @RequestBody TaskDTO taskDTO,
            @AuthenticationPrincipal UserDetails userDetails) {
        sanitizeTaskDTO(taskDTO);
        TaskDTO updatedTask = taskService.updateTask(id, taskDTO, currentUsername(userDetails));
        return ResponseEntity.ok(ApiResponse.success("Task updated successfully", updatedTask));
    }

    @DeleteMapping("/{id:\\d+}")
    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(action = "DELETE", entityType = "Task", description = "删除任务")
    public ResponseEntity<Void> deleteTask(@PathVariable Long id) {
        taskService.deleteTask(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/project/{projectId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Auditable(action = "READ", entityType = "Task", description = "根据项目ID获取任务")
    public ResponseEntity<ApiResponse<List<TaskDTO>>> getTasksByProjectId(@PathVariable Long projectId) {
        List<TaskDTO> tasks = taskService.getTasksByProjectId(projectId);
        return ResponseEntity.ok(ApiResponse.success("Tasks retrieved successfully", tasks));
    }

    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Auditable(action = "READ", entityType = "Task", description = "获取我的任务")
    public ResponseEntity<ApiResponse<List<TaskDTO>>> getMyTasks(
            @RequestParam(required = false) Long assigneeId,
            @AuthenticationPrincipal UserDetails userDetails) {
        List<TaskDTO> tasks = taskService.getAccessibleTasksByAssigneeId(assigneeId, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Tasks retrieved successfully", tasks));
    }

    @PatchMapping("/{id:\\d+}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Auditable(action = "UPDATE", entityType = "Task", description = "更新任务状态")
    public ResponseEntity<ApiResponse<TaskDTO>> updateTaskStatus(
            @PathVariable Long id,
            @RequestBody com.xiyu.bid.entity.Task.Status status,
            @AuthenticationPrincipal UserDetails userDetails) {
        TaskDTO updatedTask = taskService.updateTaskStatus(id, status, currentUsername(userDetails));
        return ResponseEntity.ok(ApiResponse.success("Task status updated successfully", updatedTask));
    }

    @GetMapping("/{id:\\d+}/activity")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Auditable(action = "READ", entityType = "Task", description = "获取任务动态")
    public ResponseEntity<ApiResponse<List<TaskActivityDTO>>> getTaskActivity(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Task activity retrieved successfully",
                taskActivityService.getActivity(id)));
    }

    @PostMapping("/{id:\\d+}/comments")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Auditable(action = "CREATE", entityType = "TaskComment", description = "创建任务评论")
    public ResponseEntity<ApiResponse<TaskActivityDTO>> createTaskComment(
            @PathVariable Long id,
            @Valid @RequestBody TaskCommentCreateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        TaskCommentCreateRequest sanitized = new TaskCommentCreateRequest(
                InputSanitizer.sanitizeMarkdown(request.content(), MAX_COMMENT_CHARS)
        );
        TaskActivityDTO created = taskActivityService.createComment(id, sanitized, currentUsername(userDetails));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Task comment created successfully", created));
    }

    @PatchMapping("/{id:\\d+}/assign")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Auditable(action = "UPDATE", entityType = "Task", description = "分配任务")
    public ResponseEntity<ApiResponse<TaskDTO>> assignTask(
            @PathVariable Long id,
            @Valid @RequestBody TaskAssignmentRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        TaskDTO updatedTask = taskService.assignTask(id, request, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Task assigned successfully", updatedTask));
    }

    @GetMapping("/assignment-candidates")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Auditable(action = "READ", entityType = "Task", description = "获取任务分配候选人")
    public ResponseEntity<ApiResponse<List<TaskAssignmentCandidateDTO>>> getAssignmentCandidates(
            @RequestParam(required = false) String deptCode,
            @RequestParam(required = false) String roleCode,
            @AuthenticationPrincipal UserDetails userDetails) {
        List<TaskAssignmentCandidateDTO> candidates = taskService.getAssignmentCandidates(deptCode, roleCode, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Task assignment candidates retrieved successfully", candidates));
    }

    @GetMapping("/team-workload")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Auditable(action = "READ", entityType = "Task", description = "获取团队任务负载")
    public ResponseEntity<ApiResponse<TeamTaskWorkloadDTO>> getTeamTaskWorkload(
            @AuthenticationPrincipal UserDetails userDetails) {
        TeamTaskWorkloadDTO workload = taskService.getTeamTaskWorkload(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Team task workload retrieved successfully", workload));
    }

    @GetMapping("/upcoming")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Auditable(action = "READ", entityType = "Task", description = "获取即将到期的任务")
    public ResponseEntity<ApiResponse<List<TaskDTO>>> getUpcomingTasks(@RequestParam(defaultValue = "7") int days) {
        LocalDateTime beforeDate = LocalDateTime.now().plusDays(days);
        List<TaskDTO> tasks = taskService.getUpcomingTasks(beforeDate);
        return ResponseEntity.ok(ApiResponse.success("Upcoming tasks retrieved successfully", tasks));
    }

    @GetMapping("/overdue")
    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(action = "READ", entityType = "Task", description = "获取已过期任务")
    public ResponseEntity<ApiResponse<List<TaskDTO>>> getOverdueTasks() {
        List<TaskDTO> tasks = taskService.getOverdueTasks();
        return ResponseEntity.ok(ApiResponse.success("Overdue tasks retrieved successfully", tasks));
    }

    /**
     * Maximum accepted Markdown content length, measured in <em>characters</em>.
     *
     * <p>V102 迁移将 {@code tasks.content} 定义为 MySQL {@code TEXT}，其容量上限为
     * <strong>65,535 字节</strong>（而非字符数）。在 UTF-8 下，单个 CJK 字符占 3 字节，
     * 若直接以 65,535 字符为上限，CJK 文本可能达到约 196KB，超出列容量。因此这里
     * 收紧到 20,000 字符：即便全部是 CJK 也只占约 60KB，稳妥落在 64KB 内，同时对
     * 前端亦是友好、可在编辑器中强制提示的上限。</p>
     */
    private static final int MAX_CONTENT_CHARS = 20_000;
    private static final int MAX_COMMENT_CHARS = 5_000;

    private void sanitizeTaskDTO(TaskDTO dto) {
        if (dto.getTitle() != null) dto.setTitle(InputSanitizer.sanitizeString(dto.getTitle(), 200));
        if (dto.getDescription() != null) dto.setDescription(InputSanitizer.sanitizeString(dto.getDescription(), 2000));
        // NOTE: content is Markdown. Do NOT strip HTML tags here – frontend is responsible
        // for render-time sanitization (DOMPurify / equivalent in src/utils/markdown.js).
        // 使用 sanitizeMarkdown 以保留 \t \n \r（普通 sanitizeString 会剥离所有 0x00-0x1F
        // 控制字符并 trim，会破坏标题、列表、代码块等 Markdown 结构）。
        if (dto.getContent() != null) dto.setContent(InputSanitizer.sanitizeMarkdown(dto.getContent(), MAX_CONTENT_CHARS));
    }

    private String currentUsername(UserDetails userDetails) {
        return userDetails == null ? null : userDetails.getUsername();
    }
}
