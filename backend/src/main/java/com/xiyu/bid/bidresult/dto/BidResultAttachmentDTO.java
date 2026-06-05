package com.xiyu.bid.bidresult.dto;

import com.xiyu.bid.bidresult.core.BidResultAttachmentRef;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BidResultAttachmentDTO {
    private Long documentId;
    private BidResultAttachmentRef.AttachmentType attachmentType;
    private String name;
    private String fileType;
    private String reference;
}

