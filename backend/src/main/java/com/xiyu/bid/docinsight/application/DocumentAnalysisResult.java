package com.xiyu.bid.docinsight.application;

import java.util.List;
import java.util.Map;

/**
 * 通用文档分析结果
 */
public record DocumentAnalysisResult(
    String documentId,
    Map<String, Object> extractedData, // 提取出的结构化数据（K-V）
    List<AnalysisRequirementItem> requirements, // 提取出的具体要求项
    String rawMarkdown,
    List<String> warnings
) {
    public record AnalysisRequirementItem(
        String category,
        String title,
        String content,
        boolean mandatory,
        String sourceExcerpt,
        Integer confidence,
        String sectionPath
    ) {}
}
