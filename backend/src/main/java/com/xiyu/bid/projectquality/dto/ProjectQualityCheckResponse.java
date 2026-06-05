package com.xiyu.bid.projectquality.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectQualityCheckResponse {
    private Long id;
    private Long projectId;
    private Long documentId;
    private String documentName;
    private String status;
    private boolean empty;
    private String summary;
    private LocalDateTime checkedAt;
    private List<ProjectQualityIssueResponse> issues;
}
