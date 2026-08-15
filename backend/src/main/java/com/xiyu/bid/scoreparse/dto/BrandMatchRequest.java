package com.xiyu.bid.scoreparse.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 品牌授权匹配请求（POST /api/knowledge/brand/match）。
 *
 * @param brandNameKeywords 品牌名称关键词（命中任一即算）
 * @param productLine       产品线（ProductLine 枚举名或中文；降级：授权范围近似表达）
 * @param importDomestic    进口/国产
 * @param requireValidUntil 要求授权有效期至（auth_end_date 需 ≥ 该日期）
 */
public record BrandMatchRequest(
        List<String> brandNameKeywords,
        String productLine,
        String importDomestic,
        LocalDate requireValidUntil
) {
}
