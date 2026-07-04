package com.xiyu.bid.casework.dto;

import java.time.LocalDateTime;

/**
 * CO-496: 文档分类下载文件视图行数据。
 */
public record ArchiveFileListItem(
        Long fileId,
        Long projectId,
        String projectName,
        String projectType,
        String projectStatus,
        String fileName,
        String documentCategory,
        String projectManager,
        String bidManager,
        String uploaderName,
        Long fileSize,
        LocalDateTime uploadedAt
) {}
