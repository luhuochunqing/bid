package com.xiyu.bid.analytics.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * M1 趋势分析下钻响应（PRD 6.6）。
 */
@Data
@Builder
public class TrendDrillDownResponse {
    private List<DrillDownItem> items;
    private DrillDownSummary summary;
    private Pagination pagination;

    @Data
    @Builder
    public static class DrillDownItem {
        private Long projectId;
        private String projectName;
        private String managerName;
        private String techLeaderName;
        private LocalDateTime openTime;
        private String status;
    }

    @Data
    @Builder
    public static class DrillDownSummary {
        private Long totalCount;
        private Long totalBids;
        private Long totalWins;
        private Double winRate;
    }

    @Data
    @Builder
    public static class Pagination {
        private Integer page;
        private Integer size;
        private Long total;
        private Integer totalPages;
    }
}
