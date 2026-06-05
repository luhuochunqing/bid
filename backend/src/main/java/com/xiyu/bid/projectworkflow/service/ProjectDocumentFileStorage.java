package com.xiyu.bid.projectworkflow.service;

public interface ProjectDocumentFileStorage {

    StoredProjectDocumentFile store(Long projectId, String fileName, String contentType, byte[] content);
}
