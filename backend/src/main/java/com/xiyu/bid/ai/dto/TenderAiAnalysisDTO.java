package com.xiyu.bid.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenderAiAnalysisDTO {
    private Long tenderId;
    private Integer winScore;
    private String suggestion;
    private List<DimensionScoreViewDTO> dimensionScores;
    private List<AiRiskItemDTO> risks;
    private List<AiAutoTaskDTO> autoTasks;
}
