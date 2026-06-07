package com.xiyu.bid.bidresult.dto;

import com.xiyu.bid.bidresult.core.BidResultAttachmentRef;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;

public final class BidResultAttachmentAssembler {

    private BidResultAttachmentAssembler() {
    }

    public static BidResultAttachmentDTO required(BidResultAttachmentRef.AttachmentType type) {
        return BidResultAttachmentDTO.builder()
                .attachmentType(type)
                .reference(type.name().toLowerCase())
                .build();
    }

    public static BidResultAttachmentDTO fromDocument(ProjectDocument document, BidResultAttachmentRef.AttachmentType type) {
        if (document == null) {
            return null;
        }
        return BidResultAttachmentDTO.builder()
                .documentId(document.getId())
                .attachmentType(type)
                .name(document.getName())
                .fileType(document.getFileType())
                .reference("project-document:" + document.getId())
                .build();
    }

    public static BidResultAttachmentDTO fromLegacyUrl(String url, BidResultAttachmentRef.AttachmentType type) {
        if (url == null || url.isBlank()) {
            return null;
        }
        return BidResultAttachmentDTO.builder()
                .attachmentType(type)
                .reference(url)
                .build();
    }
}
