package com.xiyu.bid.casework.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreItemCaseRecommendResponse {

    private long total;

    private List<Item> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private Long id;
        private Long sourceProjectId;
        private String sourceProjectName;
        private String scoringPointTitle;
        private String scoringCategory;
        private Integer matchScore;
        private String matchLabel;
        private List<String> matchReasons;
        private List<String> matchedKeywords;
        private String requirementRaw;
        private String responseText;
        private String responseTextSummary;
        private String customerType;
        private String projectType;
        private String bidResult;
        private Integer reuseCount;
        private Boolean isPinned;
    }
}
