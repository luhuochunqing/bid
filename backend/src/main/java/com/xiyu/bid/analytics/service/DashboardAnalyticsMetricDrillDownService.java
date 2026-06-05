// Input: Dashboard analytics query, computation, and assembler services
// Output: Metric drill-down response DTOs
// Pos: Service/指标下钻编排层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.analytics.service;

import com.xiyu.bid.analytics.dto.AnalyticsDrillDownResponseDTO;
import com.xiyu.bid.analytics.dto.AnalyticsDrillDownRowDTO;
import com.xiyu.bid.analytics.dto.AnalyticsDrillDownSummaryDTO;
import com.xiyu.bid.analytics.dto.AnalyticsFilterDimensionDTO;
import com.xiyu.bid.analytics.model.ProjectSnapshotAggregate;
import com.xiyu.bid.analytics.model.TeamAggregate;
import com.xiyu.bid.analytics.model.TeamTaskAggregate;
import com.xiyu.bid.analytics.repository.DashboardAnalyticsRepository;
import com.xiyu.bid.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class DashboardAnalyticsMetricDrillDownService {

    private final DashboardAnalyticsQueryService queryService;
    private final DashboardAnalyticsComputationService computationService;
    private final DashboardAnalyticsAssemblerService assemblerService;

    @Transactional(readOnly = true)
    AnalyticsDrillDownResponseDTO getRevenueDrillDown(
            String status,
            LocalDate startDate,
            LocalDate endDate,
            Integer page,
            Integer size
    ) {
        List<AnalyticsDrillDownRowDTO> baseRows =
                assemblerService.toRevenueDrillDownRows(queryService.fetchRevenueDrillDownRows(startDate, endDate));
        List<AnalyticsDrillDownRowDTO> filteredRows = baseRows.stream()
                .filter(row -> assemblerService.matchesFilter(row.getStatus(), status))
                .toList();

        return buildMetricResponse(
                "revenue",
                "中标金额明细",
                startDate,
                endDate,
                List.of(assemblerService.buildDimension(
                        "status",
                        "状态",
                        status,
                        baseRows,
                        AnalyticsDrillDownRowDTO::getStatus,
                        assemblerService::translateTenderStatus
                )),
                filteredRows,
                page,
                size,
                AnalyticsDrillDownSummaryDTO.builder()
                        .totalCount((long) filteredRows.size())
                        .totalAmount(assemblerService.sumAmounts(filteredRows))
                        .build()
        );
    }

    @Transactional(readOnly = true)
    AnalyticsDrillDownResponseDTO getWinRateDrillDown(
            String outcome,
            LocalDate startDate,
            LocalDate endDate,
            Integer page,
            Integer size
    ) {
        List<DashboardAnalyticsRepository.TenderSummaryRow> filteredTenders = computationService.filterTenderRowsByDateRange(
                queryService.fetchTenderSummaryRows(),
                startDate,
                endDate
        );
        Map<Long, DashboardAnalyticsRepository.TenderSummaryRow> tenderById = filteredTenders.stream()
                .collect(Collectors.toMap(
                        DashboardAnalyticsRepository.TenderSummaryRow::tenderId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Map<Long, ProjectSnapshotAggregate> projectByTenderId = queryService.fetchProjectSnapshotsByTenderIds(tenderById.keySet()).stream()
                .collect(Collectors.toMap(ProjectSnapshotAggregate::tenderId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        List<AnalyticsDrillDownRowDTO> baseRows = buildWinRateRows(filteredTenders, projectByTenderId);
        List<AnalyticsDrillDownRowDTO> filteredRows = baseRows.stream()
                .filter(row -> assemblerService.matchesFilter(row.getOutcome(), outcome))
                .toList();
        long wonCount = filteredRows.stream().filter(row -> "WON".equals(row.getOutcome())).count();

        return buildMetricResponse(
                "win-rate",
                "中标率明细",
                startDate,
                endDate,
                List.of(assemblerService.buildDimension(
                        "outcome",
                        "结果",
                        outcome,
                        baseRows,
                        AnalyticsDrillDownRowDTO::getOutcome,
                        assemblerService::translateOutcome
                )),
                filteredRows,
                page,
                size,
                AnalyticsDrillDownSummaryDTO.builder()
                        .totalCount((long) filteredRows.size())
                        .totalAmount(assemblerService.sumAmounts(filteredRows))
                        .wonCount(wonCount)
                        .winRate(filteredRows.isEmpty() ? 0.0 : (wonCount * 100.0) / filteredRows.size())
                        .build()
        );
    }

    @Transactional(readOnly = true)
    AnalyticsDrillDownResponseDTO getTeamDrillDown(
            String role,
            LocalDate startDate,
            LocalDate endDate,
            Integer page,
            Integer size
    ) {
        Map<Long, DashboardAnalyticsRepository.TenderSummaryRow> tenderById = queryService.fetchTenderSummaryRows().stream()
                .collect(Collectors.toMap(
                        DashboardAnalyticsRepository.TenderSummaryRow::tenderId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        // Prefer optimized path: project snapshot + embedded team member user data.
        DashboardAnalyticsQueryService.ProjectSnapshotWithUsers snapshotWithUsers =
                queryService.fetchProjectSnapshotsWithUsersByDateRange(startDate, endDate);
        List<ProjectSnapshotAggregate> filteredProjects = snapshotWithUsers.projects();
        Map<Long, User> userById = snapshotWithUsers.userById();
        // Fallback to legacy path when optimized query fails to hydrate team members
        // (e.g., element-collection join portability differences).
        if (hasNoTeamMembers(filteredProjects) && !filteredProjects.isEmpty()) {
            filteredProjects = queryService.fetchProjectSnapshotsByDateRange(startDate, endDate);
            userById = queryService.fetchUsersByIds(queryService.collectProjectUserIds(filteredProjects));
        }

        Map<Long, TeamTaskAggregate> taskByAssignee = computationService.summarizeTaskRows(
                queryService.fetchTaskSnapshots(collectProjectIds(filteredProjects)),
                LocalDateTime.now()
        );
        Map<Long, TeamAggregate> aggregates = computationService.buildTeamProjectAggregates(filteredProjects, tenderById);
        List<AnalyticsDrillDownRowDTO> baseRows = assemblerService.toTeamDrillDownRows(aggregates, userById, taskByAssignee);
        List<AnalyticsDrillDownRowDTO> filteredRows = baseRows.stream()
                .filter(row -> assemblerService.matchesFilter(row.getRole(), role))
                .toList();

        return buildMetricResponse(
                "team",
                "人员绩效明细",
                startDate,
                endDate,
                List.of(assemblerService.buildDimension(
                        "role",
                        "角色",
                        role,
                        baseRows,
                        AnalyticsDrillDownRowDTO::getRole,
                        assemblerService::translateUserRole
                )),
                filteredRows,
                page,
                size,
                assemblerService.buildTeamSummary(filteredRows, filteredProjects, tenderById)
        );
    }

    private boolean hasNoTeamMembers(List<ProjectSnapshotAggregate> projects) {
        return projects.stream().noneMatch(project -> !project.teamMemberIds().isEmpty());
    }

    @Transactional(readOnly = true)
    AnalyticsDrillDownResponseDTO getProjectDrillDown(
            String status,
            LocalDate startDate,
            LocalDate endDate,
            Integer page,
            Integer size
    ) {
        List<AnalyticsDrillDownRowDTO> baseRows =
                assemblerService.toProjectDrillDownRows(queryService.fetchProjectDrillDownRows(startDate, endDate));
        List<AnalyticsDrillDownRowDTO> filteredRows = baseRows.stream()
                .filter(row -> assemblerService.matchesProjectStatusFilter(row.getStatus(), status))
                .toList();

        return buildMetricResponse(
                "projects",
                "进行中项目明细",
                startDate,
                endDate,
                List.of(assemblerService.buildProjectStatusDimension(status, baseRows)),
                filteredRows,
                page,
                size,
                AnalyticsDrillDownSummaryDTO.builder()
                        .totalCount((long) filteredRows.size())
                        .totalAmount(assemblerService.sumAmounts(filteredRows))
                        .activeCount(filteredRows.stream().filter(row -> !"WON".equals(row.getStatus()) && !"LOST".equals(row.getStatus()) && !"FAILED".equals(row.getStatus()) && !"ABANDONED".equals(row.getStatus())).count())
                        .build()
        );
    }

    private AnalyticsDrillDownResponseDTO buildMetricResponse(
            String metricKey,
            String metricLabel,
            LocalDate startDate,
            LocalDate endDate,
            List<AnalyticsFilterDimensionDTO> dimensions,
            List<AnalyticsDrillDownRowDTO> filteredRows,
            Integer page,
            Integer size,
            AnalyticsDrillDownSummaryDTO summary
    ) {
        return assemblerService.buildMetricDrillDownResponse(
                metricKey,
                metricLabel,
                startDate,
                endDate,
                dimensions,
                filteredRows,
                page,
                size,
                summary
        );
    }

    private List<AnalyticsDrillDownRowDTO> buildWinRateRows(
            List<DashboardAnalyticsRepository.TenderSummaryRow> tenders,
            Map<Long, ProjectSnapshotAggregate> projectByTenderId
    ) {
        return tenders.stream()
                .map(tender -> buildWinRateRow(tender, projectByTenderId.get(tender.tenderId())))
                .sorted((left, right) -> {
                    int createdCompare = Comparator.nullsLast(LocalDateTime::compareTo).compare(right.getCreatedAt(), left.getCreatedAt());
                    if (createdCompare != 0) {
                        return createdCompare;
                    }
                    return Comparator.nullsLast(BigDecimal::compareTo).compare(right.getAmount(), left.getAmount());
                })
                .toList();
    }

    private AnalyticsDrillDownRowDTO buildWinRateRow(
            DashboardAnalyticsRepository.TenderSummaryRow tender,
            ProjectSnapshotAggregate project
    ) {
        String outcome = computationService.deriveOutcome(tender.status(), project);
        return AnalyticsDrillDownRowDTO.builder()
                .id(tender.tenderId())
                .relatedId(project != null ? project.projectId() : null)
                .title(tender.title())
                .subtitle(project != null ? project.projectName() : "未形成项目")
                .status(project != null && project.projectStatus() != null ? project.projectStatus().name() : tender.status().name())
                .outcome(outcome)
                .ownerName(project != null ? assemblerService.resolveDisplayName(null, project.managerName(), project.managerId()) : "-")
                .amount(tender.budget() == null ? BigDecimal.ZERO : tender.budget())
                .rate("WON".equals(outcome) ? 100.0 : 0.0)
                .createdAt(tender.createdAt())
                .deadline(tender.deadline())
                .build();
    }

    private Set<Long> collectProjectIds(List<ProjectSnapshotAggregate> projects) {
        return projects.stream()
                .map(ProjectSnapshotAggregate::projectId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
