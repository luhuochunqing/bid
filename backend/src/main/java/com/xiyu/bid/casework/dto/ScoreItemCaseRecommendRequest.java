package com.xiyu.bid.casework.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreItemCaseRecommendRequest {

    private Long scoreItemId;

    private String scoreItemCode;

    private String keyword;

    @Builder.Default
    private Integer page = 0;

    @Builder.Default
    private Integer size = 10;
}
