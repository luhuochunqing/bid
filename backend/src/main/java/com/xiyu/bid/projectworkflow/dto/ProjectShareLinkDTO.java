package com.xiyu.bid.projectworkflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectShareLinkDTO {

    private Long id;
    private Long projectId;
    private String token;
    private String url;
    private Long createdBy;
    private String createdByName;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
}
