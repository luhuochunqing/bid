// Input: fileUrl（obs-direct: OBS 直传 / doc-insight:// 本地路径 / http(s):// 外部 URL）
// Output: ResponseEntity<Resource> 文件流 + Content-Disposition 头；obs-direct 走 302 预签名重定向；非法路径抛 ResponseStatusException
// Pos: integration.external — CRM 跨系统附件下载服务，复用本地存储与外部代理下载逻辑
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.integration.external;

import com.xiyu.bid.file.application.ObsShareUrlSigner;
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
import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

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
 *   <li>{@code obs-direct:{uploadId}}：OBS 直传文件，实时签发预签名 URL 后 302 重定向</li>
 *   <li>{@code http(s)://<url>}：外部 URL 代理下载（CRM 推送附件）</li>
 * </ul>
 */
@Service
public class TenderAttachmentDownloadService {

    private final ObsShareUrlSigner obsShareUrlSigner;

    public TenderAttachmentDownloadService(ObsShareUrlSigner obsShareUrlSigner) {
        this.obsShareUrlSigner = obsShareUrlSigner;
    }
    /** 文件存储目录，与 LocalDocumentStorage / DocInsightController 共享同一配置。 */
    @Value("${app.doc-insight.upload-dir:}")
    private String configuredUploadDir;

    /** 外部下载域名白名单（逗号分隔），为空时仅阻止私有地址。 */
    @Value("${app.integration.external-download-allowed-hosts:}")
    private String allowedExternalHosts;

    /**
     * 下载文件。
     *
     * @param fileUrl 文件 URL，支持 obs-direct: / doc-insight:// 和 http(s)://
     * @return 文件流响应；obs-direct: 返回 302 重定向到实时签发的预签名 URL
     * @throws ResponseStatusException 400（参数非法）/ 404（文件不存在）/ 502（外部下载失败）
     */
    public ResponseEntity<Resource> download(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件 URL 不能为空");
        }
        if (fileUrl.startsWith("http://") || fileUrl.startsWith("https://")) {
            return downloadExternal(fileUrl);
        }
        if (fileUrl.startsWith(ObsShareUrlSigner.OBS_DIRECT_PREFIX)) {
            return downloadObsDirect(fileUrl);
        }
        if (!fileUrl.startsWith("doc-insight://")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无效的文件 URL 格式");
        }
        return downloadLocal(fileUrl);
    }

    /** OBS 直传文件：实时签发预签名 URL 后 302 重定向（与 ArchiveFileResponseFactory 行为对齐）。 */
    private ResponseEntity<Resource> downloadObsDirect(String fileUrl) {
        String signedUrl = obsShareUrlSigner.trySign(fileUrl)
                .filter(url -> !url.equals(fileUrl))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "OBS 文件不存在或不可用"));
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(signedUrl)).build();
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
            validateExternalUrl(uri);
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

    /**
     * 校验外部 URL，防止 SSRF 攻击。
     *
     * <p>检查规则：
     * <ul>
     *   <li>协议必须是 http 或 https</li>
     *   <li>主机名不能为 localhost 或私有 IP 地址（RFC 1918 + 环回 + 链路本地）</li>
     *   <li>若配置了域名白名单，主机名必须在白名单中</li>
     * </ul>
     */
    void validateExternalUrl(URI uri) {
        String scheme = uri.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "外部 URL 协议必须是 http 或 https");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "外部 URL 缺少有效主机名");
        }
        String normalizedHost = host.toLowerCase().trim();
        if (isPrivateOrLoopbackAddress(normalizedHost)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "禁止访问内部网络地址");
        }
        if (allowedExternalHosts != null && !allowedExternalHosts.isBlank()) {
            Set<String> allowed = Arrays.stream(allowedExternalHosts.split(","))
                    .map(String::trim)
                    .map(String::toLowerCase)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet());
            if (!allowed.contains(normalizedHost)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "外部 URL 域名不在允许列表中");
            }
        }
    }

    /** 判断主机名是否为 localhost 或私有 IP 地址（RFC 1918 / 环回 / 链路本地 / 任意本地）。 */
    static boolean isPrivateOrLoopbackAddress(String host) {
        if ("localhost".equalsIgnoreCase(host)) {
            return true;
        }
        if (host.startsWith("[") && host.endsWith("]")) {
            // IPv6 字面量，如 [::1]
            String inner = host.substring(1, host.length() - 1);
            if ("::1".equals(inner)) {
                return true;
            }
            try {
                InetAddress addr = InetAddress.getByName(inner);
                return addr.isLoopbackAddress() || addr.isSiteLocalAddress()
                        || addr.isAnyLocalAddress() || addr.isLinkLocalAddress();
            } catch (UnknownHostException e) {
                return true;
            }
        }
        try {
            InetAddress addr = InetAddress.getByName(host);
            return addr.isLoopbackAddress() || addr.isSiteLocalAddress()
                    || addr.isAnyLocalAddress() || addr.isLinkLocalAddress();
        } catch (UnknownHostException e) {
            // 域名无法解析时不视为私有地址（可能仅是 DNS 暂时不可达）
            return false;
        }
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
