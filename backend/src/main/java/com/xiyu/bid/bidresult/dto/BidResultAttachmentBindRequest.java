package com.xiyu.bid.bidresult.dto;

import com.xiyu.bid.bidresult.core.BidResultAttachmentRef;
import lombok.Data;

@Data
public class BidResultAttachmentBindRequest {
    private Long documentId;
    private BidResultAttachmentRef.AttachmentType attachmentType;
}

