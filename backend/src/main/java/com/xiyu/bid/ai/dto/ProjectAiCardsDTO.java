package com.xiyu.bid.ai.dto;

import com.xiyu.bid.competitionintel.dto.CompetitionAnalysisDTO;
import com.xiyu.bid.compliance.entity.ComplianceCheckResult;
import com.xiyu.bid.roi.dto.ROIAnalysisDTO;
import com.xiyu.bid.scoreanalysis.dto.ScoreAnalysisDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectAiCardsDTO {
    private ScoreAnalysisDTO score;
    private List<CompetitionAnalysisDTO> competition;
    private List<ComplianceCheckResult> compliance;
    private ROIAnalysisDTO roi;
}
