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
public class CaseSearchOptionsDTO {

    private List<String> industries;
    private List<String> outcomes;
    private List<String> statuses;
    private List<String> visibilities;
    private List<String> productLines;
    private List<String> tags;
    private List<String> sortOptions;
}
