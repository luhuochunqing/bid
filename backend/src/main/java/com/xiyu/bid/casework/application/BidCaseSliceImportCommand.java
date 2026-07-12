package com.xiyu.bid.casework.application;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 案例切片单条导入命令（强类型请求 DTO）。
 *
 * <p>兼容 snake_case（原 API 请求体格式）和 camelCase 两种 JSON 字段名。
 * snake_case 为 @JsonProperty 主名（向后兼容），camelCase 为 @JsonAlias（向前兼容）。</p>
 *
 * @param project      来源项目目录（必填）
 * @param docxFile     来源 docx 文件路径
 * @param docxLabel    文件类别
 * @param title        章节标题（必填）
 * @param textPreview  正文预览
 * @param projectIdx   项目序号
 * @param sectionIdx   章节序号
 * @param level        章节层级
 * @param textLength   正文长度
 * @param paraCount    段落数
 */
public record BidCaseSliceImportCommand(
        String project,
        @JsonProperty("docx_file") @JsonAlias("docxFile") String docxFile,
        @JsonProperty("docx_label") @JsonAlias("docxLabel") String docxLabel,
        String title,
        @JsonProperty("text_preview") @JsonAlias("textPreview") String textPreview,
        @JsonProperty("project_idx") @JsonAlias("projectIdx") Integer projectIdx,
        @JsonProperty("section_idx") @JsonAlias("sectionIdx") Integer sectionIdx,
        Integer level,
        @JsonProperty("text_length") @JsonAlias("textLength") Integer textLength,
        @JsonProperty("para_count") @JsonAlias("paraCount") Integer paraCount
) {
}
