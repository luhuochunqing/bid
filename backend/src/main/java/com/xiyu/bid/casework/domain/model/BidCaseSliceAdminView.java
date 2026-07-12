package com.xiyu.bid.casework.domain.model;

import java.time.LocalDateTime;

/**
 * 案例切片管理端视图（不可变值对象 / 前端 DTO）。
 *
 * <p>对应 {@code BidCaseSlice} 实体在管理端导入后回显的字段集合。</p>
 *
 * @param sliceId     切片 ID
 * @param projectDir  来源项目目录
 * @param docxFile    来源 docx 文件路径
 * @param docxLabel   文件类别
 * @param sectionIdx  章节序号
 * @param level       章节层级
 * @param title       章节标题
 * @param textPreview 正文预览
 * @param textLength  正文长度
 * @param paraCount   段落数
 * @param createdAt   创建时间
 */
public record BidCaseSliceAdminView(
        Long sliceId,
        String projectDir,
        String docxFile,
        String docxLabel,
        Integer sectionIdx,
        Integer level,
        String title,
        String textPreview,
        Integer textLength,
        Integer paraCount,
        LocalDateTime createdAt
) {
}
