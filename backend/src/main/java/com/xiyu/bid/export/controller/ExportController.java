// Input: export service and request DTOs
// Output: Export REST API endpoints and export metadata responses
// Pos: Controller/控制器层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.export.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.config.ExportConfig;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.export.dto.ExportRequest;
import com.xiyu.bid.export.dto.ExportResponse;
import com.xiyu.bid.export.service.ExcelExportService;
import com.xiyu.bid.service.AuthService;
import com.xiyu.bid.service.RateLimitService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Export Controller with security, audit logging, and rate limiting.
 */
@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
@Slf4j
public class ExportController {
    private static final String ADMIN_MANAGER_STAFF_EXPR = "hasAnyRole('ADMIN', 'MANAGER', 'STAFF')";

    private final ExcelExportService excelExportService;
    private final ExportConfig exportConfig;
    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;
    private final AuthService authService;

    private static final String EXPORT_TEMP_DIR = System.getProperty("java.io.tmpdir") + "/xiyu-exports/";

    /**
     * Export data to Excel file.
     * Requires ADMIN, MANAGER, or STAFF role.
     * Rate limited to prevent abuse.
     */
    @PostMapping("/excel")
    @PreAuthorize(ADMIN_MANAGER_STAFF_EXPR)
    @Auditable(action = "EXPORT_EXCEL", entityType = "DATA", description = "Export data to Excel")
    public ResponseEntity<?> exportToExcel(
            @Valid @RequestBody ExportRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        // Extract user ID from authentication
        Long userId = extractUserId(userDetails);

        // Check rate limit
        if (!rateLimitService.checkExportRateLimit(userId)) {
            return ResponseEntity.status(429)
                    .body(ApiResponse.error("Rate limit exceeded: maximum " +
                            exportConfig.getMaxExportsPerHour() + " exports per hour"));
        }

        try {
            // Validate export type
            String dataType = request.getDataType();
            if (dataType == null || dataType.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Export type cannot be empty"));
            }

            // Validate and create temp directory
            Path tempDir = Path.of(EXPORT_TEMP_DIR);
            if (!Files.exists(tempDir)) {
                Files.createDirectories(tempDir);
            }

            // Generate unique filename
            String filename = excelExportService.getExportFileName(dataType);
            String safeFilename = sanitizeFilename(filename);
            Path filePath = tempDir.resolve(UUID.randomUUID() + "_" + safeFilename);

            // Perform export - convert params to JSON string
            String paramsJson = request.getParams() != null ? objectMapper.writeValueAsString(request.getParams()) : null;
            ExcelExportService.ExportFileResult exportResult = excelExportService.exportToExcelWithResult(
                    dataType, filePath, paramsJson, userId);

            // Prepare response
            ExportResponse response = ExportResponse.builder()
                    .filename(filename)
                    .recordCount(exportResult.recordCount())
                    .fileSize(exportResult.fileSize())
                    .build();

            log.info("Export completed: user={}, type={}, size={}, filename={}",
                    userId, dataType, exportResult.fileSize(), filename);

            return ResponseEntity.ok(ApiResponse.success("Export completed successfully", response));

        } catch (IllegalArgumentException e) {
            log.warn("Export validation failed: user={}, error={}", userId, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (IOException | RuntimeException e) {
            log.error("Export failed: user={}, error={}", userId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("导出失败，请稍后重试"));
        }
    }

    /**
     * Export and download Excel file directly.
     */
    @PostMapping("/excel/download")
    @PreAuthorize(ADMIN_MANAGER_STAFF_EXPR)
    @Auditable(action = "EXPORT_EXCEL_DOWNLOAD", entityType = "DATA", description = "Export and download Excel file")
    public ResponseEntity<byte[]> exportAndDownload(
            @Valid @RequestBody ExportRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = extractUserId(userDetails);

        // Check rate limit
        if (!rateLimitService.checkExportRateLimit(userId)) {
            return ResponseEntity.status(429).build();
        }

        try {
            String dataType = request.getDataType();
            if (dataType == null || dataType.isBlank()) {
                return ResponseEntity.badRequest().build();
            }

            Path tempDir = Path.of(EXPORT_TEMP_DIR);
            if (!Files.exists(tempDir)) {
                Files.createDirectories(tempDir);
            }

            String filename = excelExportService.getExportFileName(dataType);
            String safeFilename = sanitizeFilename(filename);
            Path filePath = tempDir.resolve(UUID.randomUUID() + "_" + safeFilename);

            // Convert params to JSON string
            String paramsJson = request.getParams() != null ? objectMapper.writeValueAsString(request.getParams()) : null;
            excelExportService.exportToExcel(dataType, filePath, paramsJson, userId);
            byte[] fileContent = Files.readAllBytes(filePath);

            // Clean up temp file
            Files.deleteIfExists(filePath);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(fileContent.length)
                    .body(fileContent);

        } catch (IOException | RuntimeException e) {
            log.error("Export download failed: user={}, error={}", userId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get supported export types.
     */
    @GetMapping("/types")
    @PreAuthorize(ADMIN_MANAGER_STAFF_EXPR)
    public ResponseEntity<ApiResponse<java.util.List<String>>> getSupportedTypes() {
        return ResponseEntity.ok(ApiResponse.success(java.util.List.of(
                "tenders", "projects", "qualifications", "cases", "templates"
        )));
    }

    /**
     * Get export configuration (for admin display).
     */
    @GetMapping("/config")
    @PreAuthorize(ADMIN_MANAGER_STAFF_EXPR)
    @Auditable(action = "VIEW_EXPORT_CONFIG", entityType = "SYSTEM", description = "View export configuration")
    public ResponseEntity<ApiResponse<ExportConfigInfo>> getExportConfig(
            @AuthenticationPrincipal UserDetails userDetails) {

        ExportConfigInfo config = ExportConfigInfo.builder()
                .maxRecords(exportConfig.getMaxRecords())
                .maxFileSizeMB(exportConfig.getMaxFileSizeBytes() / (1024 * 1024))
                .maxExportsPerHour(exportConfig.getMaxExportsPerHour())
                .auditEnabled(exportConfig.isAuditEnabled())
                .build();

        return ResponseEntity.ok(ApiResponse.success(config));
    }

    /**
     * Extract the persisted user from UserDetails.
     *
     * SECURITY: username in Spring Security is the login name, not a numeric userId.
     * Always resolve the current user from the repository before authorizing exports.
     */
    private Long extractUserId(UserDetails userDetails) {
        return getCurrentUser(userDetails).getId();
    }

    private User getCurrentUser(UserDetails userDetails) {
        if (userDetails == null) {
            throw new org.springframework.security.authentication.AuthenticationServiceException(
                    "UserDetails cannot be null");
        }

        String username = userDetails.getUsername();
        if (username == null || username.trim().isEmpty()) {
            throw new org.springframework.security.authentication.AuthenticationServiceException(
                    "Username cannot be null or empty");
        }

        try {
            return authService.resolveUserByUsername(username.trim());
        } catch (org.springframework.security.core.userdetails.UsernameNotFoundException ex) {
            throw new org.springframework.security.authentication.AuthenticationServiceException(
                    "Authenticated user not found: " + username, ex);
        }
    }

    private String sanitizeFilename(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class ExportConfigInfo {
        private int maxRecords;
        private long maxFileSizeMB;
        private int maxExportsPerHour;
        private boolean auditEnabled;
    }
}
