package com.xiyu.bid.biddraftagent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BidDraftAgentArtifactDTO {
    private Long id;
    private Long runId;
    private String artifactType;
    private String title;
    private String content;
    private String handoffTarget;
    private String status;
    private LocalDateTime appliedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
