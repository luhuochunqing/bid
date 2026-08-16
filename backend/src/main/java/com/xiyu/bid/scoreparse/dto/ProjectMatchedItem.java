package com.xiyu.bid.scoreparse.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 项目业绩命中摘要（project/match 响应条目）。
 */
public record ProjectMatchedItem(
        Long id,
        String contractName,
        String projectType,
        LocalDate signingDate,
        BigDecimal contractAmount
) {
}
