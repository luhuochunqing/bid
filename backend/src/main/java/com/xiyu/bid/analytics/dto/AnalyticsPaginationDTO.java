package com.xiyu.bid.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsPaginationDTO {
    private Integer page;
    private Integer size;
    private Long total;
    private Integer totalPages;
    private Boolean hasNext;
}
