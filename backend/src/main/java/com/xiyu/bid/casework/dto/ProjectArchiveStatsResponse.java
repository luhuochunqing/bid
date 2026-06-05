package com.xiyu.bid.casework.dto;

public record ProjectArchiveStatsResponse(
    Long totalArchives,
    Long closedProjects,
    Long caseCount,
    Long reuseCount
) {}
