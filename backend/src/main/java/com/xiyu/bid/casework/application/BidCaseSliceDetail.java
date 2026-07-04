package com.xiyu.bid.casework.application;

import java.time.LocalDateTime;

/**
 * 案例切片详情响应（不可变 DTO）。
 *
 * @param sliceId      切片 ID
 * @param projectDir   来源项目目录
 * @param docxFile     来源 docx 文件路径
 * @param docxLabel    文件类别
 * @param sectionTitle 章节标题
 * @param textPreview  正文预览
 * @param textLength   正文长度
 * @param paraCount    段落数
 * @param createdAt    创建时间
 */
public record BidCaseSliceDetail(
        Long sliceId,
        String projectDir,
        String docxFile,
        String docxLabel,
        String sectionTitle,
        String textPreview,
        int textLength,
        int paraCount,
        LocalDateTime createdAt
) {
}
