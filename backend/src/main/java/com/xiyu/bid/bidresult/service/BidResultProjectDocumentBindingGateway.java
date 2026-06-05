package com.xiyu.bid.bidresult.service;

import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.projectworkflow.service.ProjectDocumentBindingGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@RequiredArgsConstructor
public class BidResultProjectDocumentBindingGateway implements ProjectDocumentBindingGateway {

    private final BidResultAttachmentAppService attachmentAppService;

    @Override
    public void onDocumentCreated(ProjectDocument document) {
        if (!isBidResultAttachment(document)) {
            return;
        }
        attachmentAppService.bindAttachmentFromDocument(document);
    }

    @Override
    public void onDocumentDeleted(ProjectDocument document) {
        if (!isBidResultAttachment(document)) {
            return;
        }
        attachmentAppService.unbindAttachmentByDocument(document.getId());
    }

    private boolean isBidResultAttachment(ProjectDocument document) {
        return document != null
                && "BID_RESULT".equalsIgnoreCase(document.getLinkedEntityType())
                && document.getLinkedEntityId() != null
                && ("BID_RESULT_NOTICE".equalsIgnoreCase(document.getDocumentCategory())
                || "BID_RESULT_ANALYSIS".equalsIgnoreCase(document.getDocumentCategory()));
    }
}
