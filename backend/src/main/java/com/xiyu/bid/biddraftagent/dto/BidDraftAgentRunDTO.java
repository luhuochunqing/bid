package com.xiyu.bid.biddraftagent.dto;

import com.xiyu.bid.biddraftagent.domain.BidDraftSnapshot;
import com.xiyu.bid.biddraftagent.domain.GapCheckResult;
import com.xiyu.bid.biddraftagent.domain.ManualConfirmationDecision;
import com.xiyu.bid.biddraftagent.domain.MaterialMatchScore;
import com.xiyu.bid.biddraftagent.domain.RequirementClassification;
import com.xiyu.bid.biddraftagent.domain.WriteCoverageDecision;
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
public class BidDraftAgentRunDTO {
    private Long id;
    private Long projectId;
    private Long tenderId;
    private String projectName;
    private String tenderTitle;
    private String status;
    private BidDraftSnapshot snapshot;
    private RequirementClassification requirementClassification;
    private MaterialMatchScore materialMatchScore;
    private GapCheckResult gapCheck;
    private ManualConfirmationDecision manualConfirmation;
    private WriteCoverageDecision writeCoverage;
    private String draftText;
    private String reviewText;
    private List<BidDraftAgentArtifactDTO> artifacts;
    private LocalDateTime reviewedAt;
    private LocalDateTime appliedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
