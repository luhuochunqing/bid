package com.xiyu.bid.casework.domain.model;

/**
 * AI 案例切片推荐的输出（不可变值对象 / 前端 DTO）。
 *
 * @param sliceId      切片 ID
 * @param projectDir   来源项目目录
 * @param docxFile     来源 docx 文件路径
 * @param docxLabel    文件类别
 * @param sectionTitle 章节标题
 * @param textPreview  正文预览
 * @param textLength   正文长度
 * @param paraCount    段落数
 * @param cosineScore  余弦相似度（0~1）
 * @param finalScore   精排后总分（0~100）
 * @param matchReason  匹配理由
 */
public record BidCaseSliceRecommendation(
        Long sliceId,
        String projectDir,
        String docxFile,
        String docxLabel,
        String sectionTitle,
        String textPreview,
        int textLength,
        int paraCount,
        double cosineScore,
        int finalScore,
        String matchReason
) {
}
