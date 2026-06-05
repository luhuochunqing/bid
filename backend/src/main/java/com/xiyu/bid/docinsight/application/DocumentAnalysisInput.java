package com.xiyu.bid.docinsight.application;

import com.xiyu.bid.docinsight.domain.DocumentChunk;
import java.util.List;
import java.util.Map;

/**
 * 通用文档分析输入
 */
public record DocumentAnalysisInput(
    String documentId,
    String fileName,
    String fullText,
    String structuredMetadata, // Sidecar 返回的元数据
    List<DocumentChunk> chunks, // 已切分的片段
    String profileCode,        // 分析配置文件代码（如 TENDER, CONTRACT）
    Map<String, Object> context // 额外的业务上下文
) {
}
