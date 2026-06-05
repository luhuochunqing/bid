package com.xiyu.bid.bidresult.dto;

import com.xiyu.bid.bidresult.core.CompetitorWinRow;
import com.xiyu.bid.bidresult.entity.CompetitorWinRecord;

import java.math.BigDecimal;

public final class CompetitorWinAssembler {

    private CompetitorWinAssembler() {
    }

    public static CompetitorWinDTO toDto(CompetitorWinRecord entity) {
        if (entity == null) {
            return null;
        }
        return CompetitorWinDTO.builder()
                .id(entity.getId())
                .competitorId(entity.getCompetitorId())
                .competitorName(entity.getCompetitorName())
                .projectId(entity.getProjectId())
                .projectName(entity.getProjectName())
                .skuCount(entity.getSkuCount())
                .category(entity.getCategory())
                .discount(entity.getDiscount())
                .paymentTerms(entity.getPaymentTerms())
                .wonAt(entity.getWonAt())
                .amount(entity.getAmount())
                .notes(entity.getNotes())
                .recordedBy(entity.getRecordedBy())
                .recordedByName(entity.getRecordedByName())
                .build();
    }

    public static CompetitorWinRow toRow(CompetitorWinRecord entity) {
        return new CompetitorWinRow(
                entity.getCompetitorId(),
                entity.getCompetitorName(),
                entity.getSkuCount(),
                entity.getCategory(),
                entity.getDiscount(),
                entity.getPaymentTerms(),
                entity.getAmount() != null ? entity.getAmount() : BigDecimal.ZERO
        );
    }
}

