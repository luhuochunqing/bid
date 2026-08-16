package com.xiyu.bid.scoreparse.dto;

import java.math.BigDecimal;

/**
 * 仓库命中摘要（warehouse/match 响应条目）。
 */
public record WarehouseMatchedItem(
        Long id,
        String name,
        String region,
        BigDecimal area,
        String status
) {
}
