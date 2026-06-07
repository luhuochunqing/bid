package com.xiyu.bid.biddraftagent.domain.validation;

import java.time.LocalDate;

/** 品牌授权轻量摘要，纯核心不依赖外部 DTO */
public record BrandAuthSummary(
        Long id,
        String brandName,
        String productLine,
        String manufacturerName,
        LocalDate authEndDate
) {}
