package com.xiyu.bid.qualification.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * §4.2.1.4 批量关联附件结果 DTO
 */
@Value
@Builder
public class BatchAttachResultDTO {

    int total;
    int success;
    int failed;
    List<MatchedItem> matched;
    List<UnmatchedItem> unmatched;

    @Value
    @Builder
    public static class MatchedItem {
        String fileName;
        String certificateNo;
        Long qualificationId;
        String qualificationName;
    }

    @Value
    @Builder
    public static class UnmatchedItem {
        String fileName;
        String reason;
    }
}
