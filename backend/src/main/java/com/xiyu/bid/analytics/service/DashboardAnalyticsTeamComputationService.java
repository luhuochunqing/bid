package com.xiyu.bid.analytics.service;

import com.xiyu.bid.analytics.model.ProjectSnapshotAggregate;
import com.xiyu.bid.analytics.model.TeamAggregate;
import com.xiyu.bid.analytics.model.TeamTaskAggregate;
import com.xiyu.bid.analytics.repository.DashboardAnalyticsRepository;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
class DashboardAnalyticsTeamComputationService {

    private final DashboardAnalyticsTeamPerformanceService teamPerformanceService;

    DashboardAnalyticsTeamComputationService(DashboardAnalyticsTeamPerformanceService pTeamPerformanceService) {
        this.teamPerformanceService = pTeamPerformanceService;
    }

    Map<Long, TeamTaskAggregate> summarizeTaskRows(
            List<DashboardAnalyticsRepository.TaskSnapshotRow> rows,
            LocalDateTime now
    ) {
        Map<Long, TeamTaskAggregate> taskAggregateByAssignee = new java.util.LinkedHashMap<>();
        for (DashboardAnalyticsRepository.TaskSnapshotRow task : rows) {
            if (task.assigneeId() == null) {
                continue;
            }
            TeamTaskAggregate aggregate = taskAggregateByAssignee.computeIfAbsent(task.assigneeId(), ignored -> new TeamTaskAggregate());
            aggregate.setTotalTaskCount(aggregate.totalTaskCount() + 1);

            if (task.status() == com.xiyu.bid.entity.Task.Status.COMPLETED) {
                aggregate.setCompletedTaskCount(aggregate.completedTaskCount() + 1);
            }

            if (task.dueDate() != null
                    && task.dueDate().isBefore(now)
                    && task.status() != com.xiyu.bid.entity.Task.Status.COMPLETED) {
                aggregate.setOverdueTaskCount(aggregate.overdueTaskCount() + 1);
            }
        }
        return taskAggregateByAssignee;
    }

    Map<Long, TeamAggregate> buildTeamProjectAggregates(
            List<ProjectSnapshotAggregate> projects,
            Map<Long, DashboardAnalyticsRepository.TenderSummaryRow> tenderById
    ) {
        Map<Long, TeamAggregate> aggregates = new java.util.HashMap<>();
        for (ProjectSnapshotAggregate project : projects) {
            DashboardAnalyticsRepository.TenderSummaryRow tender = tenderById.get(project.tenderId());
            BigDecimal amount = normalizeAmount(tender != null ? tender.budget() : project.budget());
            boolean won = tender != null && tender.status() == Tender.Status.WON;
            boolean active = !project.projectStatus().isTerminal();

            accumulateTeamAggregate(project.managerId(), amount, won, active, true, aggregates);
            for (Long memberId : project.teamMemberIds()) {
                if (!java.util.Objects.equals(memberId, project.managerId())) {
                    accumulateTeamAggregate(memberId, amount, won, active, false, aggregates);
                }
            }
        }
        return aggregates;
    }

    int calculatePerformanceScore(TeamAggregate aggregate) {
        return teamPerformanceService.calculatePerformanceScore(aggregate);
    }

    private void accumulateTeamAggregate(
            Long userId,
            BigDecimal amount,
            boolean won,
            boolean active,
            boolean manager,
            Map<Long, TeamAggregate> aggregates
    ) {
        if (userId == null) {
            return;
        }
        TeamAggregate aggregate = aggregates.computeIfAbsent(userId, ignored -> new TeamAggregate());
        aggregate.addProject(active ? Project.Status.BIDDING : Project.Status.LOST, amount, won, active, manager);
    }

    private BigDecimal normalizeAmount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
