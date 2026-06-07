package com.xiyu.bid.projectworkflow.service;

public record StoredProjectDocumentFile(String fileUrl, String physicalPath) {
    public StoredProjectDocumentFile(String fileUrl) {
        this(fileUrl, null);
    }
}
