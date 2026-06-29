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
public class BidDocumentQualityAiPreviewDTO {
    private String overallAssessment;
    private List<String> keyRisks;
}
