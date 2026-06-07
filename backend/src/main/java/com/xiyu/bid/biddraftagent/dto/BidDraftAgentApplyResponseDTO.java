package com.xiyu.bid.biddraftagent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BidDraftAgentApplyResponseDTO {
    private Long runId;
    private Long projectId;
    private Long artifactId;
    private String artifactType;
    private String status;
    private boolean readyForWriter;
    private String handoffTarget;
    private Long structureId;
    private Boolean structureCreated;
    private Integer totalSections;
    private Integer createdSections;
    private Integer updatedSections;
    private Integer skippedSectionsCount;
    @Builder.Default
    private List<BidDraftAgentSkippedSectionDTO> skippedSections = List.of();
    private String message;
}
