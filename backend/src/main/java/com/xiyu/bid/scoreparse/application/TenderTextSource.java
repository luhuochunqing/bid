package com.xiyu.bid.scoreparse.application;

/**
 * 评分解析用招标正文。立项文件优先，旧快照仅作兜底。
 */
public record TenderTextSource(String fileName, String fileUrl, String text) {
}
