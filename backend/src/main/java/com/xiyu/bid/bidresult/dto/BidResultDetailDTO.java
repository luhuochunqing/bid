package com.xiyu.bid.bidresult.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
public class BidResultDetailDTO {
    private BidResultFetchResultDTO fetchResult;
    private BidResultReminderDTO reminder;
    private BidResultAttachmentDTO requiredAttachment;
    private BidResultAttachmentDTO noticeAttachment;
    private BidResultAttachmentDTO analysisAttachment;
    private List<CompetitorWinDTO> competitorWins;
}
