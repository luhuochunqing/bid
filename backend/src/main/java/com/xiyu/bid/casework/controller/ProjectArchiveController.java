package com.xiyu.bid.casework.controller;

import com.xiyu.bid.casework.application.ProjectArchiveDetailService;
import com.xiyu.bid.casework.application.ProjectArchiveExportService;
import com.xiyu.bid.casework.application.ProjectArchiveWorkflowService;
import com.xiyu.bid.casework.application.StreamingZipPackager;
import com.xiyu.bid.casework.dto.ProjectArchiveDetailResponse;
import com.xiyu.bid.casework.dto.ProjectArchiveQuery;
import com.xiyu.bid.casework.dto.ProjectArchiveResponse;
import com.xiyu.bid.casework.dto.ProjectArchiveStatsResponse;
import com.xiyu.bid.casework.infrastructure.ArchiveFile;
import com.xiyu.bid.casework.infrastructure.ArchiveFileRepository;
import com.xiyu.bid.casework.infrastructure.ProjectArchive;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/archive")
@RequiredArgsConstructor
public class ProjectArchiveController {

    private final ProjectArchiveWorkflowService workflowService;
    private final ProjectArchiveDetailService detailService;
    private final ProjectArchiveExportService archiveExportService;
    private final StreamingZipPackager streamingZipPackager;
    private final ArchiveFileRepository archiveFileRepository;

    @GetMapping
    public ResponseEntity<Page<ProjectArchiveResponse>> queryProjectArchives(
            ProjectArchiveQuery query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<ProjectArchiveResponse> result = workflowService.queryProjectArchives(query, PageRequest.of(page, size));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/stats")
    public ResponseEntity<ProjectArchiveStatsResponse> getArchiveStats() {
        ProjectArchiveStatsResponse stats = workflowService.getStats();
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProjectArchiveDetailResponse> getArchiveDetail(@PathVariable Long id) {
        ProjectArchiveDetailResponse result = detailService.getArchiveDetail(id);
        return ResponseEntity.ok(result);
    }

    /**
     * 预览文件：返回文件流，浏览器直接展示（仅 PDF 支持完整预览，其他格式引导下载）
     */
    @GetMapping("/files/{fileId}/preview")
    public ResponseEntity<Resource> previewFile(@PathVariable Long fileId) {
        ArchiveFile file = archiveFileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("文件不存在: " + fileId));

        ProjectArchive archive = workflowService.findArchiveById(file.getArchiveId());
        workflowService.assertCurrentUserCanAccessProject(archive.getProjectId());

        String opName = getCurrentOperatorName();
        workflowService.recordLog(archive.getId(), 0L, opName, "预览", "预览人" + opName + "、预览时间" + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "、预览文件名" + file.getFileName());

        Path filePath = Paths.get(file.getFilePath());
        if (!Files.exists(filePath)) {
            throw new IllegalArgumentException("文件物理路径不存在: " + file.getFilePath());
        }

        String fileName = file.getFileName();
        String contentType = inferContentType(fileName);

        long fileSize;
        try { fileSize = Files.size(filePath); }
        catch (java.io.IOException e) { throw new IllegalStateException("无法读取文件大小: " + file.getFilePath(), e); }

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION,
            "inline; filename=\"" + sanitizeFilename(fileName) + "\"");
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.setContentLength(fileSize);

        Resource resource = new FileSystemResource(filePath);
        return new ResponseEntity<>(resource, headers, HttpStatus.OK);
    }

    /**
     * 下载文件：返回文件流，强制触发浏览器下载
     */
    @GetMapping("/files/{fileId}/download")
    public ResponseEntity<Resource> downloadFile(@PathVariable Long fileId) {
        ArchiveFile file = archiveFileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("文件不存在: " + fileId));

        ProjectArchive archive = workflowService.findArchiveById(file.getArchiveId());
        workflowService.assertCurrentUserCanAccessProject(archive.getProjectId());

        String opName = getCurrentOperatorName();
        workflowService.recordLog(archive.getId(), 0L, opName, "下载", "下载人" + opName + "、下载时间" + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "、下载文件名" + file.getFileName());

        Path filePath = Paths.get(file.getFilePath());
        if (!Files.exists(filePath)) {
            throw new IllegalArgumentException("文件物理路径不存在: " + file.getFilePath());
        }

        String fileName = file.getFileName();

