package com.xiyu.bid.projectworkflow.service;

import com.xiyu.bid.projectworkflow.core.UploadValidationPolicy;
import com.xiyu.bid.projectworkflow.dto.ProjectDocumentCreateRequest;
import com.xiyu.bid.projectworkflow.dto.ProjectDocumentDTO;
import com.xiyu.bid.casework.application.ProjectArchiveWorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;

@Service
@RequiredArgsConstructor
class ProjectDocumentUploadWorkflowService {

    private final ProjectWorkflowGuardService accessGuard;
    private final ProjectDocumentWorkflowService projectDocumentWorkflowService;
    private final ProjectDocumentFileStorage fileStorage;
    private final ProjectArchiveWorkflowService projectArchiveWorkflowService;

    ProjectDocumentDTO createUploadedProjectDocument(
            Long projectId,
            ProjectDocumentCreateRequest request,
            MultipartFile file
    ) {
        accessGuard.requireWorkflowMutationProject(projectId);
        validateUpload(file);
        String fileName = defaultString(request.getName(), originalFileName(file));
        byte[] content = fileBytes(file);
        StoredProjectDocumentFile storedFile = fileStorage.store(projectId, fileName, file.getContentType(), content);
        ProjectDocumentDTO created = projectDocumentWorkflowService.createProjectDocument(projectId, ProjectDocumentCreateRequest.builder()
                .name(fileName.trim())
                .size(defaultString(request.getSize(), formatSize(file.getSize())))
                .fileType(resolveFileType(request.getFileType(), fileName, file.getContentType()))
                .documentCategory(request.getDocumentCategory())
                .linkedEntityType(request.getLinkedEntityType())
                .linkedEntityId(request.getLinkedEntityId())
                .fileUrl(storedFile.fileUrl())
                .uploaderId(request.getUploaderId())
                .uploaderName(request.getUploaderName())
                .build());
        // 即时归档到项目档案（蓝图 §4.1.1.1 要求：上传时即时按分类归档）
        if (projectArchiveWorkflowService != null && storedFile.physicalPath() != null) {
            projectArchiveWorkflowService.attachFileToArchive(
                    projectId,
                    fileName.trim(),
                    request.getDocumentCategory(),
                    storedFile.physicalPath(),
                    file.getSize(),
                    request.getUploaderId(),
                    request.getUploaderName()
            );
        }
        return created;
    }

    private void validateUpload(MultipartFile file) {
        if (file == null) {
            throw new IllegalArgumentException("请上传项目文档");
        }
        UploadValidationPolicy.ValidationResult result = UploadValidationPolicy.validate(
                file.getOriginalFilename(),
                file.getContentType(),
                file.isEmpty() ? 0L : file.getSize()
        );
        if (!result.valid()) {
            throw new IllegalArgumentException(result.message());
        }
    }

    private byte[] fileBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new IllegalStateException("读取项目文档失败", ex);
        }
    }

    private String originalFileName(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        return fileName == null || fileName.isBlank() ? "项目文档" : fileName.trim();
    }

    private String resolveFileType(String requestedType, String fileName, String contentType) {
        String normalized = trimToNull(requestedType);
        if (normalized != null) {
            return normalized;
        }
        String extension = extensionOf(fileName);
        if (extension != null) {
            return extension;
        }
        return mapKnownContentType(contentType);
    }

    private String extensionOf(String fileName) {
        String normalized = trimToNull(fileName);
        if (normalized == null) {
            return null;
        }
        int dotIndex = normalized.lastIndexOf('.');
        return dotIndex < 0 || dotIndex == normalized.length() - 1
                ? null
                : normalized.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private String mapKnownContentType(String contentType) {
        String normalized = trimToNull(contentType);
        if (normalized == null) {
            return null;
        }
        return switch (normalized.toLowerCase(Locale.ROOT)) {
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx";
            case "application/msword" -> "doc";
            case "application/pdf" -> "pdf";
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "xlsx";
            case "application/vnd.ms-excel" -> "xls";
            default -> normalized.length() <= 50 ? normalized : normalized.substring(0, 50);
        };
    }

    private String formatSize(long bytes) {
        long kb = Math.max(1L, Math.round(bytes / 1024.0));
        return kb < 1024L ? kb + "KB" : Math.round(kb / 1024.0) + "MB";
    }

    private String defaultString(String value, String fallback) {
        String normalized = trimToNull(value);
        return normalized != null ? normalized : fallback;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
