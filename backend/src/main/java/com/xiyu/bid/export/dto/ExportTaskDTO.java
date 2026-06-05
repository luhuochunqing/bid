package com.xiyu.bid.export.dto;

import com.xiyu.bid.export.entity.ExportTask;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExportTaskDTO {
    private Long id;
    private ExportTask.ExportType exportType;
    private String dataType;
    private ExportTask.TaskStatus status;
    private String fileName;
    private Long fileSize;
    private String errorMessage;
    private Integer progress;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private LocalDateTime expiresAt;
    private String downloadUrl;
}
