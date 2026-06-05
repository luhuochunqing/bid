package com.xiyu.bid.projectquality.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectQualityIssueResponse {
    private Long id;
    private String type;
    private String originalText;
    private String suggestionText;
    private String locationLabel;
    private boolean adopted;
    private boolean ignored;
}
