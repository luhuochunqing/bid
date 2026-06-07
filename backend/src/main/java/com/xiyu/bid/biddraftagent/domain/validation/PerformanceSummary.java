package com.xiyu.bid.biddraftagent.domain.validation;

import java.time.LocalDate;

/** 业绩记录轻量摘要，纯核心不依赖外部 DTO */
public record PerformanceSummary(
        Long id,
        String contractName,
        String signingEntity,
        LocalDate expiryDate
) {}
