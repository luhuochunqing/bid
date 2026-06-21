// Input: fileUrl（doc-insight:// 本地路径 或 http(s):// 外部 URL）
// Output: ResponseEntity<Resource> 文件流 + Content-Disposition 头；非法路径抛 ResponseStatusException
// Pos: integration.external — CRM 跨系统附件下载服务，复用本地存储与外部代理下载逻辑
package com.xiyu.bid.integration.external;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 附件下载服务（CO-280 403 修复）。
 *
 * <p>背景：CRM 用户点击附件时，浏览器直接访问 XiYu 的下载端点。
 * 原 {@code /api/doc-insight/download} 受 {@code @PreAuthorize("isAuthenticated()")} 保护，
 * CRM 用户没有 XiYu 登录态 → 403 Forbidden。
 *
 * <p>本服务复用 {@link com.xiyu.bid.docinsight.controller.DocInsightController} 的下载逻辑，
 * 供新的集成下载端点 {@code /api/integration/tenders/attachments/download} 调用。
 * 该端点走 {@code ApiKeyAuthenticationFilter}（X-API-Key 头），无需 XiYu 登录态。
 *
 * <p>支持的 fileUrl 格式：
 * <ul>
 *   <li>{@code doc-insight://<relativePath>}：本地存储文件</li>
 *   <li>{@code http(s)://<url>}：外部 URL 代理下载（CRM 推送附件）</li>
 * </ul>
 */
@Service
public class TenderAttachmentDownloadService {

    /** 文件存储目录，与 LocalDocumentStorage / DocInsightController 共享同一配置。 */
    @Value("${app.doc-insight.upload-dir:}")
    private String configuredUploadDir;

    /**
     * 下载文件。
     *
     * @param fileUrl 文件 URL，支持 doc-insight:// 和 http(s)://
     * @return 文件流响应
     * @throws ResponseStatusException 400（参数非法）/ 404（文件不存在）/ 502（外部下载失败）
     */
    public ResponseEntity<Resource> download(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件 URL 不能为空");
        }
        if (fileUrl.startsWith("http://") || fileUrl.startsWith("https://")) {
            return downloadExternal(fileUrl);
        }
        if (!fileUrl.startsWith("doc-insight://")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无效的文件 URL 格式");
        }
        return downloadLocal(fileUrl);
    }

    /** 下载本地 doc-insight:// 文件。 */
    private ResponseEntity<Resource> downloadLocal(String fileUrl) {
        String relativePath = fileUrl.substring("doc-insight://".length());
        if (relativePath.isBlank() || relativePath.contains("..")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无效的文件路径");
        }
        Path uploadRoot = resolveUploadRoot();
        Path targetPath = uploadRoot.resolve(relativePath).normalize();
        if (!targetPath.startsWith(uploadRoot.toAbsolutePath().normalize())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件路径越界");
        }
        if (!Files.exists(targetPath)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文件不存在");
        }
        String fileName = targetPath.getFileName().toString();
        String displayName = fileName.contains("-") ? fileName.substring(fileName.indexOf('-') + 1) : fileName;
        long fileSize;
        try {
            fileSize = Files.size(targetPath);
        } catch (IOException e) {
            throw new IllegalStateException("无法读取文件大小", e);
        }
        HttpHeaders headers = buildDownloadHeaders(displayName, fileSize);
        return new ResponseEntity<>(new FileSystemResource(targetPath), headers, HttpStatus.OK);
    }

    /** 代理下载外部 URL 文件。使用 UrlResource 流式转发，不缓存到内存/磁盘。 */
    private ResponseEntity<Resource> downloadExternal(String fileUrl) {
        UrlResource resource;
        String displayName;
        long fileSize;
        try {
            URI uri = URI.create(fileUrl);
            displayName = extractFileNameFromPath(uri.getPath());
            resource = new UrlResource(uri);
            if (!resource.exists()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "外部文件不存在");
            }
            fileSize = resource.contentLength();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "无法下载外部文件: " + e.getMessage(), e);
        }
        HttpHeaders headers = buildDownloadHeaders(displayName, fileSize);
        return new ResponseEntity<>(resource, headers, HttpStatus.OK);
    }

    /** 构造下载响应头（Content-Disposition + Content-Type + Content-Length）。 */
    private HttpHeaders buildDownloadHeaders(String displayName, long fileSize) {
        HttpHeaders headers = new HttpHeaders();
        String sanitized = sanitizeFilename(displayName);
        headers.add(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + sanitized + "\"; "
                        + "filename*=UTF-8''" + URLEncoder.encode(sanitized, StandardCharsets.UTF_8));
        headers.setContentType(MediaType.parseMediaType(inferContentType(displayName)));
        if (fileSize > 0) {
            headers.setContentLength(fileSize);
        }
        return headers;
    }

    /** 从 URL 路径提取文件名（URL 解码），失败回退为 "attachment"。 */
    private String extractFileNameFromPath(String path) {
        if (path == null || path.isBlank()) return "attachment";
        String name = path.substring(path.lastIndexOf('/') + 1);
        if (name.isBlank()) return "attachment";
        try {
            return java.net.URLDecoder.decode(name, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return name;
        }
    }

    private Path resolveUploadRoot() {
        if (configuredUploadDir != null && !configuredUploadDir.isBlank()) {
            return Path.of(configuredUploadDir).toAbsolutePath().normalize();
        }
        return Path.of(System.getProperty("java.io.tmpdir"), "xiyu-doc-insight-uploads")
                .toAbsolutePath().normalize();
    }

    private String inferContentType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".doc")) return "application/msword";
        if (lower.endsWith(".docx"))
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (lower.endsWith(".xls")) return "application/vnd.ms-excel";
        if (lower.endsWith(".xlsx"))
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (lower.endsWith(".ppt")) return "application/vnd.ms-powerpoint";
        if (lower.endsWith(".pptx"))
            return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        if (lower.endsWith(".txt")) return "text/plain";
        return "application/octet-stream";
    }

    private String sanitizeFilename(String fileName) {
        return fileName.replaceAll("[\\\\/:*?\"<>|]+", "_");
    }
}
