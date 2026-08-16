package com.xiyu.bid.scoreparse.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 仓库匹配请求（POST /api/knowledge/warehouse/match）。
 *
 * @param nameKeywords     仓库名称关键词（命中任一即算）
 * @param region           区域（精确匹配）
 * @param minArea          最小面积（含）
 * @param facilityKeywords 设施关键词（降级：基于备注文本匹配，命中时 matchDetail 注明）
 */
public record WarehouseMatchRequest(
        List<String> nameKeywords,
        String region,
        BigDecimal minArea,
        List<String> facilityKeywords
) {
}
