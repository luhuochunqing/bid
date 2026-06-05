package com.xiyu.bid.analytics.service;

import com.xiyu.bid.analytics.dto.AnalyticsDrillDownFileDTO;
import com.xiyu.bid.analytics.dto.AnalyticsDrillDownProjectDTO;
import com.xiyu.bid.analytics.dto.AnalyticsDrillDownRowDTO;
import com.xiyu.bid.analytics.dto.AnalyticsDrillDownTeamDTO;
import com.xiyu.bid.analytics.dto.AnalyticsDrillDownSummaryDTO;
import com.xiyu.bid.analytics.model.ProjectSnapshotAggregate;
import com.xiyu.bid.analytics.model.TeamAggregate;
import com.xiyu.bid.analytics.model.TeamTaskAggregate;
import com.xiyu.bid.analytics.repository.DashboardAnalyticsRepository;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Task;
import com.xiyu.bid.entity.User;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;

@Component
class DashboardAnalyticsDrillDownContentAssemblerService {

    private final DashboardAnalyticsTeamPerformanceService teamPerformanceService;
    private final DashboardAnalyticsDrillDownContentSupport contentSupport;

    DashboardAnalyticsDrillDownContentAssemblerService(
            DashboardAnalyticsTeamPerformanceService pTeamPerformanceService,
            DashboardAnalyticsDrillDownContentSupport pContentSupport
    ) {
        this.teamPerformanceService = pTeamPerformanceService;
        this.contentSupport = pContentSupport;
    }

    List<AnalyticsDrillDownProjectDTO> buildProjectItems(
            List<ProjectSnapshotAggregate> projects,
            Map<Long, User> userById
    ) {
        return projects.stream()
                .map(project -> AnalyticsDrillDownProjectDTO.builder()
                        .id(project.projectId())
                        .name(project.projectName())
                        .customer(contentSupport.defaultString(project.tenderSource(), "-"))
                        .budget(contentSupport.defaultAmount(project.budget()))
                        .status(project.projectStatus() == null ? "-" : project.projectStatus().name().toLowerCase(java.util.Locale.ROOT))
                        .manager(contentSupport.resolveDisplayName(userById.get(project.managerId()), project.managerName(), project.managerId()))
                        .result(contentSupport.resolveProjectResult(project.projectStatus()))
                        .build())
                .toList();
    }

    List<AnalyticsDrillDownTeamDTO> buildDrillDownTeamItems(
            List<ProjectSnapshotAggregate> projects,
            List<DashboardAnalyticsRepository.TaskSnapshotRow> tasks,
            Map<Long, User> userById
    ) {
        Map<Long, List<DashboardAnalyticsRepository.TaskSnapshotRow>> tasksByAssignee = tasks.stream()
                .filter(task -> task.assigneeId() != null)
                .collect(Collectors.groupingBy(DashboardAnalyticsRepository.TaskSnapshotRow::assigneeId));

        List<Long> teamMemberIds = projects.stream()
                .flatMap(project -> {
                    List<Long> members = new ArrayList<>(project.teamMemberIds());
                    if (project.managerId() != null) {
                        members.add(project.managerId());
                    }
                    return members.stream();
                })
                .distinct()
                .toList();

        return teamMemberIds.stream()
                .map(userId -> {
                    User user = userById.get(userId);
                    long participation = tasksByAssignee.getOrDefault(userId, emptyList()).size();
                    long completedWins = tasksByAssignee.getOrDefault(userId, emptyList()).stream()
                            .filter(task -> task.status() == Task.Status.COMPLETED)
                            .count();
                    double winRate = participation > 0 ? (completedWins * 100.0) / participation : 0.0;
                    return AnalyticsDrillDownTeamDTO.builder()
                            .name(contentSupport.resolveDisplayName(user, null, userId))
                            .role(user != null ? user.getRoleCode().toLowerCase(java.util.Locale.ROOT) : "member")
                            .dept("-")
                            .participation(participation)
                            .winRate(winRate)
                            .build();
                })
                .toList();
    }

