package com.xiyu.bid.casework.dto;

import java.util.List;

public record ProjectArchiveStatsResponse(
    Long totalArchives,
    Long closedProjects,
    Long caseCount,
    Long reuseCount,
    List<String> projectManagers,
    List<String> bidManagers
) {
    public ProjectArchiveStatsResponse(Long totalArchives, Long closedProjects, Long caseCount, Long reuseCount) {
        this(totalArchives, closedProjects, caseCount, reuseCount, List.of(), List.of());
    }
}
