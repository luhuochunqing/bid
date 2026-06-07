package com.xiyu.bid.projectworkflow.service;

import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import org.springframework.stereotype.Component;

@Component
class NoOpProjectDocumentBindingGateway implements ProjectDocumentBindingGateway {

    @Override
    public void onDocumentCreated(ProjectDocument document) {
        // Extension point for bid-result attachment binding.
    }

    @Override
    public void onDocumentDeleted(ProjectDocument document) {
        // Extension point for attachment unbinding / reminder rollback.
    }
}