    List<AnalyticsDrillDownFileDTO> buildFileItems(
            List<ProjectSnapshotAggregate> projects,
            List<DashboardAnalyticsRepository.ProjectDocumentRow> projectDocuments,
            List<DashboardAnalyticsRepository.DocumentExportRow> documentExports
    ) {
        Map<Long, ProjectSnapshotAggregate> projectById = projects.stream()
                .collect(Collectors.toMap(ProjectSnapshotAggregate::projectId, Function.identity(), (left, right) -> left, java.util.LinkedHashMap::new));
        List<AnalyticsDrillDownFileDTO> files = new ArrayList<>();
        for (DashboardAnalyticsRepository.ProjectDocumentRow document : projectDocuments) {
            ProjectSnapshotAggregate project = projectById.get(document.projectId());
            if (project != null) {
                files.add(AnalyticsDrillDownFileDTO.builder()
                        .id("doc-" + document.documentId())
                        .name(document.name())
                        .project(project.projectName())
                        .uploader(document.uploaderName())
                        .uploadTime(document.createdAt() == null ? null : document.createdAt().toString())
                        .size(document.size())
                        .build());
            }
        }
        for (DashboardAnalyticsRepository.DocumentExportRow export : documentExports) {
            ProjectSnapshotAggregate project = projectById.get(export.projectId());
            if (project != null) {
                files.add(AnalyticsDrillDownFileDTO.builder()
                        .id("export-" + export.exportId())
                        .name(export.fileName())
                        .project(project.projectName())
                        .uploader(export.exportedByName())
                        .uploadTime(export.exportedAt() == null ? null : export.exportedAt().toString())
                        .size(export.fileSize() == null ? "-" : export.fileSize() + "B")
                        .build());
            }
        }
        return files.stream()
                .sorted(Comparator.comparing(AnalyticsDrillDownFileDTO::getUploadTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    List<AnalyticsDrillDownRowDTO> toRevenueDrillDownRows(
            List<DashboardAnalyticsRepository.RevenueDrillDownRow> rows
    ) {
        return rows.stream()
                .map(row -> AnalyticsDrillDownRowDTO.builder()
                        .id(row.tenderId())
                        .relatedId(row.projectId())
                        .title(row.title())
                        .subtitle(contentSupport.defaultString(row.source(), "未知来源"))
                        .status(row.tenderStatus() == null ? null : row.tenderStatus().name())
                        .ownerName(contentSupport.defaultString(row.projectName(), "未关联项目"))
                        .amount(contentSupport.defaultAmount(row.budget()))
                        .score(row.score())
                        .createdAt(row.createdAt())
                        .deadline(row.deadline())
                        .build())
                .sorted((left, right) -> {
                    int amountCompare = Comparator.nullsLast(BigDecimal::compareTo)
                            .compare(right.getAmount(), left.getAmount());
                    if (amountCompare != 0) {
                        return amountCompare;
                    }
                    return Comparator.nullsLast(LocalDateTime::compareTo)
                            .compare(right.getCreatedAt(), left.getCreatedAt());
                })
                .toList();
    }

    List<AnalyticsDrillDownRowDTO> toProjectDrillDownRows(
            List<DashboardAnalyticsRepository.ProjectDrillDownRow> rows
    ) {
        return rows.stream()
                .map(row -> AnalyticsDrillDownRowDTO.builder()
                        .id(row.projectId())
                        .relatedId(row.tenderId())
                        .title(row.projectName())
                        .subtitle(contentSupport.defaultString(row.tenderTitle(), "未关联标讯"))
                        .status(row.projectStatus() == null ? null : row.projectStatus().name())
                        .ownerName(contentSupport.defaultString(row.managerName(), contentSupport.fallbackUserName(row.managerId())))
                        .amount(contentSupport.defaultAmount(row.budget()))
                        .teamSize(row.teamSize())
                        .createdAt(row.referenceDate())
                        .deadline(row.endDate())
                        .build())
                .sorted(Comparator.comparing(AnalyticsDrillDownRowDTO::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                .toList();
    }

    List<AnalyticsDrillDownRowDTO> toTeamDrillDownRows(
            Map<Long, TeamAggregate> aggregates,
            Map<Long, User> userById,
            Map<Long, TeamTaskAggregate> taskByAssignee
    ) {
        return aggregates.entrySet().stream()
                .map(entry -> {
                    Long userId = entry.getKey();
                    TeamAggregate aggregate = entry.getValue();
                    TeamTaskAggregate taskAggregate = taskByAssignee.getOrDefault(userId, TeamTaskAggregate.empty());
                    aggregate.setTaskMetrics(taskAggregate.totalTaskCount(), taskAggregate.completedTaskCount(), taskAggregate.overdueTaskCount());
                    double winRate = aggregate.projectCount() == 0
                            ? 0.0 : (aggregate.wonCount() * 100.0) / aggregate.projectCount();
                    double taskCompletionRate = aggregate.totalTaskCount() == 0
                            ? 0.0 : (aggregate.completedTaskCount() * 100.0) / aggregate.totalTaskCount();
                    int performanceScore = teamPerformanceService.calculatePerformanceScore(aggregate);
                    User user = userById.get(userId);
                    return AnalyticsDrillDownRowDTO.builder()
                            .id(userId)
                            .title(contentSupport.resolveDisplayName(user, null, userId))
                            .subtitle(user != null ? user.getEmail() : "-")
                            .role(user != null ? user.getRoleCode().toUpperCase(java.util.Locale.ROOT) : "UNKNOWN")
                            .count(aggregate.projectCount())
                            .wonCount(aggregate.wonCount())
                            .activeProjectCount(aggregate.activeProjectCount())
                            .managedProjectCount(aggregate.managedProjectCount())
                            .totalTaskCount(aggregate.totalTaskCount())
                            .completedTaskCount(aggregate.completedTaskCount())
                            .overdueTaskCount(aggregate.overdueTaskCount())
                            .rate(winRate)
                            .taskCompletionRate(taskCompletionRate)
                            .amount(aggregate.totalAmount())
                            .score(performanceScore)
                            .teamSize(Math.toIntExact(Math.max(0, aggregate.managedProjectCount())))
                            .build();
                })
                .sorted((left, right) -> {
                    int scoreCompare = Comparator.nullsLast(Integer::compareTo).compare(right.getScore(), left.getScore());
                    if (scoreCompare != 0) {
                        return scoreCompare;
                    }
                    return Comparator.nullsLast(Double::compareTo).compare(right.getRate(), left.getRate());
                })
                .toList();
    }

    AnalyticsDrillDownSummaryDTO buildTeamSummary(
            List<AnalyticsDrillDownRowDTO> filteredRows,
            List<ProjectSnapshotAggregate> filteredProjects,
            Map<Long, DashboardAnalyticsRepository.TenderSummaryRow> tenderById
    ) {
        return contentSupport.buildTeamSummary(filteredRows, filteredProjects, tenderById);
    }

    String resolveDisplayName(User user, String fallbackName, Long userId) {
        return contentSupport.resolveDisplayName(user, fallbackName, userId);
    }

    String resolveProjectResult(Project.Status status) {
        return contentSupport.resolveProjectResult(status);
    }
}
