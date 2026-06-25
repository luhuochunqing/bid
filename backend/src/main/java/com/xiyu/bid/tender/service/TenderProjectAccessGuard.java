// Input: tender ids and linked projects
// Output: tender visibility decisions backed by project data permissions
// Pos: Service/权限支撑层
// 维护声明: 仅维护标讯与关联项目的数据权限判断；标讯业务流转留在命令/查询服务。
package com.xiyu.bid.tender.service;

import com.xiyu.bid.admin.service.DataScopeAccessProfile;
import com.xiyu.bid.admin.service.DataScopeConfigService;
import com.xiyu.bid.batch.entity.TenderAssignmentRecord;
import com.xiyu.bid.batch.repository.TenderAssignmentRecordRepository;
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
    private final TenderAssignmentRecordRepository tenderAssignmentRecordRepository;

    void assertCanAccessTender(Tender tender) {
        List<Project> projects = linkedProjects(tender);
        for (Project project : projects) {
            projectAccessScopeService.assertCurrentUserCanAccessProject(project.getId());
        }
        if (projects.isEmpty()) {
            User user = resolveCurrentUser();
            String ds = resolveDataScope(user);
            if ("all".equals(ds)) return;
            if (user == null || !isSelfVisibleTender(tender, user.getId())) {
                throw new org.springframework.security.access.AccessDeniedException("权限不足，无法访问该标讯");
            }
        }
    }

    private boolean isSelfVisibleTender(Tender tender, Long userId) {
        if (isSelfOwnedTender(tender, userId)) {
            return true;
        }
        return tenderAssignmentRecordRepository.findFirstByTenderIdOrderByAssignedAtDesc(tender.getId())
                .map(record -> Objects.equals(record.getAssigneeId(), userId))
                .orElse(false);
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

        if (projectAccessScopeService.currentUserHasAdminAccess()) {
            return tenders;
        }

        Set<Long> allowedProjectIds = new HashSet<>(
                projectAccessScopeService.getAllowedProjectIdsForCurrentUser());

        Set<Long> tenderIds = tenders.stream()
                .map(Tender::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, List<Project>> projectsByTenderId = projectRepository
                .findByTenderIdIn(tenderIds).stream()
                .collect(Collectors.groupingBy(Project::getTenderId));

        Map<Long, TenderAssignmentRecord> latestAssignmentByTenderId = tenderAssignmentRecordRepository
                .findLatestByTenderIds(tenderIds).stream()
                .collect(Collectors.toMap(TenderAssignmentRecord::getTenderId, r -> r, (a, b) -> a));

        User currentUser = resolveCurrentUser();
        String dataScope = resolveDataScope(currentUser);

        return tenders.stream()
                .filter(t -> canAccessViaProjects(
                        t, projectsByTenderId.getOrDefault(t.getId(), List.of()),
                        allowedProjectIds, currentUser, dataScope,
                        latestAssignmentByTenderId.get(t.getId())))
                .toList();
    }

    private boolean canAccessViaProjects(Tender tender, List<Project> projects,
                                         Set<Long> allowedProjectIds,
                                         User currentUser, String dataScope,
                                         TenderAssignmentRecord latestAssignment) {
        if (!projects.isEmpty()) {
            return projects.stream().anyMatch(p -> allowedProjectIds.contains(p.getId()));
        }

        if ("all".equals(dataScope)) {
            return true;
        }

        if (currentUser == null) return false;
        if (isSelfOwnedTender(tender, currentUser.getId())) {
            return true;
        }
        if (latestAssignment != null) {
            return Objects.equals(latestAssignment.getAssigneeId(), currentUser.getId());
        }
        return false;
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
        DataScopeAccessProfile profile = dataScopeConfigService.getAccessProfile(user);
        return profile == null ? "self" : profile.getDataScope();
    }
}
