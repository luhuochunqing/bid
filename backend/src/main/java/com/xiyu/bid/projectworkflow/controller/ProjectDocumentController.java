package com.xiyu.bid.projectworkflow.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.projectworkflow.dto.ProjectDocumentCreateRequest;
import com.xiyu.bid.projectworkflow.dto.ProjectDocumentDTO;
import com.xiyu.bid.projectworkflow.dto.ProjectDocumentDownloadFile;
import com.xiyu.bid.projectworkflow.service.ProjectWorkflowService;
import com.xiyu.bid.util.InputSanitizer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/documents")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ProjectDocumentController {

    private final ProjectWorkflowService projectWorkflowService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<ProjectDocumentDTO>>> getProjectDocuments(
            @PathVariable Long projectId,
            @RequestParam(required = false) String documentCategory,
            @RequestParam(required = false) String linkedEntityType,
            @RequestParam(required = false) Long linkedEntityId
    ) {
        return ResponseEntity.ok(ApiResponse.success(projectWorkflowService.getProjectDocuments(
                projectId,
                documentCategory,
                linkedEntityType,
                linkedEntityId
        )));
    }

    @GetMapping("/{documentId}/download")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<org.springframework.core.io.Resource> downloadProjectDocument(
            @PathVariable Long projectId,
            @PathVariable Long documentId
    ) {
        ProjectDocumentDownloadFile file = projectWorkflowService.getProjectDocumentFile(projectId, documentId);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.contentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(file.resource());
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ProjectDocumentDTO>> createProjectDocument(
            @PathVariable Long projectId,
            @Valid @RequestBody ProjectDocumentCreateRequest request
    ) {
        sanitizeDocumentRequest(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Project document created successfully",
                        projectWorkflowService.createProjectDocument(projectId, request)
                ));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ProjectDocumentDTO>> uploadProjectDocument(
            @PathVariable Long projectId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String size,
            @RequestParam(required = false) String fileType,
            @RequestParam(required = false) String documentCategory,
            @RequestParam(required = false) String linkedEntityType,
            @RequestParam(required = false) Long linkedEntityId,
            @RequestParam(required = false) Long uploaderId,
            @RequestParam(required = false) String uploaderName
    ) {
        ProjectDocumentCreateRequest request = ProjectDocumentCreateRequest.builder()
                .name(name == null || name.isBlank() ? file.getOriginalFilename() : name)
                .size(size)
                .fileType(fileType)
                .documentCategory(documentCategory)
                .linkedEntityType(linkedEntityType)
                .linkedEntityId(linkedEntityId)
                .uploaderId(uploaderId)
                .uploaderName(uploaderName)
                .build();
        sanitizeDocumentRequest(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Project document uploaded successfully",
                        projectWorkflowService.createUploadedProjectDocument(projectId, request, file)
                ));
    }

    @DeleteMapping("/{documentId}")
    // CO-382: 早过滤层——对齐蓝图 §3.3.1.2「删除文档」权限矩阵。
    // 旧 hasAnyRole('ADMIN','MANAGER') 用了已废弃的 MANAGER 角色名，新角色体系下 bid-TeamLeader/bidAdmin 无法命中。
    // 真权限闸门在 Service 层 ProjectDocumentWorkflowPolicy.canDeleteProjectDocument，本注解只是早过滤减少无效调用。
    @PreAuthorize("hasAnyRole('ADMIN', 'BIDADMIN', 'BID_TEAMLEADER')")
    public ResponseEntity<ApiResponse<Void>> deleteProjectDocument(
            @PathVariable Long projectId,
            @PathVariable Long documentId
    ) {
        projectWorkflowService.deleteProjectDocument(projectId, documentId);
        return ResponseEntity.ok(ApiResponse.success("Project document deleted successfully", null));
    }

    private void sanitizeDocumentRequest(ProjectDocumentCreateRequest request) {
        request.setName(InputSanitizer.sanitizeString(request.getName(), 255));
        if (request.getUploaderName() != null) {
            request.setUploaderName(InputSanitizer.sanitizeString(request.getUploaderName(), 100));
        }
        if (request.getFileType() != null) {
            request.setFileType(InputSanitizer.sanitizeString(request.getFileType(), 50));
        }
        if (request.getSize() != null) {
            request.setSize(InputSanitizer.sanitizeString(request.getSize(), 50));
        }
        if (request.getDocumentCategory() != null) {
            request.setDocumentCategory(InputSanitizer.sanitizeString(request.getDocumentCategory(), 64));
        }
        if (request.getLinkedEntityType() != null) {
            request.setLinkedEntityType(InputSanitizer.sanitizeString(request.getLinkedEntityType(), 64));
        }
        if (request.getFileUrl() != null) {
            request.setFileUrl(InputSanitizer.sanitizeString(request.getFileUrl(), 1000));
        }
    }
}
