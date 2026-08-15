package com.xiyu.bid.scoreparse.dto;

import java.time.LocalDate;

/**
 * 资质证书命中摘要（cert/match 响应条目）。
 *
 * @param expired true 表示 status=EXPIRED 或 expiry_date < 今天（算命中但标记，FR-009/5.3）
 */
public record CertMatchedItem(
        Long id,
        String name,
        String level,
        LocalDate expiryDate,
        boolean expired
) {
}
