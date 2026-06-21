package com.xiyu.bid.projectworkflow.service;

import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.projectworkflow.dto.ProjectDocumentDownloadFile;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class ProjectDocumentDownloadService {

    private final ProjectWorkflowGuardService accessGuard;
    private final ProjectDocumentFileStorage fileStorage;

    ProjectDocumentDownloadFile getProjectDocumentFile(Long projectId, Long documentId) {
        accessGuard.requireProject(projectId);
        ProjectDocument document = accessGuard.requireDocument(projectId, documentId);
        String fileUrl = trimToNull(document.getFileUrl());
        if (fileUrl == null) {
            throw ResourceNotFoundException.withMessage("Project document file not found: " + documentId);
        }
        LoadedProjectDocumentFile loaded = fileStorage.load(fileUrl)
                .orElseThrow(() -> ResourceNotFoundException.withMessage("Project document file not found: " + documentId));
        byte[] content = loaded.content() == null ? new byte[0] : loaded.content();
        return new ProjectDocumentDownloadFile(
                defaultString(document.getName(), "项目文档"),
                loaded.fileUrl(),
                loaded.physicalPath(),
                defaultString(loaded.contentType(), resolveContentType(document.getFileType(), document.getName())),
                content.length,
                loaded.resource() == null ? new ByteArrayResource(content) : loaded.resource()
        );
    }

    private String resolveContentType(String fileType, String fileName) {
        String normalized = trimToNull(fileType);
        if (normalized != null && normalized.contains("/")) {
            return MediaType.parseMediaType(normalized).toString();
        }
        String candidateName = normalized == null ? fileName : "document." + normalized;
        return MediaTypeFactory.getMediaType(candidateName)
                .orElse(MediaType.APPLICATION_OCTET_STREAM)
                .toString();
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static String defaultString(String value, String fallback) {
        String normalized = trimToNull(value);
        return normalized != null ? normalized : fallback;
    }
}
