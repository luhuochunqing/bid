package com.xiyu.bid.task.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.admin.service.DataScopeConfigService;
import com.xiyu.bid.entity.RoleProfile;
import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.entity.Task;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.project.repository.ProjectLeadAssignmentRepository;
import com.xiyu.bid.repository.TaskRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.service.RoleProfileService;
import com.xiyu.bid.project.notification.ProjectNotificationService;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.task.dto.TaskDTO;
import com.xiyu.bid.task.repository.TaskDeliverableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceProjectAccessTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProjectAccessScopeService projectAccessScopeService;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private RoleProfileService roleProfileService;

    @Mock
    private TaskHistoryRecorder taskHistoryRecorder;

    @Mock
    private ProjectNotificationService notificationService;

    @Mock
    private ProjectDocumentRepository projectDocumentRepository;

    @Mock
    private TaskDeliverableRepository taskDeliverableRepository;

    @Mock
    private TaskPermissionGuard taskPermissionGuard;

    @Mock
    private ProjectLeadAssignmentRepository leadAssignmentRepository;

    @Mock
    private DataScopeConfigService dataScopeConfigService;

    @Mock
    private TaskAssignmentSupport assignmentSupport;

    private TaskService taskService;

    @BeforeEach
    void setUp() {
        taskService = new TaskService(
                taskRepository,
                projectAccessScopeService,
                projectRepository,
                assignmentSupport,
                new TaskDtoMapper(new ObjectMapper(), projectDocumentRepository, taskDeliverableRepository),
                taskHistoryRecorder,
                notificationService,
                userRepository,
                taskPermissionGuard,
                leadAssignmentRepository,
                dataScopeConfigService
        );
    }

    @Test
    void getTaskByIdRejectsTaskFromInvisibleProject() {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task(1L, 20L, "不可见任务")));
        when(projectRepository.existsById(20L)).thenReturn(true);
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of(10L));
        when(projectRepository.existsById(20L)).thenReturn(true);

        assertThatThrownBy(() -> taskService.getTaskById(1L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void updateTaskRejectsTaskFromInvisibleProjectBeforeSaving() {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task(1L, 20L, "不可见任务")));
        when(projectRepository.existsById(20L)).thenReturn(true);
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of(10L));
        when(projectRepository.existsById(20L)).thenReturn(true);

        assertThatThrownBy(() -> taskService.updateTask(1L, TaskDTO.builder().title("新标题").build()))
                .isInstanceOf(AccessDeniedException.class);

        verify(taskRepository, never()).save(org.mockito.ArgumentMatchers.any(Task.class));
    }

    @Test
    void getAllTasksFiltersTasksFromInvisibleProjects() {
        when(taskRepository.findAll()).thenReturn(List.of(
                task(1L, 10L, "可见任务"),
                task(2L, 20L, "不可见任务")
        ));
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of(10L));

        List<TaskDTO> tasks = taskService.getAllTasks();

        assertThat(tasks).extracting(TaskDTO::getId).containsExactly(1L);
    }

    @Test
    void getUpcomingTasksFiltersTasksFromInvisibleProjects() {
        LocalDateTime beforeDate = LocalDateTime.now().plusDays(7);
        when(taskRepository.findByDueDateBefore(beforeDate)).thenReturn(List.of(
                task(1L, 10L, "可见即将到期"),
                task(2L, 20L, "不可见即将到期")
        ));
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of(10L));

        List<TaskDTO> tasks = taskService.getUpcomingTasks(beforeDate);

        assertThat(tasks).extracting(TaskDTO::getId).containsExactly(1L);
    }

    @Test
    void getOverdueTasksFiltersTasksFromInvisibleProjects() {
        when(taskRepository.findByDueDateBeforeAndStatusNot(
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.eq(Task.Status.COMPLETED)
        )).thenReturn(List.of(
                task(1L, 10L, "可见逾期"),
                task(2L, 20L, "不可见逾期")
        ));
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of(10L));

        List<TaskDTO> tasks = taskService.getOverdueTasks();

        assertThat(tasks).extracting(TaskDTO::getId).containsExactly(1L);
    }

    // CO-361: 三态模型已彻底收口，CANCELLED 已从枚举移除，
    // isVisibleTask 仅做 null 过滤，不再有"过滤 CANCELLED"语义。

    /**
     * CO-361: OSS 同步用户（role_id=NULL）在项目详情页任务看板看不到自己作为负责人项目的任务。
     *
     * <p>根因：{@link User#getRoleCode()} 对 role_id=NULL 用户返回 "manager"（实体 fallback），
     * 而非 OSS 缓存的 "bid-Team"。{@code TaskService.getTasksByProjectId} 传给
     * {@link com.xiyu.bid.task.core.TaskVisibilityPolicy} 的是 "manager"，
     * 导致投标专员 + 项目负责人本应看所有任务，却走错分支只查 assignee=自己。
     *
     * <p>修复：改用 {@link DataScopeConfigService#getRoleCode(User)}（OSS-cache-aware），
     * cache 命中返回 "bid-Team"，配合 matchesAnyLead → canViewAllProjectTasks 返回 true → 查项目所有任务。
     */
    @Test
    void getTasksByProjectIdUsesOssRoleCodeForBidTeamLead() {
        // OSS 同步用户：DB role_id=NULL → User.getRoleCode() 返回 "manager"，但 OSS 缓存角色为 bid-Team
        User ossBidTeamUser = User.builder()
                .id(7220L)
                .username("wangzhanjun")
                .fullName("王占俊")
                .build();
        when(assignmentSupport.resolveEnabledUserByUsername("wangzhanjun")).thenReturn(ossBidTeamUser);
        // 关键：DataScopeConfigService.getRoleCode 返回 OSS 缓存的 "bid-Team"，而非实体的 "manager"
        when(dataScopeConfigService.getRoleCode(ossBidTeamUser))
                .thenReturn(RoleProfileCatalog.BID_SPECIALIST_CODE);

        // 王占俊是项目 101 的主投标负责人
        when(leadAssignmentRepository.resolveLeadIdsByProjectId(101L))
                .thenReturn(new Long[]{7220L, 8556L});
        // 项目访问检查通过（王占俊作为 primary_lead 命中）
        when(projectRepository.existsById(101L)).thenReturn(true);
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of(101L));

        Task projectTask = Task.builder()
                .id(2368L)
                .projectId(101L)
                .title("项目任务（assignee 不是王占俊）")
                .status(Task.Status.TODO)
                .assigneeId(9999L)
                .build();
        // 修复后：bid-Team + matchesAnyLead(7220, 7220, 8556) → true → 走 findByProjectId
        when(taskRepository.findByProjectId(101L)).thenReturn(List.of(projectTask));

        List<TaskDTO> tasks = taskService.getTasksByProjectId(101L, "wangzhanjun");

        assertThat(tasks).extracting(TaskDTO::getId).containsExactly(2368L);
        // 验证走的是"看所有任务"分支，而非"只看 assignee=自己"
        verify(taskRepository).findByProjectId(101L);
        verify(taskRepository, never()).findByProjectIdAndAssigneeId(eq(101L), eq(7220L));
    }

    /**
     * CO-361 回归守护：OSS cache miss（返回 null）时 fail-closed，走 assignee=自己 分支，不越权放大。
     */
    @Test
    void getTasksByProjectIdFailClosedWhenOssCacheMiss() {
        User ossUser = User.builder()
                .id(7220L)
                .username("ossuser")
                .fullName("OSS用户")
                .build();
        when(assignmentSupport.resolveEnabledUserByUsername("ossuser")).thenReturn(ossUser);
        // cache miss → null（fail-closed）
        when(dataScopeConfigService.getRoleCode(ossUser)).thenReturn(null);

        when(leadAssignmentRepository.resolveLeadIdsByProjectId(101L))
                .thenReturn(new Long[]{7220L, 8556L});
        when(projectRepository.existsById(101L)).thenReturn(true);
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of(101L));

        Task ownTask = Task.builder()
                .id(2368L)
                .projectId(101L)
                .title("自己的任务")
                .status(Task.Status.TODO)
                .assigneeId(7220L)
                .build();
        when(taskRepository.findByProjectIdAndAssigneeId(101L, 7220L)).thenReturn(List.of(ownTask));

        List<TaskDTO> tasks = taskService.getTasksByProjectId(101L, "ossuser");

        // cache miss → canViewAllProjectTasks(null,...)=false → 只看 assignee=自己
        assertThat(tasks).extracting(TaskDTO::getId).containsExactly(2368L);
        verify(taskRepository, never()).findByProjectId(any());
        verify(taskRepository).findByProjectIdAndAssigneeId(101L, 7220L);
    }

    private Task task(Long id, Long projectId, String title) {
        return task(id, projectId, title, Task.Status.TODO);
    }

    private Task task(Long id, Long projectId, String title, Task.Status status) {
        return Task.builder()
                .id(id)
                .projectId(projectId)
                .title(title)
                .status(status)
                .priority(Task.Priority.MEDIUM)
                .build();
    }
}
