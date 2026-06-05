package com.xiyu.bid.docinsight.application;

/**
 * 文档智能分析器核心接口
 */
public interface DocumentAnalyzer {
    DocumentAnalysisResult analyze(DocumentAnalysisInput input);
    boolean supports(String profileCode);
}
