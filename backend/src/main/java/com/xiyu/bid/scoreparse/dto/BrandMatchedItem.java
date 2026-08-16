package com.xiyu.bid.scoreparse.dto;

import java.time.LocalDate;

/**
 * 品牌授权命中摘要（brand/match 响应条目）。
 *
 * @param expireSoon true 表示授权止期在未来 90 天内（含今天）
 */
public record BrandMatchedItem(
        Long id,
        String brandName,
        String manufacturerName,
        String productLine,
        LocalDate authEndDate,
        boolean expireSoon
) {
}
