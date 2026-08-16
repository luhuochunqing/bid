package com.xiyu.bid.scoreparse.dto;

import java.util.List;

/**
 * 人员匹配请求（POST /api/knowledge/person/match，FR-010）。
 *
 * @param positionKeywords 岗位关键词（匹配 technical_title，命中任一即算）
 * @param certNameKeywords 证书名称关键词（证书子表命中任一即算，需未删除且在有效期内）
 * @param requiredCount    要求人数
 */
public record PersonMatchRequest(
        List<String> positionKeywords,
        List<String> certNameKeywords,
        Integer requiredCount
) {
}
