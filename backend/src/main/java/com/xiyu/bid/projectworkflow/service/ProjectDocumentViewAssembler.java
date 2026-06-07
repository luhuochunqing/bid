package com.xiyu.bid.projectworkflow.service;

import com.xiyu.bid.projectworkflow.dto.ProjectDocumentDTO;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component
class ProjectDocumentViewAssembler {

    private static final DateTimeFormatter DISPLAY_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.CHINA);

    ProjectDocumentDTO toDto(ProjectDocument document) {
        return ProjectDocumentDTO.builder()
                .id(document.getId())
                .projectId(document.getProjectId())
                .name(document.getName())
                .size(document.getSize())
                .fileType(document.getFileType())
                .documentCategory(document.getDocumentCategory())
                .linkedEntityType(document.getLinkedEntityType())
                .linkedEntityId(document.getLinkedEntityId())
                .fileUrl(document.getFileUrl())
                .uploaderId(document.getUploaderId())
                .uploader(document.getUploaderName())
                .time(document.getCreatedAt() != null ? document.getCreatedAt().format(DISPLAY_TIME) : "")
                .createdAt(document.getCreatedAt())
                .build();
    }
}
