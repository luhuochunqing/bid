package com.xiyu.bid.projectworkflow.dto;

import org.springframework.core.io.Resource;

public record ProjectDocumentDownloadFile(
        String fileName,
        String fileUrl,
        String physicalPath,
        String contentType,
        long contentLength,
        Resource resource
) {
}
