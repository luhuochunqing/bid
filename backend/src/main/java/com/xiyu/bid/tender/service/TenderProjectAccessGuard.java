// Input: tender ids and linked projects
// Output: tender visibility decisions backed by project data permissions
// Pos: Service/权限支撑层
// 维护声明: 仅维护标讯与关联项目的数据权限判断；标讯业务流转留在命令/查询服务。
package com.xiyu.bid.tender.service;

import com.xiyu.bid.admin.service.DataScopeConfigService;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
class TenderProjectAccessGuard {

    private final ProjectRepository projectRepository;
    private final ProjectAccessScopeService projectAccessScopeService;
    private final DataScopeConfigService dataScopeConfigService;
    private final UserRepository userRepository;

    void assertCanAccessTender(Tender tender) {
        List<Project> projects = linkedProjects(tender);
        for (Project project : projects) {
            projectAccessScopeService.assertCurrentUserCanAccessProject(project.getId());
        }
        // 未关联项目时，按 dataScope 判断
        if (projects.isEmpty()) {
            User user = resolveCurrentUser();
            String ds = resolveDataScope(user);
            if ("all".equals(ds)) return;
            // "self" 范围 → 仅自己创建/负责的标讯
            if (user == null || !isSelfOwnedTender(tender, user.getId())) {
                throw new org.springframework.security.access.AccessDeniedException("权限不足，无法访问该标讯");
            }
        }
    }

    private static boolean isSelfOwnedTender(Tender tender, Long userId) {
        return userId.equals(tender.getCreatorId())
                || userId.equals(tender.getBiddingPersonId())
                || userId.equals(tender.getProjectManagerId());
    }

    /**
     * 批量过滤可见标讯。一次加载用户可见项目ID + 一次批量加载关联项目，消除 N+1。
     */
    List<Tender> filterVisibleTenders(List<Tender> tenders) {
        if (tenders.isEmpty()) return tenders;

        // Admin 全可见
        if (projectAccessScopeService.currentUserHasAdminAccess()) {
            return tenders;
        }

        // 1 次查询：预加载当前用户可见的项目ID
        Set<Long> allowedProjectIds = new HashSet<>(
                projectAccessScopeService.getAllowedProjectIdsForCurrentUser());

        // 1 次查询：批量加载所有关联项目
        Set<Long> tenderIds = tenders.stream()
                .map(Tender::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, List<Project>> projectsByTenderId = projectRepository
                .findByTenderIdIn(tenderIds).stream()
                .collect(Collectors.groupingBy(Project::getTenderId));

        // 获取当前用户信息（用于未分配标讯的自过滤）
        User currentUser = resolveCurrentUser();
        String dataScope = resolveDataScope(currentUser);

        // 内存过滤
        return tenders.stream()
                .filter(t -> canAccessViaProjects(
                        t, projectsByTenderId.getOrDefault(t.getId(), List.of()),
                        allowedProjectIds, currentUser, dataScope))
                .toList();
    }

    private boolean canAccessViaProjects(Tender tender, List<Project> projects,
                                         Set<Long> allowedProjectIds,
                                         User currentUser, String dataScope) {
        // 有关联项目 → 走已有项目权限判断
        if (!projects.isEmpty()) {
            return projects.stream().anyMatch(p -> allowedProjectIds.contains(p.getId()));
        }

        // 无关联 project = 未分配阶段，按 dataScope 过滤
        if ("all".equals(dataScope)) {
            return true;
        }

        // "self" 范围或兜底 → 仅自己创建/负责的标讯可见
        if (currentUser == null) return false;
        return isSelfOwnedTender(tender, currentUser.getId());
        // "dept"/"deptAndSub" — Tender 缺少 departmentCode 字段，暂无法按部门过滤
    }

    private boolean canAccessTender(Tender tender) {
        try {
            assertCanAccessTender(tender);
            return true;
        } catch (org.springframework.security.access.AccessDeniedException exception) {
            return false;
        }
    }

    private List<Project> linkedProjects(Tender tender) {
        if (tender == null || tender.getId() == null) {
            return List.of();
        }
        List<Project> projects = projectRepository.findByTenderId(tender.getId());
        return projects == null ? List.of() : projects;
    }

    private User resolveCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        return userRepository.findByUsername(authentication.getName()).orElse(null);
    }

    private String resolveDataScope(User user) {
        if (user == null) return "self";
        DataScopeConfigService.AccessProfile profile = dataScopeConfigService.getAccessProfile(user);
        return profile == null ? "self" : profile.getDataScope();
    }
}
