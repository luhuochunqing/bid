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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@RestController
@RequestMapping("/api/archive")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
public class ProjectArchiveController {

    private final ProjectArchiveWorkflowService workflowService;
    private final ProjectArchiveDetailService detailService;
    private final ProjectArchiveExportService archiveExportService;
    private final StreamingZipPackager streamingZipPackager;
    private final ArchiveFileRepository archiveFileRepository;

    @GetMapping
    @PreAuthorize("hasAuthority('project')")
    public ResponseEntity<Page<ProjectArchiveResponse>> queryProjectArchives(
            ProjectArchiveQuery query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(workflowService.queryProjectArchives(query,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))); // CO-418: 按归档日期倒序
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('project')")
    public ResponseEntity<ProjectArchiveStatsResponse> getArchiveStats() {
        ProjectArchiveStatsResponse stats = workflowService.getStats();
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('project')")
    public ResponseEntity<ProjectArchiveDetailResponse> getArchiveDetail(@PathVariable Long id) {
        // H5 fix 2026-06-13: 详情访问前对 archive 所属 project 做 owner check,
        // 避免非项目成员越权读取任意 projectId 的档案详情 (含附件路径/内部备注)。
        ProjectArchive archive = workflowService.findArchiveById(id);
        workflowService.assertCurrentUserCanAccessProject(archive.getProjectId());
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

        Path filePath = resolveAndValidateFilePath(file.getFilePath());

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

        Path filePath = resolveAndValidateFilePath(file.getFilePath());

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

    @Value("${app.doc-insight.upload-dir:}")
    private String configuredUploadDir;
    /** CO-430: 获取 upload 根目录绝对路径。 */
    private String getArchiveFileBaseDir() {
        String d = (configuredUploadDir == null || configuredUploadDir.isBlank()) ? System.getProperty("java.io.tmpdir") + "/xiyu-doc-insight-uploads" : configuredUploadDir;
        return Path.of(d).toAbsolutePath().normalize().toString(); }
    /** 解析并验证文件路径，防止路径遍历攻击。 */
    private Path resolveAndValidateFilePath(String rawPath) {
        return com.xiyu.bid.shared.security.FilePathGuard.ensureExists(
                com.xiyu.bid.shared.security.FilePathGuard.resolveAbsoluteWithin(rawPath, getArchiveFileBaseDir()), rawPath); }
    @PostMapping("/export-excel")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<byte[]> exportExcel(
            @RequestBody ProjectArchiveQuery query) throws IOException {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
        HttpHeaders headers = new HttpHeaders();
        // Content-Disposition 必须是 ISO-8859-1，中文用 RFC 5987 编码作 filename* 备份
        headers.add("Content-Disposition",
                "attachment; filename=\"archive-ledger-" + timestamp + ".xlsx\"; "
                + "filename*=UTF-8''" + java.net.URLEncoder.encode("方案管理-项目档案台账-" + timestamp + ".xlsx", java.nio.charset.StandardCharsets.UTF_8));
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

        List<ProjectArchive> archives = workflowService.getRawArchives(query);
        Set<Long> exportableProjectIds = archiveExportService.resolveExportableProjectIds();
        // admin 时 exportableProjectIds == null，全部放行；非 admin 时 archive.projectId 已 filter null，Set 也 filter 过 null，contains 安全
        List<Long> allowedProjectIds = archives.stream()
                .map(ProjectArchive::getProjectId)
                .filter(Objects::nonNull)
                .filter(id -> exportableProjectIds == null || exportableProjectIds.contains(id))
                .toList();
        String opName = getCurrentOperatorName();
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        if (!allowedProjectIds.isEmpty() && !archives.isEmpty()) {
            workflowService.recordLog(archives.get(0).getId(), 0L, opName, "导出", "台账导出 " + opName + " " + ts);
        }
        ProjectArchiveExportService.ArchiveExportResult result =
                archiveExportService.exportProjectArchives(new HashSet<>(allowedProjectIds));
        headers.setContentLength(result.data().length);
        return new ResponseEntity<>(result.data(), headers, HttpStatus.OK);
    }

    /** 导出单个项目全部资料（ZIP 压缩包）。 */
    @GetMapping("/export-zip/{projectId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<byte[]> exportSingleProjectArchive(
            @PathVariable Long projectId) throws IOException {
        // H5 fix 2026-06-13: 单项目导出前先做 project owner check,
        // 阻止非项目成员通过遍历 projectId 越权下载任意项目档案包。
        workflowService.assertCurrentUserCanAccessProject(projectId);

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
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
        // admin 时 exportableProjectIds == null，全部放行；非 admin 才校验项目权限
        if (exportableProjectIds != null && !exportableProjectIds.contains(projectId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        // 4.1.1.3 档案详情抽屉：记录文件包导出日志
        String opName = getCurrentOperatorName();
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        workflowService.recordLog(archives.get(0).getId(), 0L, opName, "导出", "文件包导出 " + opName + " " + ts);
        // Generate single-project Excel ledger
        ProjectArchiveExportService.ArchiveExportResult excelResult =
                archiveExportService.exportProjectArchives(new HashSet<>(Set.of(projectId)));
        Path tempExcelPath = Files.createTempFile("single_archive_", ".xlsx");
        Files.write(tempExcelPath, excelResult.data());
        byte[] zipBytes = streamingZipPackager.buildZipBytes(archives, tempExcelPath);
        HttpHeaders headers = new HttpHeaders();
        // ISO-8859-1 主 filename + RFC 5987 中文 filename* 备份
        headers.add(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"archive-" + projectId + "-" + timestamp + ".zip\"; "
                + "filename*=UTF-8''" + java.net.URLEncoder.encode("项目档案-" + projectId + "-" + timestamp + ".zip", java.nio.charset.StandardCharsets.UTF_8));
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentLength(zipBytes.length);
        return new ResponseEntity<>(zipBytes, headers, HttpStatus.OK);
    }

    @PostMapping("/export-zip")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<byte[]> exportZip(
            @RequestBody ProjectArchiveQuery query,
            @RequestParam(required = false) Long userId) throws IOException {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
        List<ProjectArchive> archives = workflowService.getRawArchives(query);
        Set<Long> exportableProjectIds = archiveExportService.resolveExportableProjectIds();
        // admin 时 exportableProjectIds == null，全部放行；非 admin 时 archive.projectId 已 filter null，contains 安全
        List<Long> allowedProjectIds = archives.stream()
                .map(ProjectArchive::getProjectId)
                .filter(Objects::nonNull)
                .filter(id -> exportableProjectIds == null || exportableProjectIds.contains(id))
                .toList();
        List<ProjectArchive> filteredArchives = archives.stream()
                .filter(a -> allowedProjectIds.contains(a.getProjectId()))
                .toList();
        String opName = getCurrentOperatorName();
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        if (!filteredArchives.isEmpty()) {
            // 4.1.1.3 档案详情抽屉：按 archiveId 单选时只对当前归档记日志（与 GET /export-zip/{projectId} 一致）
            workflowService.recordLog(filteredArchives.get(0).getId(), 0L, opName, "导出", "文件包导出 " + opName + " " + ts);
        }

        ProjectArchiveExportService.ArchiveExportResult excelResult =
                archiveExportService.exportProjectArchives(new HashSet<>(allowedProjectIds));
        Path tempExcelPath = Files.createTempFile("archive_ledger_", ".xlsx");
        Files.write(tempExcelPath, excelResult.data());

        byte[] zipBytes = streamingZipPackager.buildZipBytes(filteredArchives, tempExcelPath);
        HttpHeaders headers = new HttpHeaders();
        // ISO-8859-1 主 filename + RFC 5987 中文 filename* 备份（Tomcat 会拒绝裸中文）
        headers.add("Content-Disposition",
                "attachment; filename=\"archive-bundle-" + timestamp + ".zip\"; "
                + "filename*=UTF-8''" + java.net.URLEncoder.encode("方案管理-项目档案文件包-" + timestamp + ".zip", java.nio.charset.StandardCharsets.UTF_8));
        headers.setContentType(MediaType.parseMediaType("application/zip"));
        headers.setContentLength(zipBytes.length);
        return new ResponseEntity<>(zipBytes, headers, HttpStatus.OK);
    }

    private String inferContentType(String filename) {
        if (filename == null) return "application/octet-stream";
        String l = filename.toLowerCase();
        if (l.endsWith(".pdf")) return "application/pdf";
        if (l.endsWith(".doc")) return "application/msword";
        if (l.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (l.endsWith(".xls")) return "application/vnd.ms-excel";
        if (l.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (l.endsWith(".png")) return "image/png";
        if (l.endsWith(".jpg") || l.endsWith(".jpeg")) return "image/jpeg";
        if (l.endsWith(".gif")) return "image/gif";
        return l.endsWith(".txt") ? "text/plain" : "application/octet-stream";
    }

    private String sanitizeFilename(String f) {
        return f == null ? "unnamed" : f.replaceAll("[^\\w\\u4e00-\\u9fa5.\\-]", "_");
    }

    private String getCurrentOperatorName() {
        try {
            var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getName() != null) return auth.getName();
        } catch (IllegalStateException | NullPointerException ignored) {
        }
        return "系统";
    }
}
