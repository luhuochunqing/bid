// checkstyle:off
package com.xiyu.bid.performance.infrastructure;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.performance.application.dto.PerformanceAttachmentUploadDTO;
import com.xiyu.bid.performance.application.service.PerformanceAttachmentStorageAppService;
import com.xiyu.bid.performance.infrastructure.persistence.entity.PerformanceAttachmentEntity;
import com.xiyu.bid.performance.infrastructure.persistence.repository.PerformanceAttachmentJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

/**
 * 业绩附件上传/下载接口（CO-442）。
 *
 * <p>独立于 PerformanceController 的 CRUD 路径，提供不依赖 performanceId 的文件上传端点，
 * 供"新增业绩"时先上传文件、拿到 fileUrl 后再随表单整体提交。
 * 附件下载端点供详情页和前端直接访问附件文件。
 */
@RestController
@RequestMapping("/api/knowledge/performance/attachments")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class PerformanceAttachmentController {

    private static final String PERM = RoleProfileCatalog.PERFORMANCE_MANAGE_PERMISSION;

    private final PerformanceAttachmentStorageAppService storageService;
    private final PerformanceAttachmentJpaRepository attachmentRepository;

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

    /**
     * 下载业绩附件.
     *
     * <p>根据附件 ID 查找元数据，通过 storageService 解析本地路径并读取文件返回。
     * fileUrl 可能是绝对路径（页面上传）或相对路径（批量导入），由 storageService 统一解析。
     */
    @GetMapping("/{id}/download")
    @PreAuthorize("hasAuthority('" + PERM + "')")
    public ResponseEntity<byte[]> download(@PathVariable Long id) throws IOException {
        PerformanceAttachmentEntity attachment = attachmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PerformanceAttachment", String.valueOf(id)));

        String fileUrl = attachment.getFileUrl();
        if (fileUrl == null || fileUrl.isBlank()) {
            return ResponseEntity.notFound().build();
        }

        try {
            byte[] data = storageService.readAttachmentFile(fileUrl);

            String fileName = attachment.getFileName() != null
                    ? attachment.getFileName()
                    : "attachment";
            String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                    .replace("+", "%20");

            String contentType = resolveContentType(fileName);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename*=UTF-8''" + encodedName)
                    .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                            HttpHeaders.CONTENT_DISPOSITION)
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(data);
        } catch (NoSuchFileException e) {
            return ResponseEntity.notFound().build();
        }
    }

    private static String resolveContentType(String fileName) {
        if (fileName == null) return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }
}
