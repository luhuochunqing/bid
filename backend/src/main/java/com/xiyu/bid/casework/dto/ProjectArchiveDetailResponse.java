package com.xiyu.bid.casework.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ProjectArchiveDetailResponse(
    Long archiveId,
    String projectName,
    String projectType,
    String projectStatus,
    String bidResult,
    // 基础信息补充
    String tenderAgency,      // 招标主体
    LocalDateTime initiatedAt, // 立项日期（进入INITIATED时间）
    LocalDateTime bidSubmissionAt, // 标书提交日期（进入EVALUATING时间）
    LocalDateTime bidOpeningAt,   // 开标日期
    LocalDateTime closedAt,       // 结项日期（进入CLOSED时间）
    // 关联信息
    String projectManager,
    String bidManager,
    List<ArchiveFileDTO> files,
    List<ArchiveLogDTO> logs
) {
    public record ArchiveFileDTO(
        Long fileId,
        String fileName,
        String category,
        String uploadUser,
        LocalDateTime uploadedAt,
        Long fileSize
    ) {}

    public record ArchiveLogDTO(
        Long logId,
        LocalDateTime time,
        String operator,
        String actionType,
        String content
    ) {}
}
