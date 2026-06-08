package com.xiyu.bid.personnel.application.dto;

import java.util.List;

/**
 * 批量关联证书附件上传结果
 */
public record BatchAttachmentUploadResult(
        int successCount,
        int failedCount,
        List<UnmatchedFileInfo> unmatchedFiles
) {
    public record UnmatchedFileInfo(
            String fileName,
            String reason
    ) {}
}
