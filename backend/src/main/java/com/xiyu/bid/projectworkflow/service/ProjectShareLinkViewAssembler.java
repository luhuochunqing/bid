package com.xiyu.bid.projectworkflow.service;

import com.xiyu.bid.projectworkflow.dto.ProjectShareLinkDTO;
import com.xiyu.bid.projectworkflow.entity.ProjectShareLink;
import org.springframework.stereotype.Component;

@Component
class ProjectShareLinkViewAssembler {

    ProjectShareLinkDTO toDto(ProjectShareLink shareLink) {
        return ProjectShareLinkDTO.builder()
                .id(shareLink.getId())
                .projectId(shareLink.getProjectId())
                .token(shareLink.getToken())
                .url(shareLink.getUrl())
                .createdBy(shareLink.getCreatedBy())
                .createdByName(shareLink.getCreatedByName())
                .expiresAt(shareLink.getExpiresAt())
                .createdAt(shareLink.getCreatedAt())
                .build();
    }
}
