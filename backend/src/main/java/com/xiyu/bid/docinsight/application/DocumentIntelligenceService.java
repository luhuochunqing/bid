package com.xiyu.bid.docinsight.application;

import org.springframework.web.multipart.MultipartFile;

public interface DocumentIntelligenceService {
    DocumentAnalysisResult process(String profileCode, String entityId, MultipartFile file);
    DocumentAnalysisResult processExisting(String profileCode, String entityId, String storagePath, String fileName, String contentType);
}
