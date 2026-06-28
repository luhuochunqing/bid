// Input: HTTP GET /api/integration/tenders/attachments/download?fileUrl=...
// Output: 文件流响应（Content-Disposition: attachment）
// Pos: integration.external — CRM 跨系统附件下载 REST 入口（X-API-Key 认证）
package com.xiyu.bid.integration.external;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * CRM 跨系统附件下载端点（CO-280 403 修复）。
 *
 * <p>背景：CRM 用户点击附件时浏览器直接访问 XiYu 下载端点。原
 * {@code /api/doc-insight/download} 受 {@code @PreAuthorize("isAuthenticated()")} 保护，
 * CRM 用户无 XiYu 登录态 → 403 Forbidden。
 *
 * <p>本端点位于 {@code /api/integration/tenders/attachments/download}，由
 * {@code ApiKeyAuthenticationFilter} 通过 {@code X-API-Key} 头认证，
 * 不需要 XiYu 登录态。CRM 后端调用此端点获取文件流后转发给前端，
 * 或 CRM 前端直接发起请求（需在 URL 或头中携带 API Key）。
 *
 * <p>支持的 fileUrl 格式：
 * <ul>
 *   <li>{@code doc-insight://<relativePath>}：XiYu 本地存储文件</li>
 *   <li>{@code http(s)://<url>}：外部 URL 代理下载（CRM 推送附件）</li>
 * </ul>
 *
 * <p>认证要求：{@code X-API-Key} 头 + {@code ROLE_EXTERNAL_API} 权限。
 * 详见 {@code ApiKeyAuthenticationFilter} 和 {@code SecurityConfig}。
 */
@RestController
@Tag(name = "CRM 附件下载（外部API v2.0）", description = "CRM 跨系统附件下载端点，公开访问无需认证")
@RequestMapping("/api/integration/tenders/attachments")
@PreAuthorize("permitAll()")
@RequiredArgsConstructor
@Slf4j
public class TenderAttachmentDownloadController {

    private final TenderAttachmentDownloadService downloadService;

    /**
     * 下载附件文件。
     *
     * <p>支持 {@code doc-insight://}（本地存储）和 {@code http(s)://}（外部代理下载）两种 URL 格式。
     * 响应头包含 {@code Content-Disposition: attachment}，浏览器会触发下载而非导航。
     *
     * @param fileUrl 文件 URL，必须以 {@code doc-insight://} 或 {@code http(s)://} 开头
     * @return 文件流响应
     * @throws org.springframework.web.server.ResponseStatusException 400（参数非法）/ 404（文件不存在）/ 502（外部下载失败）
     */
    @GetMapping("/download")
    @Operation(summary = "CRM 附件下载", description = "通过 X-API-Key 认证下载附件，支持 doc-insight:// 和 http(s):// 两种 URL 格式")
    public ResponseEntity<Resource> download(@RequestParam("fileUrl") String fileUrl) {
        log.info("INTEGRATION GET /api/integration/tenders/attachments/download - fileUrl={}",
                fileUrl != null && fileUrl.length() > 100 ? fileUrl.substring(0, 100) + "..." : fileUrl);
        return downloadService.download(fileUrl);
    }
}
