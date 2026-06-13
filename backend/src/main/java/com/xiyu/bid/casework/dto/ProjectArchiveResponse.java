package com.xiyu.bid.casework.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record ProjectArchiveResponse(
    Long archiveId,
    Long projectId,
    String projectName,
    String projectType,
    String projectStatus,
    String bidResult,
    String purchaserName,
    Integer fileCount,
    Map<String, Long> fileCategoryDetails,
    LocalDateTime lastUploadedAt,
    String projectManager,
    String bidManager
) {}
