package com.xiyu.bid.performance.application.dto;

/**
 * 业绩附件上传响应 DTO（CO-442）。
 *
 * @param fileName 原始文件名（用于前端回显）
 * @param fileUrl  磁盘绝对路径（提交保存业绩时回传给后端）
 */
public record PerformanceAttachmentUploadDTO(String fileName, String fileUrl) {
}
