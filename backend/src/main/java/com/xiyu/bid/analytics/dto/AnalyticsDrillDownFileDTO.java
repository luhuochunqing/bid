package com.xiyu.bid.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsDrillDownFileDTO {
    private String id;
    private String name;
    private String project;
    private String uploader;
    private String uploadTime;
    private String size;
}
