package com.xiyu.bid.documentexport.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class DocumentExportDTO {

    private Long id;
    private Long projectId;
    private Long structureId;
    private String projectName;
    private String format;
    private String fileName;
    private String contentType;
    private Long fileSize;
    private Long exportedBy;
    private String exportedByName;
    private LocalDateTime exportedAt;
    private String content;
}
