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
public class ProjectDocumentDTO {

    private Long id;
    private Long projectId;
    private String name;
    private String size;
    private String fileType;
    private String documentCategory;
    private String linkedEntityType;
    private Long linkedEntityId;
    private String fileUrl;
    private Long uploaderId;
    private String uploader;
    private String time;
    private LocalDateTime createdAt;
}
