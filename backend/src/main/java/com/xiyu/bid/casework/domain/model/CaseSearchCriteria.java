package com.xiyu.bid.casework.domain.model;

import java.math.BigDecimal;
import java.util.List;

public record CaseSearchCriteria(
        String keyword,
        String industry,
        String productLine,
        String outcome,
        Integer year,
        BigDecimal amountMin,
        BigDecimal amountMax,
        List<String> tags,
        String status,
        String visibility,
        int page,
        int pageSize,
        String sort
) {
}
