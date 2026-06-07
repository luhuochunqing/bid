package com.xiyu.bid.biddraftagent.infrastructure.tenderdocument;

import com.xiyu.bid.biddraftagent.application.TenderDocumentStorage;
import com.xiyu.bid.projectworkflow.service.ProjectDocumentFileStorage;
import com.xiyu.bid.projectworkflow.service.StoredProjectDocumentFile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class BidAgentProjectDocumentFileStorage implements ProjectDocumentFileStorage {

    private final TenderDocumentStorage tenderDocumentStorage;

    @Override
    public StoredProjectDocumentFile store(Long projectId, String fileName, String contentType, byte[] content) {
        var stored = tenderDocumentStorage.store(projectId, fileName, contentType, content);
        return new StoredProjectDocumentFile(stored.fileUrl(), stored.storagePath());
    }
}
