package com.xiyu.bid.scoreparse.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 资质证书匹配请求（POST /api/knowledge/cert/match，FR-009）。
 *
 * @param certNameKeywords 证书名称关键词（命中任一即算）
 * @param requiredLevel    要求等级（空则忽略等级）
 * @param requireValidUntil 要求有效期至（空则不校验有效期与状态）
 * @param requiredCount    要求数量
 */
public record CertMatchRequest(
        List<String> certNameKeywords,
        String requiredLevel,
        LocalDate requireValidUntil,
        Integer requiredCount
) {
}
