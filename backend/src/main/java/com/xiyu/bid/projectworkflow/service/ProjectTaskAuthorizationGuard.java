// Input: 当前用户 username + 任务 id/目标状态
// Output: 授权断言（失败抛 403）；纯编排，判定委托给 ProjectTaskAuthorizationPolicy
// Pos: projectworkflow/service/ - 任务授权编排守卫（Split-First：从 ProjectWorkflowService 抽出，避免门面协作者超限）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.projectworkflow.service;

import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.entity.Task;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.projectworkflow.core.ProjectTaskAuthorizationPolicy;
import com.xiyu.bid.repository.TaskRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

/**
 * 项目任务授权编排守卫：先校验项目访问范围（统一守卫链），再解析当前用户角色码与任务指派人，
 * 委托 {@link ProjectTaskAuthorizationPolicy} 做纯核心判定，失败抛 403。
 *
 * <p>从 {@link ProjectWorkflowService} 抽离，避免门面积累 UserRepository/TaskRepository 协作者
 * 触发 MaintainabilityArchitectureTest 的 Split-First 协作者预算（≤5）。</p>
 *
 * <p>内部调用路径（username 为 null 或 "system"）跳过身份校验，保持向后兼容。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectTaskAuthorizationGuard {

    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final ProjectAccessScopeService projectAccessScopeService;

    /** 断言当前用户有权管理任务（手动添加/AI 拆解）。蓝图 §2.3.1：管理员/组长/负责人/辅助。 */
    public void assertCanManageTask(Long projectId, String username) {
        if (isInternalCall(username)) {
            return;
        }
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        User currentUser = resolveUser(username);
        String effectiveRoleCode = effectiveRoleCode(currentUser);
        ProjectTaskAuthorizationPolicy.Decision decision =
                ProjectTaskAuthorizationPolicy.canManageTask(effectiveRoleCode);
        if (!decision.allowed()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, decision.reason());
        }
    }

    /** 断言当前用户有权发起指定状态流转（提交任务/上传交付物=执行人本人；审核=管理员/组长/负责人/辅助）。 */
    public void assertStatusTransition(Long projectId, Long taskId, String toStatus, String username) {
        if (isInternalCall(username)) {
            return;
        }
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        User currentUser = resolveUser(username);
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在: " + taskId));
        String fromStatus = task.getStatus() == null ? null : task.getStatus().name();
        boolean isAssignee = Objects.equals(currentUser.getId(), task.getAssigneeId());
        String effectiveRoleCode = effectiveRoleCode(currentUser);
        ProjectTaskAuthorizationPolicy.Decision decision =
                ProjectTaskAuthorizationPolicy.decideStatusTransition(fromStatus, toStatus, effectiveRoleCode, isAssignee);
        if (!decision.allowed()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, decision.reason());
        }
    }

    private boolean isInternalCall(String username) {
        return username == null || username.isBlank() || "system".equals(username);
    }

    private User resolveUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在: " + username));
    }

    private String effectiveRoleCode(User user) {
        return RoleProfileCatalog.definitionForCode(user.getRoleCode()).code();
    }
}
