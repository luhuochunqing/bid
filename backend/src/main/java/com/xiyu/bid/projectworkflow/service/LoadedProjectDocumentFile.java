package com.xiyu.bid.projectworkflow.service;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

public record LoadedProjectDocumentFile(
        String fileUrl,
        String physicalPath,
        String contentType,
        byte[] content,
        Resource resource
) {

    public LoadedProjectDocumentFile {
        content = content == null ? null : content.clone();
    }

    public LoadedProjectDocumentFile(String fileUrl, String physicalPath, String contentType, byte[] content) {
        this(fileUrl, physicalPath, contentType, content, new ByteArrayResource(content == null ? new byte[0] : content.clone()));
    }

    @Override
    public byte[] content() {
        return content == null ? null : content.clone();
    }
}
