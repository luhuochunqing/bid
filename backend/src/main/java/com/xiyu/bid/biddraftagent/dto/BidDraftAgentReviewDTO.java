package com.xiyu.bid.biddraftagent.dto;

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
public class BidDraftAgentReviewDTO {
    private Long runId;
    private Long projectId;
    private String status;
    private String reviewSummary;
    private String draftText;
    private RequirementClassification requirementClassification;
    private MaterialMatchScore materialMatchScore;
    private GapCheckResult gapCheck;
    private ManualConfirmationDecision manualConfirmation;
    private WriteCoverageDecision writeCoverage;
    private List<String> nextActions;
    private LocalDateTime reviewedAt;
}
