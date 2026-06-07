package com.xiyu.bid.biddraftagent.application;

import java.util.Optional;

public interface TenderDocumentStorage {

    StoredTenderDocument store(Long projectId, String fileName, String contentType, byte[] content);

    Optional<LoadedTenderDocument> loadByFileUrl(String fileUrl);
}
