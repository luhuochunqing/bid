package com.xiyu.bid.casework.application;

import com.xiyu.bid.casework.infrastructure.ArchiveFile;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.file.application.ObsShareUrlSigner;
import com.xiyu.bid.file.domain.FileUrlPrefixes;
import com.xiyu.bid.shared.security.FilePathGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 项目档案文件响应工厂：按 file_path 存储类型构建预览/下载响应。
 *
 * <p>spec 039 修复：archive_file.file_path 存在两类值——
 * <ul>
 *   <li>本地物理路径（multipart 上传归档）→ 校验边界后流式返回 200</li>
 *   <li>{@code obs-direct:{uploadId}} 伪协议（OBS 直传归档，含 V1171 回填）→
 *       委托 {@link ObsShareUrlSigner} 签发预签名 URL，返回 302 让浏览器直连 OBS，
 *       与项目文档下载（ProjectDocumentDownloadService）的处理方式对齐</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class ArchiveFileResponseFactory {

    private final ObsShareUrlSigner obsShareUrlSigner;

    @Value("${app.doc-insight.upload-dir:}")
    private String configuredUploadDir;

    /**
     * 构建档案文件响应。
     *
     * @param file   归档文件记录
     * @param inline true=预览（inline + 推断 Content-Type），false=下载（attachment + octet-stream）
     */
    public ResponseEntity<Resource> build(ArchiveFile file, boolean inline) {
        String rawPath = file.getFilePath();
        if (rawPath != null && rawPath.startsWith(FileUrlPrefixes.OBS_DIRECT)) {
            // OBS 直传文件不在本地磁盘，签发预签名 URL 后 302 重定向；签发失败按资源不存在处理
            String signedUrl = obsShareUrlSigner.trySign(rawPath)
                    .filter(url -> !url.equals(rawPath))
                    .orElseThrow(() -> ResourceNotFoundException.withMessage(
                            "Archive file not available: " + file.getId()));
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(signedUrl)).build();
        }

        Path filePath = resolveAndValidateFilePath(rawPath);
        long fileSize;
        try {
            fileSize = Files.size(filePath);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("无法读取文件大小: " + rawPath, e);
        }

        String fileName = file.getFileName();
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION,
                (inline ? "inline" : "attachment") + "; filename=\"" + sanitizeFilename(fileName) + "\"");
        headers.setContentType(inline
                ? MediaType.parseMediaType(inferContentType(fileName))
                : MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentLength(fileSize);
        return new ResponseEntity<>(new FileSystemResource(filePath), headers, HttpStatus.OK);
    }

    /** CO-430: 获取 upload 根目录绝对路径。 */
    private String getArchiveFileBaseDir() {
        return Path.of((configuredUploadDir == null || configuredUploadDir.isBlank())
                        ? System.getProperty("java.io.tmpdir") + "/xiyu-doc-insight-uploads"
                        : configuredUploadDir)
                .toAbsolutePath().normalize().toString();
    }

    /** 解析并验证文件路径，防止路径遍历攻击。 */
    private Path resolveAndValidateFilePath(String rawPath) {
        return FilePathGuard.ensureExists(
                FilePathGuard.resolveAbsoluteWithin(rawPath, getArchiveFileBaseDir()), rawPath);
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
}
