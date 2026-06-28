// Input: Task DTOs, repositories, assignment support, and project access scope
// Output: task command/query results filtered by project data permissions
// Pos: Service/业务编排层
// 维护声明: 仅维护任务 CRUD 与项目数据权限编排；人员分配和团队工作量留在 TaskAssignmentSupport。
package com.xiyu.bid.task.service;
import com.xiyu.bid.entity.Task;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.project.repository.BidDocumentReviewRepository;
import com.xiyu.bid.project.repository.ProjectLeadAssignmentRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TaskRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.admin.service.DataScopeConfigService;
import com.xiyu.bid.service.ProjectAccessScopeService;
import com.xiyu.bid.task.core.TaskProjectVisibilityPolicy;
import com.xiyu.bid.task.core.TaskVisibilityPolicy;
import com.xiyu.bid.task.dto.TaskAssignmentRequest;
import com.xiyu.bid.task.dto.TaskDTO;
import com.xiyu.bid.task.dto.TeamTaskWorkloadDTO;
import com.xiyu.bid.project.notification.ProjectNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {
    private final TaskRepository taskRepository;
    private final ProjectAccessScopeService projectAccessScopeService;
    private final ProjectRepository projectRepository;
    private final TaskAssignmentSupport assignmentSupport;
    private final TaskDtoMapper taskDtoMapper;
    private final TaskHistoryRecorder taskHistoryRecorder;
    private final ProjectNotificationService notificationService;
    private final UserRepository userRepository;
    private final TaskPermissionGuard taskPermissionGuard;
    private final ProjectLeadAssignmentRepository leadAssignmentRepository;
    private final BidDocumentReviewRepository bidDocumentReviewRepository;
    private final DataScopeConfigService dataScopeConfigService;
    @Transactional
    public TaskDTO createTask(TaskDTO taskDTO) {
        return createTask(taskDTO, null);
    }
    /**
     * CO-382: 创建任务时记录创建人用户名（来自 Controller 的认证上下文），
     * 用于看板展示"创建人"。权限仍走 {@link TaskPermissionGuard}，不依赖此字段。
     */
    @Transactional
    public TaskDTO createTask(TaskDTO taskDTO, String creatorUsername) {
        log.info("Creating task: {}", taskDTO.getTitle());
        assertCanAccessProject(taskDTO.getProjectId());
        taskPermissionGuard.assertCanManageTask(taskDTO.getProjectId());
        return doCreateTask(taskDTO, creatorUsername);
    }
    @Transactional
    public TaskDTO createSystemTask(TaskDTO taskDTO) {
        log.info("Creating system task: {}", taskDTO.getTitle());
        return doCreateTask(taskDTO, "system");
    }
    private TaskDTO doCreateTask(TaskDTO dto, String creator) {
        var a = assignmentSupport.resolveAssignmentSnapshot(assignmentRequestFrom(dto), null);
        Task saved = taskRepository.save(Task.builder()
                .projectId(dto.getProjectId()).title(dto.getTitle())
                .description(dto.getDescription()).content(dto.getContent())
                .assigneeId(a.assigneeId()).assigneeDeptCode(a.assigneeDeptCode()).assigneeDeptName(a.assigneeDeptName())
                .assigneeRoleCode(a.assigneeRoleCode()).assigneeRoleName(a.assigneeRoleName())
                .status(Task.Status.TODO).priority(dto.getPriority() != null ? dto.getPriority() : Task.Priority.MEDIUM)
                .dueDate(dto.getDueDate()).extendedFieldsJson(taskDtoMapper.serializeExtendedFields(dto.getExtendedFields()))
                .createdBy(creator).build());
        log.info("Task created successfully with id: {}", saved.getId());
        return toDTOWithNames(saved);
    }
    @Transactional(readOnly = true)
    public List<TaskDTO> getAllTasks() {
        log.debug("Fetching all tasks");
        return toDTOsWithNames(visibleTasks(taskRepository.findAll()));
    }
    @Transactional(readOnly = true)
    public TaskDTO getTaskById(Long id) {
        log.debug("Fetching task by id: {}", id);
        Task task = findTask(id);
        assertCanAccessProject(task.getProjectId());
        return toDTOWithNames(task);
    }
    @Transactional
    public TaskDTO updateTask(Long id, TaskDTO taskDTO) {
        return updateTask(id, taskDTO, null);
    }
    @Transactional
    public TaskDTO updateTask(Long id, TaskDTO taskDTO, String actorUsername) {
        log.info("Updating task: {}", id);
        Task task = findTask(id);
        assertCanAccessProject(task.getProjectId());
        taskPermissionGuard.assertCanManageOrSubmitTask(task);
        Task before = TaskSnapshots.copy(task);
        if (taskDTO.getTitle() != null) {
            task.setTitle(taskDTO.getTitle());
        }
        if (taskDTO.getDescription() != null) {
            task.setDescription(taskDTO.getDescription());
        }
        if (taskDTO.getContent() != null) {
            task.setContent(taskDTO.getContent());
        }
        if (hasAssignmentChange(taskDTO)) {
            assignmentSupport.applyAssignment(task, assignmentSupport.resolveAssignmentSnapshot(assignmentRequestFrom(taskDTO), null));
        }
        if (taskDTO.getStatus() != null) {
            task.setStatus(taskDTO.getStatus());
        }
        if (taskDTO.getPriority() != null) {
            task.setPriority(taskDTO.getPriority());
        }
        if (taskDTO.getDueDate() != null) {
            task.setDueDate(taskDTO.getDueDate());
        }
        if (taskDTO.getCompletionNotes() != null) {
            task.setCompletionNotes(taskDTO.getCompletionNotes());
        }
        if (taskDTO.getExtendedFields() != null) {
            task.setExtendedFieldsJson(taskDtoMapper.serializeExtendedFields(taskDTO.getExtendedFields()));
        }
        Task saved = taskRepository.save(task);
        taskHistoryRecorder.recordUpdate(before, saved, actorUsername);
        return toDTOWithNames(saved);
    }
    @Transactional
    public void deleteTask(Long id) {
        log.info("Deleting task: {}", id);
        Task task = findTask(id);
        assertCanAccessProject(task.getProjectId());
        taskPermissionGuard.assertCanManageTask(task.getProjectId());
        taskRepository.deleteById(id);
        log.info("Task deleted successfully: {}", id);
    }
    @Transactional(readOnly = true)
    public List<TaskDTO> getTasksByProjectId(Long projectId, String username) {
        log.debug("Fetching tasks for project: {}", projectId);
        assertCanAccessProject(projectId);
        User currentUser = assignmentSupport.resolveEnabledUserByUsername(username);
        Long[] leadIds = leadAssignmentRepository.resolveLeadIdsByProjectId(projectId);
        // CO-361: 使用 OSS-cache-aware 的 roleCode（DataScopeConfigService.getRoleCode），
        // 而非 User.getRoleCode() 实体 fallback。OSS 同步用户 DB role_id=NULL 时实体返回 "manager"，
        // 会导致投标专员（OSS bid-Team）+ 项目负责人误走"只看 assignee=自己"分支，看不到项目任务。
        boolean canViewAll = TaskVisibilityPolicy.canViewAllProjectTasks(
                dataScopeConfigService.getRoleCode(currentUser), currentUser.getId(), leadIds[0], leadIds[1])
                || isAssignedReviewer(projectId, currentUser.getId());
        List<Task> tasks = canViewAll
                ? taskRepository.findByProjectId(projectId)
                : taskRepository.findByProjectIdAndAssigneeId(projectId, currentUser.getId());
        // CO-361: 三态模型下 isVisibleTask 仅做 null 过滤，与独立看板展示语义一致
        return toDTOsWithNames(tasks.stream().filter(TaskBoardItemMapper::isVisibleTask).toList());
    }
    @Transactional(readOnly = true)
    public List<TaskDTO> getTasksByAssigneeId(Long assigneeId) {
        log.debug("Fetching tasks for assignee: {}", assigneeId);
        return toDTOsWithNames(visibleTasks(taskRepository.findByAssigneeId(assigneeId)));
    }
    @Transactional(readOnly = true)
    public List<TaskDTO> getAccessibleTasksByAssigneeId(Long assigneeId, String username) {
        User currentUser = assignmentSupport.resolveEnabledUserByUsername(username);
        if (assigneeId == null || Objects.equals(currentUser.getId(), assigneeId)) {
            return getTasksByAssigneeId(currentUser.getId());
        }
        User targetUser = assignmentSupport.resolveEnabledUserById(assigneeId);
        assignmentSupport.assertCanAccessTargetUser(currentUser, targetUser, false);
        return getTasksByAssigneeId(assigneeId);
    }
    @Transactional(readOnly = true)
    public List<TaskDTO> getTasksByStatus(Task.Status status) {
        log.debug("Fetching tasks with status: {}", status);
        return toDTOsWithNames(visibleTasks(taskRepository.findByStatus(status)));
    }
    @Transactional
    public TaskDTO updateTaskStatus(Long id, Task.Status status) {
        return updateTaskStatus(id, status, null);
    }
    @Transactional
    public TaskDTO updateTaskStatus(Long id, Task.Status status, String actorUsername) {
        log.info("Updating task {} status to: {}", id, status);
        Task task = findTask(id);
        assertCanAccessProject(task.getProjectId());
        taskPermissionGuard.assertCanTransitionTaskStatus(task, status);
        Task before = TaskSnapshots.copy(task);
        task.setStatus(status);
        Task saved = taskRepository.save(task);
        taskHistoryRecorder.recordUpdate(before, saved, actorUsername);
        return toDTOWithNames(saved);
    }
    @Transactional
    public TaskDTO assignTask(Long id, TaskAssignmentRequest request, String username) {
        log.info("Assigning task {} to user: {}", id, request == null ? null : request.getAssigneeId());
        Task task = findTask(id);
        assertCanAccessProject(task.getProjectId());
        boolean isReassignment = task.getAssigneeId() != null
                && !Objects.equals(task.getAssigneeId(), request == null ? null : request.getAssigneeId());
        taskPermissionGuard.assertCanAssignTask(task.getProjectId(), isReassignment);
        User currentUser = assignmentSupport.resolveEnabledUserByUsername(username);
        Task before = TaskSnapshots.copy(task);
        assignmentSupport.applyAssignment(task, assignmentSupport.resolveAssignmentSnapshot(request, currentUser));
        Task saved = taskRepository.save(task);
        taskHistoryRecorder.recordUpdate(before, saved, username);
        // 通知 #4: 分配投标负责人 → 被分配人
        if (request != null && request.getAssigneeId() != null) {
            notificationService.notifyTaskAssigned(task.getProjectId(), request.getAssigneeId(), currentUser.getId());
        }
        return toDTOWithNames(saved);
    }
    @Transactional(readOnly = true)
    public TeamTaskWorkloadDTO getTeamTaskWorkload(String username) {
        return assignmentSupport.getTeamTaskWorkload(username);
    }
    @Transactional(readOnly = true)
    public List<TaskDTO> getUpcomingTasks(LocalDateTime beforeDate) {
        log.debug("Fetching tasks due before: {}", beforeDate);
        return toDTOsWithNames(visibleTasks(taskRepository.findByDueDateBefore(beforeDate)));
    }
    @Transactional(readOnly = true)
    public List<TaskDTO> getOverdueTasks() {
        log.debug("Fetching overdue tasks");
        return toDTOsWithNames(visibleTasks(taskRepository.findByDueDateBeforeAndStatusNot(LocalDateTime.now(), Task.Status.COMPLETED)));
    }
    public Long countProjectTasks(Long projectId) {
        assertCanAccessProject(projectId);
        return taskRepository.countByProjectId(projectId);
    }
    public Long countUserTasks(Long assigneeId) {
        return (long) visibleTasks(taskRepository.findByAssigneeId(assigneeId)).size();
    }
    private Task findTask(Long id) {
        return taskRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Task", id.toString()));
    }
    private List<Task> visibleTasks(List<Task> tasks) {
        return TaskProjectVisibilityPolicy.filterVisibleTasks(tasks, projectAccessScopeService.getAllowedProjectIdsForCurrentUser());
    }
    private void assertCanAccessProject(Long projectId) {
        if (projectId == null || !projectRepository.existsById(projectId)) return;
        if (!TaskProjectVisibilityPolicy.canAccessProject(projectId, projectAccessScopeService.getAllowedProjectIdsForCurrentUser()))
            throw new AccessDeniedException("权限不足，无法访问该项目任务");
    }
    private static TaskAssignmentRequest assignmentRequestFrom(TaskDTO dto) {
        return TaskAssignmentRequest.builder().assigneeId(dto.getAssigneeId())
                .assigneeDeptCode(dto.getAssigneeDeptCode()).assigneeDeptName(dto.getAssigneeDeptName())
                .assigneeRoleCode(dto.getAssigneeRoleCode()).assigneeRoleName(dto.getAssigneeRoleName()).build();
    }
    private static boolean hasAssignmentChange(TaskDTO d) { return d.getAssigneeId() != null || hasText(d.getAssigneeDeptCode()) || hasText(d.getAssigneeRoleCode()); }
    private static boolean hasText(String v) { return v != null && !v.isBlank(); }
    private boolean isAssignedReviewer(Long projectId, Long uid) {
        return uid != null && bidDocumentReviewRepository.findByProjectId(projectId).map(r -> uid.equals(r.getReviewerId())).orElse(false);
    }
    private List<TaskDTO> toDTOsWithNames(List<Task> tasks) {
        var assigneeNames = userRepository.findAllById(tasks.stream().map(Task::getAssigneeId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().filter(u -> u.getFullName() != null && !u.getFullName().isBlank()).collect(Collectors.toMap(User::getId, User::getFullName, (a, b) -> a));
        var creatorNames = userRepository.findAllByUsernameIn(tasks.stream().map(Task::getCreatedBy).filter(c -> c != null && !c.isBlank()).collect(Collectors.toSet()))
                .stream().filter(u -> u.getFullName() != null && !u.getFullName().isBlank()).collect(Collectors.toMap(User::getUsername, User::getFullName, (a, b) -> a));
        return taskDtoMapper.toDTOs(tasks, assigneeNames, creatorNames);
    }
    private TaskDTO toDTOWithNames(Task task) { return taskDtoMapper.toDTO(task, resolveAssigneeName(task.getAssigneeId()), resolveCreatorName(task.getCreatedBy())); }
    private String resolveAssigneeName(Long id) { return id == null ? null : userRepository.findById(id).map(User::getFullName).filter(n -> !n.isBlank()).orElse(null); }
    private String resolveCreatorName(String u) { return u == null || u.isBlank() ? null : userRepository.findByUsername(u).map(User::getFullName).filter(n -> n != null && !n.isBlank()).orElse(null); }
}
