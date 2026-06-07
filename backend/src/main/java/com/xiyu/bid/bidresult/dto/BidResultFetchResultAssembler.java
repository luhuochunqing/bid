package com.xiyu.bid.bidresult.dto;

import com.xiyu.bid.bidresult.entity.BidResultFetchResult;

public final class BidResultFetchResultAssembler {

    private BidResultFetchResultAssembler() {
    }

    public static BidResultFetchResultDTO toDto(BidResultFetchResult entity) {
        if (entity == null) {
            return null;
        }
        return BidResultFetchResultDTO.builder()
                .id(entity.getId())
                .source(entity.getSource())
                .tenderId(entity.getTenderId())
                .projectId(entity.getProjectId())
                .projectName(entity.getProjectName())
                .result(entity.getResult())
                .amount(entity.getAmount())
                .fetchTime(entity.getFetchTime())
                .status(entity.getStatus())
                .confirmedAt(entity.getConfirmedAt())
                .confirmedBy(entity.getConfirmedBy())
                .ignoredReason(entity.getIgnoredReason())
                .registrationType(entity.getRegistrationType())
                .contractStartDate(entity.getContractStartDate())
                .contractEndDate(entity.getContractEndDate())
                .contractDurationMonths(entity.getContractDurationMonths())
                .remark(entity.getRemark())
                .skuCount(entity.getSkuCount())
                .winAnnounceDocUrl(entity.getWinAnnounceDocUrl())
                .noticeDocumentId(entity.getNoticeDocumentId())
                .analysisDocumentId(entity.getAnalysisDocumentId())
                .build();
    }
}

