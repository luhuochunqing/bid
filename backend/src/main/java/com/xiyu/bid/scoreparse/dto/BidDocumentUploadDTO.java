package com.xiyu.bid.scoreparse.dto;

/**
 * 投标文件上传响应（contracts/score-parse-api.md §4）。
 */
public record BidDocumentUploadDTO(
        Long documentId,
        String fileUrl
) {
}
