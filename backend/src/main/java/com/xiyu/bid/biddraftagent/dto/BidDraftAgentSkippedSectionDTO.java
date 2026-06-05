package com.xiyu.bid.biddraftagent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BidDraftAgentSkippedSectionDTO {

    private Long sectionId;
    private String sectionKey;
    private String title;
    private Boolean locked;
    private String reason;
}
