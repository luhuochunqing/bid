package com.xiyu.bid.analytics.model;

import com.xiyu.bid.entity.Project;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

public record ProjectSnapshotAggregate(
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
        Set<Long> teamMemberIds
) {

    public ProjectSnapshotAggregate {
        teamMemberIds = teamMemberIds == null ? Set.of() : Set.copyOf(teamMemberIds);
    }

    public static ProjectSnapshotAggregate create(Long projectId, Long tenderId, String projectName,
                                                 Project.Status projectStatus, Long managerId, String managerName,
                                                 String tenderSource, BigDecimal budget,
                                                 LocalDateTime referenceDate, LocalDateTime endDate) {
        return new ProjectSnapshotAggregate(
                projectId,
                tenderId,
                projectName,
                projectStatus,
                managerId,
                managerName,
                tenderSource,
                budget,
                referenceDate,
                endDate,
                new LinkedHashSet<>()
        );
    }

    public ProjectSnapshotAggregate withTeamMember(Long memberId) {
        Set<Long> members = new LinkedHashSet<>(teamMemberIds);
        if (memberId != null) {
            members.add(memberId);
        }
        return new ProjectSnapshotAggregate(projectId, tenderId, projectName, projectStatus, managerId,
                managerName, tenderSource, budget, referenceDate, endDate, members);
    }
}
