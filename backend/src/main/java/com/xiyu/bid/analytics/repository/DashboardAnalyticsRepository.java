package com.xiyu.bid.analytics.repository;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class DashboardAnalyticsRepository {

    private DashboardAnalyticsRepository() {
    }

    public record OverviewSnapshot(
            Long totalTenders,
            BigDecimal totalBudget,
            Long activeProjects,
            Long pendingTasks,
            Long biddedTenders,
            Long winningProjects
    ) {
    }

    public record MonthlyTrendRow(Integer year, Integer month, Long count, BigDecimal totalValue) {
    }

    public record StatusCountRow(Tender.Status status, Long count) {
    }

    public record SourceAggregateRow(String source, Long bidCount, Long winCount, BigDecimal totalBidAmount) {
    }

    public record RevenueDrillDownRow(
            Long tenderId,
            Long projectId,
            String title,
            String source,
            Tender.Status tenderStatus,
            String projectName,
            Project.Status projectStatus,
            Long managerId,
            String managerName,
            BigDecimal budget,
            Integer score,
            LocalDateTime createdAt,
            LocalDateTime deadline
    ) {
    }

    public record ProjectDrillDownRow(
            Long projectId,
            Long tenderId,
            String projectName,
            String tenderTitle,
            Project.Status projectStatus,
            Long managerId,
            String managerName,
            BigDecimal budget,
            LocalDateTime referenceDate,
            LocalDateTime endDate,
            Integer teamSize
    ) {
    }

    public record ProductLineCandidateRow(
            String title,
            Tender.Status status,
            BigDecimal budget
    ) {
    }

    public record TenderSummaryRow(
            Long tenderId,
            String title,
            String source,
            Tender.Status status,
            BigDecimal budget,
            LocalDateTime createdAt,
            LocalDateTime deadline
    ) {
    }

    public record ProjectSnapshotRow(
            Long projectId,
            Long tenderId,
            String projectName,
            Project.Status projectStatus,
            Long managerId,
            String managerName,
            String tenderSource,
            BigDecimal budget,
            LocalDateTime referenceDate,
            LocalDateTime endDate,
            Long teamMemberId
    ) {
    }

    /**
     * Enhanced snapshot row that includes full team member User objects,
     * eliminating the need for a separate fetchUsersByIds query.
     */
    public record ProjectSnapshotRowWithUsers(
            Long projectId,
            Long tenderId,
            String projectName,
            Project.Status projectStatus,
            Long managerId,
            String managerName,
            String tenderSource,
            BigDecimal budget,
            LocalDateTime referenceDate,
            LocalDateTime endDate,
            Long teamMemberId,
            Long teamMemberUserId,
            String teamMemberFullName,
            String teamMemberUsername,
            String teamMemberRoleProfile
    ) {
    }

    public record TaskSnapshotRow(
            Long projectId,
            Long assigneeId,
            com.xiyu.bid.entity.Task.Status status,
            LocalDateTime dueDate
    ) {
    }

    public record ProjectDocumentRow(
            Long projectId,
            Long documentId,
            String name,
            String uploaderName,
            LocalDateTime createdAt,
            String size
    ) {
    }

    public record DocumentExportRow(
            Long projectId,
            Long exportId,
            String fileName,
            String exportedByName,
            LocalDateTime exportedAt,
            Long fileSize,
            String format
    ) {
    }
}
