// Input: security context, UserRepository and ProjectRepository
// Output: current-user project access decisions and project scope snapshots
// Pos: Service/权限支撑层
// 维护声明: 维护项目访问范围判断；显式项目、部门范围和管理员绕过统一在这里收口。
package com.xiyu.bid.service;

import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.matrixcollaboration.entity.CrmCustomerPermission;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.matrixcollaboration.entity.ProjectMember;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.matrixcollaboration.repository.CrmCustomerPermissionRepository;
import com.xiyu.bid.matrixcollaboration.repository.ProjectMemberRepository;
import com.xiyu.bid.project.entity.BidDocumentReviewEntity;
import com.xiyu.bid.project.repository.BidDocumentReviewRepository;
import com.xiyu.bid.project.repository.ProjectInitiationDetailsRepository;
import com.xiyu.bid.project.repository.ProjectLeadAssignmentRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.admin.service.DataScopeAccessProfile;
import com.xiyu.bid.admin.service.DataScopeConfigService;
import com.xiyu.bid.admin.service.ProjectGroupService;
import com.xiyu.bid.repository.TaskRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.security.EffectiveRoleResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectAccessScopeService {

    private static final String ADMIN_AUTHORITY = "ROLE_ADMIN";
    private static final String EXTERNAL_API_AUTHORITY = "ROLE_EXTERNAL_API";

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final DataScopeConfigService dataScopeConfigService;
    private final ProjectGroupService projectGroupService;
    private final ProjectMemberRepository projectMemberRepository;
    private final CrmCustomerPermissionRepository crmCustomerPermissionRepository;
    private final ProjectLeadAssignmentRepository leadAssignmentRepository;
    private final ProjectInitiationDetailsRepository initiationDetailsRepository;
    private final TaskRepository taskRepository;
    private final BidDocumentReviewRepository bidDocumentReviewRepository;
    private final EffectiveRoleResolver effectiveRoleResolver;

    public List<Long> getAllowedProjectIds(User user) {
        if (user == null) {
            return List.of();
        }
        // CO-373: 角色码只解析一次，避免同方法内重复读 OSS 缓存（Redis 往返 + 重复 debug 日志）。
        String effectiveRoleCode = effectiveRoleResolver.resolveRoleCode(user);
        if (RoleProfileCatalog.ADMIN_CODE.equalsIgnoreCase(effectiveRoleCode)) {
            return List.of();
        }
        DataScopeAccessProfile accessProfile = dataScopeConfigService.getAccessProfile(user);
        if ("all".equals(accessProfile.getDataScope())) {
            return projectRepository.findAllProjectIds().stream()
                    .filter(java.util.Objects::nonNull)
                    .sorted(Comparator.naturalOrder())
                    .toList();
        }

        Set<Long> allowedIds = new LinkedHashSet<>(projectRepository.findAccessibleProjectIdsByUserId(user.getId()));
        allowedIds.addAll(accessProfile.getExplicitProjectIds());
        allowedIds.addAll(projectGroupService.getGrantedProjectIds(user));
        
        // Add collaborated projects
        allowedIds.addAll(projectMemberRepository.findByUserId(user.getId()).stream()
                .map(ProjectMember::getProjectId)
                .collect(Collectors.toList()));

        // Add projects where user is assigned as primary bidding lead
        allowedIds.addAll(leadAssignmentRepository.findByPrimaryLeadUserId(user.getId()).stream()
                .map(a -> a.getProjectId())
                .collect(Collectors.toList()));

        // CO-361: Add projects where user is assigned as secondary bidding lead (副投标负责人)
        if (RoleProfileCatalog.BID_SPECIALIST_CODE.equalsIgnoreCase(effectiveRoleCode)) {
            allowedIds.addAll(leadAssignmentRepository.findBySecondaryLeadUserId(user.getId()).stream()
                    .map(a -> a.getProjectId())
                    .collect(Collectors.toList()));
        }

        // CO-361: Add projects where user is the project leader (项目负责人, owner_user_id in initiation details)
        allowedIds.addAll(initiationDetailsRepository.findByOwnerUserId(user.getId()).stream()
                .map(com.xiyu.bid.project.entity.ProjectInitiationDetails::getProjectId)
                .collect(Collectors.toList()));

        // Add projects where current user owns assigned project tasks
        allowedIds.addAll(taskRepository.findDistinctProjectIdsByAssigneeId(user.getId()));

        // Add projects from CRM-authorized customers
        List<String> crmCustomerIds = crmCustomerPermissionRepository.findByUserId(user.getId()).stream()
                .map(CrmCustomerPermission::getCustomerId)
                .collect(Collectors.toList());
        if (!crmCustomerIds.isEmpty()) {
            allowedIds.addAll(projectRepository.findBySourceCustomerIdIn(crmCustomerIds).stream()
                    .map(Project::getId)
                    .collect(Collectors.toList()));
        }

        // CO-315: Add projects where current user is assigned as bid document reviewer
        allowedIds.addAll(bidDocumentReviewRepository.findByReviewerId(user.getId()).stream()
                .map(BidDocumentReviewEntity::getProjectId)
                .collect(Collectors.toList()));

        if (!accessProfile.getAllowedDepartmentCodes().isEmpty()) {
            allowedIds.addAll(projectRepository.findAccessibleProjectIdsByDepartmentCodes(accessProfile.getAllowedDepartmentCodes()));
        }
        return allowedIds.stream()
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    public List<String> getAllowedDepartmentCodes(User user) {
        if (user == null || RoleProfileCatalog.ADMIN_CODE.equalsIgnoreCase(effectiveRoleResolver.resolveRoleCode(user))) {
            return List.of();
        }
        return dataScopeConfigService.getAccessProfile(user).getAllowedDepartmentCodes();
    }

    public List<Long> getAllowedProjectIdsForCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (hasAdminAccess(authentication)) {
            return List.of();
        }
        return getAllowedProjectIds(resolveCurrentUser(authentication));
    }

    public boolean currentUserHasAdminAccess() {
        return hasAdminAccess(SecurityContextHolder.getContext().getAuthentication());
    }

    public List<Project> filterAccessibleProjects(List<Project> projects) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (hasAdminAccess(authentication)) {
            return projects;
        }

        Set<Long> allowedIds = new LinkedHashSet<>(getAllowedProjectIds(resolveCurrentUser(authentication)));
        return projects.stream()
                .filter(project -> allowedIds.contains(project.getId()))
                .toList();
    }

    @Transactional(readOnly = true, noRollbackFor = AccessDeniedException.class)
    public void assertCurrentUserCanAccessProject(Long projectId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (hasAdminAccess(authentication)) {
            return;
        }

        User user = resolveCurrentUser(authentication);
        if (!new LinkedHashSet<>(getAllowedProjectIds(user)).contains(projectId)) {
            throw new AccessDeniedException("权限不足，无法访问该项目");
        }
    }

    private boolean hasAdminAccess(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> {
                    String a = authority.getAuthority();
                    return ADMIN_AUTHORITY.equals(a) || EXTERNAL_API_AUTHORITY.equals(a);
                });
    }

    private User resolveCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("当前用户未认证");
        }
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new AccessDeniedException("当前用户不存在或不可用"));
    }
}
