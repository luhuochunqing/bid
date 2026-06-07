package com.xiyu.bid.bidresult.dto;

import com.xiyu.bid.bidresult.core.CompetitorReportRow;

public final class CompetitorReportRowAssembler {

    private CompetitorReportRowAssembler() {
    }

    public static BidResultCompetitorReportRowDTO toDto(CompetitorReportRow row) {
        if (row == null) {
            return null;
        }
        return BidResultCompetitorReportRowDTO.builder()
                .company(row.company())
                .skuCount(row.skuCount())
                .category(row.category())
                .discount(row.discount())
                .paymentTerms(row.paymentTerms())
                .winRate(row.winRate())
                .projectCount(row.projectCount())
                .trend(row.trend())
                .build();
    }
}
