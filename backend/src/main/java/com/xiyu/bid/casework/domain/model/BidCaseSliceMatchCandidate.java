package com.xiyu.bid.casework.domain.model;

/**
 * 精排策略使用的候选切片（不可变值对象）。
 *
 * @param id          切片 ID
 * @param projectDir  来源项目目录
 * @param docxFile    来源 docx 文件路径
 * @param docxLabel   文件类别
 * @param title       章节标题
 * @param textPreview 正文预览
 * @param textLength  正文长度
 * @param paraCount   段落数
 * @param level       章节层级
 * @param vector      语义向量
 */
public record BidCaseSliceMatchCandidate(
        Long id,
        String projectDir,
        String docxFile,
        String docxLabel,
        String title,
        String textPreview,
        int textLength,
        int paraCount,
        int level,
        float[] vector
) {
}
