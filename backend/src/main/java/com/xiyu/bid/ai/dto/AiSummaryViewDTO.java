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
public class AiSummaryViewDTO {
    private Integer winScore;
    private String winLevel;
    private List<AiSummaryRiskDTO> risks;
    private List<String> suggestions;
}
