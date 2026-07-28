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

    /**
     * 当前用户是否拥有全局数据访问权限（CO-593 工作台截止时间模块用）。
     *
     * <p>覆盖 {@link RoleProfileCatalog#GLOBAL_ACCESS_ROLES}（admin / /bidAdmin / bid-TeamLeader / bid-SystemAdmin）
     * 以及 {@code ROLE_EXTERNAL_API}（外部 API 集成无 User 实体，按管理员语义放行）。</p>
     *
     * <p>区别于 {@link #currentUserHasAdminAccess()}：后者只认 {@code ROLE_ADMIN}，不含投标管理员/组长。</p>
     *
     * <p>角色码解析统一走 {@link EffectiveRoleResolver#resolveRoleCode}（CO-373 规范）。
     * OSS 用户缓存未命中时返回 null —— {@link RoleProfileCatalog#GLOBAL_ACCESS_ROLES} 是不可变 {@code Set.of}
     * 不能接受 null（会抛 NPE），这里显式短路为 false，符合 fail-closed 语义。</p>
     */
    public boolean currentUserHasGlobalAccess() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (hasAdminAccess(authentication)) {
            return true;
        }
        User user = resolveCurrentUser(authentication);
        String roleCode = effectiveRoleResolver.resolveRoleCode(user);
        // hasGlobalAccess 内部做 canonicalCode 归一化 + null 安全短路（fail-closed）
        return RoleProfileCatalog.hasGlobalAccess(roleCode);
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
        // 保留 ROLE_EXTERNAL_API 兼容（外部 API 走 Spring Security authority，无 User 实体）
        if (hasAdminAccess(authentication)) {
            return;
        }

        User user = resolveCurrentUser(authentication);
        if (!canAccessProjectInternal(user, projectId)) {
            throw new AccessDeniedException("权限不足，无法访问该项目");
        }
    }

    /**
     * CO-361: 检查用户是否为项目立项负责人（owner_user_id）。
     * 统一入口，避免多个 Guard/Service 各自查询 ProjectInitiationDetailsRepository。
     */
    @Transactional(readOnly = true)
    public boolean isProjectOwner(Long projectId, Long userId) {
        if (projectId == null || userId == null) {
            return false;
        }
        return initiationDetailsRepository.findByProjectId(projectId)
                .map(details -> userId.equals(details.getOwnerUserId()))
                .orElse(false);
    }

    private boolean hasAdminAccess(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        boolean hasAdminAuthority = authentication.getAuthorities().stream()
                .anyMatch(authority -> {
                    String a = authority.getAuthority();
                    return ADMIN_AUTHORITY.equals(a) || EXTERNAL_API_AUTHORITY.equals(a);
                });
        if (!hasAdminAuthority) {
            return false;
        }
        // ROLE_EXTERNAL_API 是外部 API 集成（无 User 实体），直接放行
        boolean isExternalApi = authentication.getAuthorities().stream()
                .anyMatch(a -> EXTERNAL_API_AUTHORITY.equals(a.getAuthority()));
        if (isExternalApi) {
            return true;
        }
        // §78 深度防御：OSS 用户即使 authorities 含 ROLE_ADMIN 也不能获得 admin 绕过
        // （OSS admin 是其他系统 Home/CRM/SCM 的，不属于本系统；详见 lessons-learned.md §78）
        User user = userRepository.findByUsername(authentication.getName()).orElse(null);
        return user != null && !user.isOssUser();
    }

    private User resolveCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("当前用户未认证");
        }
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new AccessDeniedException("当前用户不存在或不可用"));
    }

    /**
     * Spec 030: 判定指定用户是否可访问指定项目（轻量级单点判定）。
     *
     * <p>用于通知派发接收人过滤：从"按角色反查的候选接收人集合"中剔除"对该项目无访问权"的用户，
     * 避免被通知的用户点击跳转后被 403 拦截（06131 案例 / spec 030）。
     *
     * <p><b>H2 修复（口径对齐）</b>：判定逻辑与 {@link #assertCurrentUserCanAccessProject(Long)}
     * 共享同一私有方法 {@link #canAccessProjectInternal(User, Long)}，确保过滤结果与实际访问判定
     * 不出现分歧（避免 OSS 同步脏数据导致"通知过滤时被剔除但实际能访问"或反向场景）。
     *
     * <p>短路优化：admin/dataScope=all 角色不进入全量计算，避免对管理员 O(N) 查询。
     *
     * @param userId   被判定的用户 id；为 null 时返回 false（防御性）
     * @param projectId 被判定的项目 id；为 null 时返回 false（防御性）
     * @return true 表示该用户可访问该项目；false 表示无权访问或用户/项目不存在
     * @throws RuntimeException 当 {@link #getAllowedProjectIds(User)} 内部查询失败时透传
     *                          （调用方在 Service 层 try-catch 兜底降级）
     */
    @Transactional(readOnly = true)
    public boolean canAccessProject(Long userId, Long projectId) {
        if (userId == null || projectId == null) {
            return false;
        }
        User user = userRepository.findById(userId).orElse(null);
        return canAccessProjectInternal(user, projectId);
    }

    /**
     * Spec 030 H2: 共享判定核心 — 基于 User 对象判定项目可访问性。
     *
     * <p>被 {@link #canAccessProject(Long, Long)}（按 userId 查）、
     * {@link #assertCurrentUserCanAccessProject(Long)}（按 SecurityContext 查）、
     * 以及同包 {@link ProjectAccessFilter#filterUsersByProjectAccess}（批量过滤）共用，
     * 确保三个入口的判定口径完全一致。</p>
     *
     * <p>判定顺序：</p>
     * <ol>
     *   <li>user 或 projectId 为 null → false</li>
     *   <li>EffectiveRoleResolver 解析后的角色码 = admin → true（短路）</li>
     *   <li>dataScope=all → true（短路，覆盖 /bidAdmin / bid-TeamLeader 等）</li>
     *   <li>getAllowedProjectIds(user).contains(projectId) → 该表达式</li>
     * </ol>
     */
    boolean canAccessProjectInternal(User user, Long projectId) {
        if (user == null || projectId == null) {
            return false;
        }
        // admin/dataScope=all 短路：与 getAllowedProjectIds 第一个分支一致
        String effectiveRoleCode = effectiveRoleResolver.resolveRoleCode(user);
        if (RoleProfileCatalog.ADMIN_CODE.equalsIgnoreCase(effectiveRoleCode)) {
            return true;
        }
        DataScopeAccessProfile accessProfile = dataScopeConfigService.getAccessProfile(user);
        if ("all".equals(accessProfile.getDataScope())) {
            return true;
        }
        return new LinkedHashSet<>(getAllowedProjectIds(user)).contains(projectId);
    }
}
