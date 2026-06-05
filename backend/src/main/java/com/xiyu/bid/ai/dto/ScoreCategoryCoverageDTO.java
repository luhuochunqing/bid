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
public class ScoreCategoryCoverageDTO {
    private String name;
    private Integer weight;
    private Integer covered;
    private Integer total;
    private Integer percentage;
    private List<String> gaps;
}
