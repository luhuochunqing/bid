package com.xiyu.bid.bidresult.core;

public record BidResultAttachmentRef(
        Long documentId,
        AttachmentType attachmentType
) {
    public enum AttachmentType {
        NOTICE,
        REPORT
    }

    public boolean isPresent() {
        return documentId != null;
    }
}

