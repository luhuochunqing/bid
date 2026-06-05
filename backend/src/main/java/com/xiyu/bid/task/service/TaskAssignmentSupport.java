// Input: users, task repository, role profiles, and project access departments
// Output: assignment snapshots, visible candidates, and team workload view
// Pos: Service/业务支撑层
// 维护声明: 仅维护任务人员分配和团队工作量编排；任务 CRUD 与项目权限留在 TaskService。
package com.xiyu.bid.task.service;

import com.xiyu.bid.entity.Task;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.repository.TaskRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import com.xiyu.bid.service.RoleProfileService;
import com.xiyu.bid.task.dto.TaskAssignmentCandidateDTO;
import com.xiyu.bid.task.dto.TaskAssignmentRequest;
import com.xiyu.bid.task.dto.TeamTaskWorkloadDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaskAssignmentSupport {

    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final ProjectAccessScopeService projectAccessScopeService;
    private final RoleProfileService roleProfileService;

    public AssignmentSnapshot resolveAssignmentSnapshot(TaskAssignmentRequest request, User currentUser) {
        if (request == null || !request.hasAssignmentTarget()) {
            return AssignmentSnapshot.empty();
        }
        if (request.getAssigneeId() != null) {
            User assignee = resolveEnabledUserById(request.getAssigneeId());
            if (currentUser != null) {
                assertCanAccessTargetUser(currentUser, assignee, Boolean.TRUE.equals(request.getAllowCrossDeptCollaboration()));
            }
            return AssignmentSnapshot.fromUser(assignee);
        }

        String deptCode = trimToNull(request.getAssigneeDeptCode());
        if (currentUser != null && !Boolean.TRUE.equals(request.getAllowCrossDeptCollaboration())) {
            List<String> allowedDeptCodes = normalizeAllowedDeptCodes(currentUser);
            if (deptCode != null && !allowedDeptCodes.contains(deptCode)) {
                throw new AccessDeniedException("当前用户无权向该部门分配任务");
            }
        }
        return new AssignmentSnapshot(
                null,
                deptCode,
                defaultText(request.getAssigneeDeptName(), "未配置部门"),
                trimToNull(request.getAssigneeRoleCode()),
                trimToNull(request.getAssigneeRoleName())
        );
    }

    public void applyAssignment(Task task, AssignmentSnapshot assignment) {
        task.setAssigneeId(assignment.assigneeId());
        task.setAssigneeDeptCode(assignment.assigneeDeptCode());
        task.setAssigneeDeptName(assignment.assigneeDeptName());
        task.setAssigneeRoleCode(assignment.assigneeRoleCode());
        task.setAssigneeRoleName(assignment.assigneeRoleName());
    }

    public List<TaskAssignmentCandidateDTO> getAssignmentCandidates(String deptCode, String roleCode, String username) {
        User currentUser = resolveEnabledUserByUsername(username);
        List<String> allowedDeptCodes = normalizeAllowedDeptCodes(currentUser);
        String normalizedDeptCode = trimToNull(deptCode);
        String normalizedRoleCode = trimToNull(roleCode);

        return userRepository.findByEnabledTrue().stream()
                .filter(user -> canSeeCandidate(currentUser, user, allowedDeptCodes))
                .filter(user -> normalizedDeptCode == null || normalizedDeptCode.equalsIgnoreCase(user.getDepartmentCode()))
                .filter(user -> normalizedRoleCode == null || normalizedRoleCode.equalsIgnoreCase(user.getRoleCode()))
                .sorted(Comparator.comparing(User::getDepartmentCode, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(User::getRoleName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(User::getFullName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(user -> TaskAssignmentCandidateDTO.builder()
                        .userId(user.getId())
                        .name(user.getFullName())
                        .roleCode(user.getRoleCode())
                        .roleName(user.getRoleName())
                        .deptCode(defaultText(user.getDepartmentCode(), "UNASSIGNED"))
                        .deptName(defaultText(user.getDepartmentName(), "未配置部门"))
                        .enabled(Boolean.TRUE.equals(user.getEnabled()))
                        .build())
                .toList();
    }

    public TeamTaskWorkloadDTO getTeamTaskWorkload(String username) {
        User currentUser = resolveEnabledUserByUsername(username);
        List<String> allowedDeptCodes = normalizeAllowedDeptCodes(currentUser);
        if (!canSeeTeamWorkload(currentUser)) {
            return emptyWorkload("self", hasText(currentUser.getDepartmentCode()), "仅管理角色可查看团队任务分配");
        }
        if (allowedDeptCodes.isEmpty()) {
            return emptyWorkload(scopeOf(currentUser), false, "未配置组织关系");
        }

        List<User> visibleUsers = userRepository.findByEnabledTrue().stream()
                .filter(user -> allowedDeptCodes.contains(defaultText(user.getDepartmentCode(), "")))
                .sorted(Comparator.comparing(User::getDepartmentCode, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(User::getRoleName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(User::getFullName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
        if (visibleUsers.isEmpty()) {
            return emptyWorkload(scopeOf(currentUser), true, "当前范围内无团队成员");
        }

        Map<Long, List<Task>> tasksByAssignee = taskRepository.findByAssigneeIdIn(visibleUsers.stream().map(User::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(Task::getAssigneeId));
        var members = visibleUsers.stream()
                .map(user -> TaskWorkloadAssembler.buildTeamMemberWorkload(user, tasksByAssignee.getOrDefault(user.getId(), List.of())))
                .toList();

        return TeamTaskWorkloadDTO.builder()
                .scope(scopeOf(currentUser))
                .orgConfigured(true)
                .emptyReason(members.stream().allMatch(member -> member.getTasks().isEmpty()) ? "暂无任务数据" : null)
                .members(members)
                .build();
    }

    public void assertCanAccessTargetUser(User currentUser, User targetUser, boolean allowCrossDeptCollaboration) {
        if (roleProfileService.isAdminRole(currentUser)) {
            return;
        }
        if (!Boolean.TRUE.equals(targetUser.getEnabled())) {
            throw new IllegalArgumentException("目标责任人已停用，无法分配");
        }

        List<String> allowedDeptCodes = normalizeAllowedDeptCodes(currentUser);
        String targetDeptCode = defaultText(targetUser.getDepartmentCode(), "");
        if (allowCrossDeptCollaboration && allowedDeptCodes.isEmpty()) {
            return;
        }
        if (!allowedDeptCodes.isEmpty() && !allowedDeptCodes.contains(targetDeptCode)) {
            throw new AccessDeniedException(allowCrossDeptCollaboration ? "跨部门协作未在当前数据权限范围内" : "当前用户无权查看或分配该部门任务");
        }
    }

    public User resolveEnabledUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AccessDeniedException("当前用户不存在"));
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new AccessDeniedException("当前用户已停用");
        }
        return user;
    }

    public User resolveEnabledUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", String.valueOf(userId)));
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new IllegalArgumentException("目标责任人已停用，无法分配");
        }
        return user;
    }

    private List<String> normalizeAllowedDeptCodes(User user) {
        LinkedHashSet<String> allowedDeptCodes = new LinkedHashSet<>(projectAccessScopeService.getAllowedDepartmentCodes(user));
        if (hasText(user.getDepartmentCode())) {
            allowedDeptCodes.add(user.getDepartmentCode().trim());
        }
        return allowedDeptCodes.stream().filter(TaskAssignmentSupport::hasText).toList();
    }

    private boolean canSeeCandidate(User currentUser, User candidate, List<String> allowedDeptCodes) {
        if (!Boolean.TRUE.equals(candidate.getEnabled())) {
            return false;
        }
        if (roleProfileService.isAdminRole(currentUser)) {
            return true;
        }
        return allowedDeptCodes.isEmpty()
                ? Objects.equals(currentUser.getId(), candidate.getId())
                : allowedDeptCodes.contains(defaultText(candidate.getDepartmentCode(), ""));
    }

    private boolean canSeeTeamWorkload(User currentUser) {
        return roleProfileService.isAdminRole(currentUser)
                || "manager".equalsIgnoreCase(currentUser.getRoleCode())
                || "部门主管".equals(currentUser.getRoleName());
    }

    private String scopeOf(User currentUser) {
        return roleProfileService.isAdminRole(currentUser) ? "all" : "dept";
    }

    private static TeamTaskWorkloadDTO emptyWorkload(String scope, boolean orgConfigured, String reason) {
        return TeamTaskWorkloadDTO.builder()
                .scope(scope)
                .orgConfigured(orgConfigured)
                .emptyReason(reason)
                .members(List.of())
                .build();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String defaultText(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record AssignmentSnapshot(
            Long assigneeId,
            String assigneeDeptCode,
            String assigneeDeptName,
            String assigneeRoleCode,
            String assigneeRoleName
    ) {
        private static AssignmentSnapshot empty() {
            return new AssignmentSnapshot(null, null, null, null, null);
        }

        private static AssignmentSnapshot fromUser(User user) {
            return new AssignmentSnapshot(
                    user.getId(),
                    user.getDepartmentCode(),
                    user.getDepartmentName(),
                    user.getRoleCode(),
                    user.getRoleName()
            );
        }
    }
}