        long fileSize;
        try { fileSize = Files.size(filePath); }
        catch (java.io.IOException e) { throw new IllegalStateException("无法读取文件大小: " + file.getFilePath(), e); }

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"" + sanitizeFilename(fileName) + "\"");
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentLength(fileSize);

        Resource resource = new FileSystemResource(filePath);
        return new ResponseEntity<>(resource, headers, HttpStatus.OK);
    }

    @PostMapping("/export-excel")
    public ResponseEntity<StreamingResponseBody> exportExcel(
            @RequestBody ProjectArchiveQuery query,
            @RequestParam(required = false) Long userId) {
        String timestamp = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Disposition",
                "attachment; filename=\"方案管理-项目档案台账-" + timestamp + ".xlsx\"");
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

        try {
            List<ProjectArchive> archives = workflowService.getRawArchives(query);
            Set<Long> exportableProjectIds = archiveExportService.resolveExportableProjectIds();
            List<Long> allowedProjectIds = archives.stream()
                    .map(ProjectArchive::getProjectId)
                    .filter(Objects::nonNull)
                    .filter(exportableProjectIds::contains)
                    .toList();
            String opName = getCurrentOperatorName();
            String ts = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            if (!allowedProjectIds.isEmpty() && !archives.isEmpty()) {
                workflowService.recordLog(archives.get(0).getId(), 0L, opName, "导出", "台账导出 " + opName + " " + ts);
            }
            ProjectArchiveExportService.ArchiveExportResult result =
                    archiveExportService.exportProjectArchives(new java.util.HashSet<>(allowedProjectIds));
            Path tempFile = Files.createTempFile("project_archive_export_", ".xlsx");
            Files.write(tempFile, result.data());

            StreamingResponseBody body = outputStream -> {
                try {
                    Files.copy(tempFile, outputStream);
                } finally {
                    try { Files.deleteIfExists(tempFile); } catch (Exception ignored) {}
                }
            };
            return new ResponseEntity<>(body, headers, HttpStatus.OK);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initiate Excel export", e);
        }
    }

    /** 导出单个项目全部资料（ZIP 压缩包）。 */
    @GetMapping("/export-zip/{projectId}")
    public ResponseEntity<StreamingResponseBody> exportSingleProjectArchive(
            @PathVariable Long projectId) {
        String timestamp = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
        try {
            ProjectArchiveQuery query = new ProjectArchiveQuery();
            // Workaround: no projectId filter in query, get raw archives and filter
            List<ProjectArchive> allArchives = workflowService.getRawArchives(new ProjectArchiveQuery());
            List<ProjectArchive> archives = allArchives.stream()
                    .filter(a -> a.getProjectId().equals(projectId))
                    .toList();
            if (archives.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            Set<Long> exportableProjectIds = archiveExportService.resolveExportableProjectIds();
            if (!exportableProjectIds.contains(projectId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            // Generate single-project Excel ledger
            ProjectArchiveExportService.ArchiveExportResult excelResult =
                    archiveExportService.exportProjectArchives(new java.util.HashSet<>(Set.of(projectId)));
            Path tempExcelPath = Files.createTempFile("single_archive_", ".xlsx");
            Files.write(tempExcelPath, excelResult.data());

            StreamingResponseBody body = streamingZipPackager.packageArchives(archives, tempExcelPath);
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"project_archive_" + projectId + "_" + timestamp + ".zip\"");
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            return new ResponseEntity<>(body, headers, HttpStatus.OK);
        } catch (Exception e) {
            throw new RuntimeException("Failed to export single project archive: " + projectId, e);
        }
    }


    @PostMapping("/export-zip")
    public ResponseEntity<StreamingResponseBody> exportZip(
            @RequestBody ProjectArchiveQuery query,
            @RequestParam(required = false) Long userId) {
        String timestamp = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
        try {
            List<ProjectArchive> archives = workflowService.getRawArchives(query);
            Set<Long> exportableProjectIds = archiveExportService.resolveExportableProjectIds();
            List<Long> allowedProjectIds = archives.stream()
                    .map(ProjectArchive::getProjectId)
                    .filter(Objects::nonNull)
                    .filter(exportableProjectIds::contains)
                    .toList();
            List<ProjectArchive> filteredArchives = archives.stream()
                    .filter(a -> allowedProjectIds.contains(a.getProjectId()))
                    .toList();
            String opName = getCurrentOperatorName();
            String ts = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            if (!allowedProjectIds.isEmpty() && !archives.isEmpty()) {
                workflowService.recordLog(archives.get(0).getId(), 0L, opName, "导出", "台账导出 " + opName + " " + ts);
            }

            ProjectArchiveExportService.ArchiveExportResult excelResult =
                    archiveExportService.exportProjectArchives(new java.util.HashSet<>(allowedProjectIds));
            Path tempExcelPath = Files.createTempFile("archive_ledger_", ".xlsx");
            Files.write(tempExcelPath, excelResult.data());

            StreamingResponseBody body = streamingZipPackager.packageArchives(filteredArchives, tempExcelPath);
            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-Disposition",
                    "attachment; filename=\"方案管理-项目档案文件包-" + timestamp + ".zip\"");
            headers.setContentType(MediaType.parseMediaType("application/zip"));
            return new ResponseEntity<>(body, headers, HttpStatus.OK);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initiate ZIP export", e);
        }
    }

    private String inferContentType(String filename) {
        if (filename == null) return "application/octet-stream";
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".doc")) return "application/msword";
        if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (lower.endsWith(".xls")) return "application/vnd.ms-excel";
        if (lower.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".txt")) return "text/plain";
        return "application/octet-stream";
    }

    private String sanitizeFilename(String filename) {
        if (filename == null) return "unnamed";
        return filename.replaceAll("[^\\w\\u4e00-\\u9fa5.\\-]", "_");
    }

    private String getCurrentOperatorName() {
        try {
            var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getName() != null) return auth.getName();
        } catch (Exception ignored) {}
        return "系统";
    }
}
