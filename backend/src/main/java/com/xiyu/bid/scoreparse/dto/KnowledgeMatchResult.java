package com.xiyu.bid.scoreparse.dto;

import java.util.List;

/**
 * 知识库五类匹配通用响应骨架（spec 041 contracts/knowledge-match-api.md）。
 *
 * @param tier        FULL / PARTIAL / NONE
 * @param matchRatio  0-100 整数
 * @param matched     命中记录摘要（各类型独立 record：CertMatchedItem 等）
 * @param matchDetail 命中说明（含降级/过期标注）
 */
public record KnowledgeMatchResult(
        String tier,
        int matchRatio,
        List<?> matched,
        String matchDetail
) {

    public static KnowledgeMatchResult empty(String matchDetail) {
        return new KnowledgeMatchResult("NONE", 0, List.of(), matchDetail);
    }
}
