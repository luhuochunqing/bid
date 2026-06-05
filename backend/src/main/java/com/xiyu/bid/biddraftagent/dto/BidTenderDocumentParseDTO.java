package com.xiyu.bid.biddraftagent.dto;

import com.xiyu.bid.biddraftagent.domain.TenderRequirementProfile;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BidTenderDocumentParseDTO {

    private BidTenderDocumentDTO document;
    private TenderRequirementProfile requirementProfile;
    private String message;
}
