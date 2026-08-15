package com.xiyu.bid.biddraftagent.application;

/**
 * 招标文档存储完成事件（spec 041 contracts/score-parse-api.md §8）。
 * <p>由 {@link BidTenderDocumentImportAppService} 在招标文档 + 快照持久化成功后发布；
 * scoreparse 模块 {@code @Async @EventListener} 消费，自动触发评分标准解析。
 *
 * @param projectId  项目 ID
 * @param documentId 项目文档 ID（project_document.id）
 * @param fileUrl    doc-insight:// 文件地址
 */
public record TenderDocumentStoredEvent(
        Long projectId,
        Long documentId,
        String fileUrl
) {
}
