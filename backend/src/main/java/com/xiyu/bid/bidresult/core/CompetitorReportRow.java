package com.xiyu.bid.bidresult.core;

public record CompetitorReportRow(
        String company,
        long skuCount,
        String category,
        String discount,
        String paymentTerms,
        String winRate,
        long projectCount,
        String trend
) {
}
