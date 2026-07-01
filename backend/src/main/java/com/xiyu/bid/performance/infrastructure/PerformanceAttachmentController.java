// checkstyle:off
package com.xiyu.bid.performance.infrastructure;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.performance.application.dto.PerformanceAttachmentUploadDTO;
import com.xiyu.bid.performance.application.service.PerformanceAttachmentStorageAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 业绩附件上传接口（CO-442）。
 *
 * <p>独立于 PerformanceController 的 CRUD 路径，提供不依赖 performanceId 的文件上传端点，
 * 供"新增业绩"时先上传文件、拿到 fileUrl 后再随表单整体提交。
 */
@RestController
@RequestMapping("/api/knowledge/performance/attachments")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class PerformanceAttachmentController {

    private static final String PERM = RoleProfileCatalog.PERFORMANCE_MANAGE_PERMISSION;

    private final PerformanceAttachmentStorageAppService storageService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('" + PERM + "')")
    @Auditable(action = "UPLOAD", entityType = "PerformanceAttachment", description = "上传业绩附件")
    public ResponseEntity<ApiResponse<PerformanceAttachmentUploadDTO>> upload(
            @RequestParam("fileType") String fileType,
            @RequestParam("file") MultipartFile file) throws IOException {
        PerformanceAttachmentUploadDTO result = storageService.upload(fileType, file);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("上传成功", result));
    }
}
