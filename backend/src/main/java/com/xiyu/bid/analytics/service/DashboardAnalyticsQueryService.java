package com.xiyu.bid.analytics.service;

import com.xiyu.bid.analytics.model.ProjectSnapshotAggregate;
import com.xiyu.bid.analytics.repository.DashboardAnalyticsRepository;
import com.xiyu.bid.analytics.repository.DashboardAnalyticsReadRepository;
import com.xiyu.bid.entity.RoleProfile;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class DashboardAnalyticsQueryService {

    private final DashboardAnalyticsReadRepository readRepository;
    private final UserRepository userRepository;
    private final ProjectAccessScopeService projectAccessScopeService;

    DashboardAnalyticsRepository.OverviewSnapshot fetchOverviewSnapshot() {
        Set<Long> projectIds = scopedProjectIds();
        return readRepository.fetchOverviewSnapshot(projectIds);
    }

    List<DashboardAnalyticsRepository.MonthlyTrendRow> fetchTenderTrendRows() {
        return readRepository.fetchTenderTrendRows(scopedProjectIds());
    }

    List<DashboardAnalyticsRepository.MonthlyTrendRow> fetchProjectTrendRows() {
        return readRepository.fetchProjectTrendRows(scopedProjectIds());
    }

    List<DashboardAnalyticsRepository.StatusCountRow> fetchStatusCounts() {
        return readRepository.fetchStatusCounts(scopedProjectIds());
    }

    List<DashboardAnalyticsRepository.SourceAggregateRow> fetchSourceAggregateRows(int limit) {
        return readRepository.fetchSourceAggregateRows(scopedProjectIds(), limit);
    }

    List<DashboardAnalyticsRepository.ProductLineCandidateRow> fetchProductLineCandidateRows() {
        return readRepository.fetchProductLineCandidateRows(scopedProjectIds());
    }

    List<DashboardAnalyticsRepository.TenderSummaryRow> fetchTenderSummaryRows() {
        return readRepository.fetchTenderSummaryRows(scopedProjectIds());
    }

    List<ProjectSnapshotAggregate> fetchProjectSnapshotsByTenderIds(Collection<Long> tenderIds) {
        return aggregateProjectSnapshots(readRepository.fetchProjectSnapshotRowsByTenderIds(
                scopedProjectIds(),
                tenderIds
        ));
    }

    List<ProjectSnapshotAggregate> fetchProjectSnapshotsByDateRange(
            java.time.LocalDate startDate,
            java.time.LocalDate endDate
    ) {
        return aggregateProjectSnapshots(readRepository.fetchProjectSnapshotRowsByDateRange(
                scopedProjectIds(),
                startDate,
                endDate
        ));
    }

    List<DashboardAnalyticsRepository.TaskSnapshotRow> fetchTaskSnapshots(Set<Long> projectIds) {
        return readRepository.fetchTaskSnapshotRows(projectIds);
    }

    List<DashboardAnalyticsRepository.ProjectDocumentRow> fetchProjectDocuments(Set<Long> projectIds) {
        return readRepository.fetchProjectDocumentRows(projectIds);
    }

    List<DashboardAnalyticsRepository.DocumentExportRow> fetchDocumentExports(Set<Long> projectIds) {
        return readRepository.fetchDocumentExportRows(projectIds);
    }

    List<DashboardAnalyticsRepository.RevenueDrillDownRow> fetchRevenueDrillDownRows(
            java.time.LocalDate startDate,
            java.time.LocalDate endDate
    ) {
        return readRepository.fetchRevenueDrillDownRows(scopedProjectIds(), startDate, endDate);
    }

    List<DashboardAnalyticsRepository.ProjectDrillDownRow> fetchProjectDrillDownRows(
            java.time.LocalDate startDate,
            java.time.LocalDate endDate
    ) {
        return readRepository.fetchProjectDrillDownRows(scopedProjectIds(), startDate, endDate);
    }

    Map<Long, User> fetchUsersByIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds.stream().filter(Objects::nonNull).toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (left, right) -> left, java.util.LinkedHashMap::new));
    }

    /**
     * Result of optimized project snapshot query with embedded user data.
     * Eliminates N+1 query pattern: fetchProjectSnapshots + collectProjectUserIds + fetchUsersByIds
     * by loading full User data for team members in the same query.
     */
    record ProjectSnapshotWithUsers(
            List<ProjectSnapshotAggregate> projects,
            Map<Long, User> userById
    ) {
    }

    /**
     * Optimized method that fetches project snapshots AND team member User data in a single query.
     * Replaces the previous pattern:
     *   1. fetchProjectSnapshotRowsByTenderIds() - returns only team member IDs
     *   2. collectProjectUserIds() - collects all user IDs in memory
     *   3. fetchUsersByIds() - separate query to load User objects
     *
     * Now we load full User data (fullName, username, roleProfile) directly in the project query.
     */
    ProjectSnapshotWithUsers fetchProjectSnapshotsWithUsersByTenderIds(Collection<Long> tenderIds) {
        List<DashboardAnalyticsRepository.ProjectSnapshotRowWithUsers> rows =
                readRepository.fetchProjectSnapshotRowsWithUsersByTenderIds(scopedProjectIds(), tenderIds);

        Map<Long, ProjectSnapshotAggregate> aggregates = rows.stream()
                .collect(Collectors.toMap(
                        DashboardAnalyticsRepository.ProjectSnapshotRowWithUsers::projectId,
                        this::toProjectSnapshotAggregate,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        Map<Long, User> userById = new LinkedHashMap<>();

        for (DashboardAnalyticsRepository.ProjectSnapshotRowWithUsers row : rows) {
            if (row.teamMemberId() != null) {
                ProjectSnapshotAggregate existing = aggregates.get(row.projectId());
                if (existing != null) {
                    aggregates.put(row.projectId(), existing.withTeamMember(row.teamMemberId()));
                }
            }

            if (row.teamMemberUserId() != null && !userById.containsKey(row.teamMemberUserId())) {
                User user = User.builder()
                        .id(row.teamMemberUserId())
                        .fullName(row.teamMemberFullName())
                        .username(row.teamMemberUsername())
                        .role(User.Role.fromCode(row.teamMemberRoleProfile()))
                        .roleProfile(row.teamMemberRoleProfile() != null ? RoleProfile.builder().code(row.teamMemberRoleProfile()).build() : null)
                        .build();
                userById.put(row.teamMemberUserId(), user);
            }
        }

        return new ProjectSnapshotWithUsers(aggregates.values().stream().toList(), userById);
    }

    /**
     * Optimized method that fetches project snapshots AND team member User data in a single query.
     * See fetchProjectSnapshotsWithUsersByTenderIds for details.
     */
    ProjectSnapshotWithUsers fetchProjectSnapshotsWithUsersByDateRange(
            java.time.LocalDate startDate,
            java.time.LocalDate endDate
    ) {
        List<DashboardAnalyticsRepository.ProjectSnapshotRowWithUsers> rows =
                readRepository.fetchProjectSnapshotRowsWithUsersByDateRange(scopedProjectIds(), startDate, endDate);

        Map<Long, ProjectSnapshotAggregate> aggregates = rows.stream()
                .collect(Collectors.toMap(
                        DashboardAnalyticsRepository.ProjectSnapshotRowWithUsers::projectId,
                        this::toProjectSnapshotAggregate,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        Map<Long, User> userById = new LinkedHashMap<>();

        for (DashboardAnalyticsRepository.ProjectSnapshotRowWithUsers row : rows) {
            if (row.teamMemberId() != null) {
                ProjectSnapshotAggregate existing = aggregates.get(row.projectId());
                if (existing != null) {
                    aggregates.put(row.projectId(), existing.withTeamMember(row.teamMemberId()));
                }
            }

            if (row.teamMemberUserId() != null && !userById.containsKey(row.teamMemberUserId())) {
                User user = User.builder()
                        .id(row.teamMemberUserId())
                        .fullName(row.teamMemberFullName())
                        .username(row.teamMemberUsername())
                        .role(User.Role.fromCode(row.teamMemberRoleProfile()))
                        .roleProfile(row.teamMemberRoleProfile() != null ? RoleProfile.builder().code(row.teamMemberRoleProfile()).build() : null)
                        .build();
                userById.put(row.teamMemberUserId(), user);
            }
        }

        return new ProjectSnapshotWithUsers(aggregates.values().stream().toList(), userById);
    }

    private ProjectSnapshotAggregate toProjectSnapshotAggregate(DashboardAnalyticsRepository.ProjectSnapshotRowWithUsers row) {
        return ProjectSnapshotAggregate.create(
                row.projectId(),
                row.tenderId(),
                row.projectName(),
                row.projectStatus(),
                row.managerId(),
                row.managerName(),
                row.tenderSource(),
                row.budget(),
                row.referenceDate(),
                row.endDate()
        );
    }

    Set<Long> collectProjectUserIds(List<ProjectSnapshotAggregate> projects) {
        Set<Long> userIds = new LinkedHashSet<>();
        for (ProjectSnapshotAggregate project : projects) {
            if (project.managerId() != null) {
                userIds.add(project.managerId());
            }
            userIds.addAll(project.teamMemberIds());
        }
        return userIds;
    }

    private List<ProjectSnapshotAggregate> aggregateProjectSnapshots(
            List<DashboardAnalyticsRepository.ProjectSnapshotRow> rows
    ) {
        Map<Long, ProjectSnapshotAggregate> aggregates = rows.stream()
                .collect(Collectors.toMap(
                        DashboardAnalyticsRepository.ProjectSnapshotRow::projectId,
                        row -> ProjectSnapshotAggregate.create(
                                row.projectId(),
                                row.tenderId(),
                                row.projectName(),
                                row.projectStatus(),
                                row.managerId(),
                                row.managerName(),
                                row.tenderSource(),
                                row.budget(),
                                row.referenceDate(),
                                row.endDate()
                        ),
                        (left, right) -> left
                ));

        for (DashboardAnalyticsRepository.ProjectSnapshotRow row : rows) {
            if (row.teamMemberId() != null) {
                ProjectSnapshotAggregate existing = aggregates.get(row.projectId());
                if (existing != null) {
                    aggregates.put(row.projectId(), existing.withTeamMember(row.teamMemberId()));
                }
            }
        }
        return aggregates.values().stream().toList();
    }

    private Set<Long> scopedProjectIds() {
        if (projectAccessScopeService.currentUserHasAdminAccess()) {
            return null;
        }
        List<Long> allowedIds = projectAccessScopeService.getAllowedProjectIdsForCurrentUser();
        if (allowedIds == null || allowedIds.isEmpty()) {
            return Set.of();
        }
        return allowedIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
