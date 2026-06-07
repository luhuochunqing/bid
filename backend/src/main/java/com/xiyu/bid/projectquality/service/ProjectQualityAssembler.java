package com.xiyu.bid.projectquality.service;

import com.xiyu.bid.projectquality.dto.ProjectQualityCheckResponse;
import com.xiyu.bid.projectquality.dto.ProjectQualityIssueResponse;
import com.xiyu.bid.projectquality.entity.ProjectQualityCheck;
import com.xiyu.bid.projectquality.entity.ProjectQualityIssue;

import java.util.List;

final class ProjectQualityAssembler {
    private ProjectQualityAssembler() {
    }

    static ProjectQualityCheckResponse toResponse(ProjectQualityCheck check, List<ProjectQualityIssue> issues) {
        return ProjectQualityCheckResponse.builder()
                .id(check.getId())
                .projectId(check.getProjectId())
                .documentId(check.getDocumentId())
                .documentName(check.getDocumentName())
                .status(check.getStatus())
                .empty(check.isEmpty())
                .summary(check.getSummary())
                .checkedAt(check.getCheckedAt())
                .issues(issues.stream().map(ProjectQualityAssembler::toIssueResponse).toList())
                .build();
    }

    static ProjectQualityIssueResponse toIssueResponse(ProjectQualityIssue issue) {
        return ProjectQualityIssueResponse.builder()
                .id(issue.getId())
                .type(issue.getType())
                .originalText(issue.getOriginalText())
                .suggestionText(issue.getSuggestionText())
                .locationLabel(issue.getLocationLabel())
                .adopted(issue.isAdopted())
                .ignored(issue.isIgnored())
                .build();
    }
}
