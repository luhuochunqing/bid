// Input: Task DTOs, repositories, assignment support, and project access scope
// Output: task command/query results filtered by project data permissions
// Pos: Service/业务编排层
// 维护声明: 仅维护任务 CRUD 与项目数据权限编排；人员分配和团队工作量留在 TaskAssignmentSupport。
package com.xiyu.bid.task.service;

import com.xiyu.bid.entity.Task;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TaskRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import com.xiyu.bid.task.core.TaskProjectVisibilityPolicy;
import com.xiyu.bid.task.dto.TaskAssignmentRequest;
import com.xiyu.bid.task.dto.TaskDTO;
import com.xiyu.bid.task.dto.TeamTaskWorkloadDTO;
import com.xiyu.bid.project.notification.ProjectNotificationService;
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

    public TaskService(TaskRepository taskRepository,
                       ProjectAccessScopeService projectAccessScopeService,
                       ProjectRepository projectRepository,
                       TaskAssignmentSupport assignmentSupport,
                       TaskDtoMapper taskDtoMapper,
                       TaskHistoryRecorder taskHistoryRecorder,
                       ProjectNotificationService notificationService,
                       UserRepository userRepository,
                       TaskPermissionGuard taskPermissionGuard) {
        this.taskRepository = taskRepository;
        this.projectAccessScopeService = projectAccessScopeService;
        this.projectRepository = projectRepository;
        this.assignmentSupport = assignmentSupport;
        this.taskDtoMapper = taskDtoMapper;
        this.taskHistoryRecorder = taskHistoryRecorder;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
        this.taskPermissionGuard = taskPermissionGuard;
    }

    @Transactional
    public TaskDTO createTask(TaskDTO taskDTO) {
        log.info("Creating task: {}", taskDTO.getTitle());
        assertCanAccessProject(taskDTO.getProjectId());
        assertCanManageTask(taskDTO.getProjectId());
        TaskAssignmentSupport.AssignmentSnapshot assignment = assignmentSupport.resolveAssignmentSnapshot(
                assignmentRequestFrom(taskDTO),
                null
        );
        Task savedTask = taskRepository.save(Task.builder()
                .projectId(taskDTO.getProjectId())
                .title(taskDTO.getTitle())
                .description(taskDTO.getDescription())
                .content(taskDTO.getContent())
                .assigneeId(assignment.assigneeId())
                .assigneeDeptCode(assignment.assigneeDeptCode())
                .assigneeDeptName(assignment.assigneeDeptName())
                .assigneeRoleCode(assignment.assigneeRoleCode())
                .assigneeRoleName(assignment.assigneeRoleName())
                .status(taskDTO.getStatus() != null ? taskDTO.getStatus() : Task.Status.TODO)
                .priority(taskDTO.getPriority() != null ? taskDTO.getPriority() : Task.Priority.MEDIUM)
                .dueDate(taskDTO.getDueDate())
                .extendedFieldsJson(taskDtoMapper.serializeExtendedFields(taskDTO.getExtendedFields()))
                .build());
        log.info("Task created successfully with id: {}", savedTask.getId());
        return taskDtoMapper.toDTO(savedTask, resolveAssigneeName(savedTask.getAssigneeId()));
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
        return taskDtoMapper.toDTO(task, resolveAssigneeName(task.getAssigneeId()));
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
        assertCanManageOrSubmitTask(task);
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
        if (taskDTO.getExtendedFields() != null) {
            task.setExtendedFieldsJson(taskDtoMapper.serializeExtendedFields(taskDTO.getExtendedFields()));
        }
        Task saved = taskRepository.save(task);
        taskHistoryRecorder.recordUpdate(before, saved, actorUsername);
        return taskDtoMapper.toDTO(saved, resolveAssigneeName(saved.getAssigneeId()));
    }

    @Transactional
    public void deleteTask(Long id) {
        log.info("Deleting task: {}", id);
        Task task = findTask(id);
        assertCanAccessProject(task.getProjectId());
        assertCanManageTask(task.getProjectId());
        taskRepository.deleteById(id);
        log.info("Task deleted successfully: {}", id);
    }

    @Transactional(readOnly = true)
    public List<TaskDTO> getTasksByProjectId(Long projectId) {
        log.debug("Fetching tasks for project: {}", projectId);
        assertCanAccessProject(projectId);
        return toDTOsWithNames(taskRepository.findByProjectId(projectId));
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
        assertCanTransitionTaskStatus(task, status);
        Task before = TaskSnapshots.copy(task);
        task.setStatus(status);
        Task saved = taskRepository.save(task);
        taskHistoryRecorder.recordUpdate(before, saved, actorUsername);
        return taskDtoMapper.toDTO(saved, resolveAssigneeName(saved.getAssigneeId()));
    }

    @Transactional
    public TaskDTO assignTask(Long id, TaskAssignmentRequest request, String username) {
        log.info("Assigning task {} to user: {}", id, request == null ? null : request.getAssigneeId());
        Task task = findTask(id);
        assertCanAccessProject(task.getProjectId());
        assertCanManageTask(task.getProjectId());
        User currentUser = assignmentSupport.resolveEnabledUserByUsername(username);
        Task before = TaskSnapshots.copy(task);
        assignmentSupport.applyAssignment(task, assignmentSupport.resolveAssignmentSnapshot(request, currentUser));
        Task saved = taskRepository.save(task);
        taskHistoryRecorder.recordUpdate(before, saved, username);

        // 通知 #4: 分配投标负责人 → 被分配人
        if (request != null && request.getAssigneeId() != null) {
            notificationService.notifyTaskAssigned(task.getProjectId(), request.getAssigneeId(), currentUser.getId());
        }

        return taskDtoMapper.toDTO(saved, resolveAssigneeName(saved.getAssigneeId()));
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
        return TaskProjectVisibilityPolicy.filterVisibleTasks(
                tasks,
                projectAccessScopeService.getAllowedProjectIdsForCurrentUser()
        );
    }

    private void assertCanAccessProject(Long projectId) {
        if (projectId == null || !projectRepository.existsById(projectId)) {
            return;
        }
        if (!TaskProjectVisibilityPolicy.canAccessProject(
                projectId,
                projectAccessScopeService.getAllowedProjectIdsForCurrentUser()
        )) {
            throw new AccessDeniedException("权限不足，无法访问该项目任务");
        }
    }

    private void assertCanManageTask(Long projectId) {
        taskPermissionGuard.assertCanManageTask(projectId);
    }

    private void assertCanManageOrSubmitTask(Task task) {
        taskPermissionGuard.assertCanManageOrSubmitTask(task);
    }

    private void assertCanTransitionTaskStatus(Task task, Task.Status targetStatus) {
        taskPermissionGuard.assertCanTransitionTaskStatus(task, targetStatus);
    }

    private static TaskAssignmentRequest assignmentRequestFrom(TaskDTO taskDTO) {
        return TaskAssignmentRequest.builder()
                .assigneeId(taskDTO.getAssigneeId())
                .assigneeDeptCode(taskDTO.getAssigneeDeptCode())
                .assigneeDeptName(taskDTO.getAssigneeDeptName())
                .assigneeRoleCode(taskDTO.getAssigneeRoleCode())
                .assigneeRoleName(taskDTO.getAssigneeRoleName())
                .build();
    }

    private static boolean hasAssignmentChange(TaskDTO taskDTO) {
        return taskDTO.getAssigneeId() != null
                || hasText(taskDTO.getAssigneeDeptCode())
                || hasText(taskDTO.getAssigneeRoleCode());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
    private List<TaskDTO> toDTOsWithNames(List<Task> tasks) {
        var names = userRepository.findAllById(tasks.stream().map(Task::getAssigneeId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().filter(u -> u.getFullName() != null && !u.getFullName().isBlank()).collect(Collectors.toMap(User::getId, User::getFullName, (a, b) -> a));
        return taskDtoMapper.toDTOs(tasks, names);
    }
    private String resolveAssigneeName(Long userId) {
        if (userId == null) return null;
        return userRepository.findById(userId).map(User::getFullName).filter(n -> n != null && !n.isBlank()).orElse(null);
    }
}
