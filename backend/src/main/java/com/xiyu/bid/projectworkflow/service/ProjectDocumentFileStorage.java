package com.xiyu.bid.projectworkflow.service;

import java.util.Optional;

public interface ProjectDocumentFileStorage {

    StoredProjectDocumentFile store(Long projectId, String fileName, String contentType, byte[] content);

    Optional<LoadedProjectDocumentFile> load(String fileUrl);
}
