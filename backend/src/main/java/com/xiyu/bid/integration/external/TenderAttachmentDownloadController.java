// Input: HTTP GET /api/integration/tenders/attachments/download?fileUrl=...
// Output: 文件流响应（Content-Disposition: attachment）
// Pos: integration.external — CRM 跨系统附件下载 REST 入口（X-API-Key 认证）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
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
 * <p>本端点位于 {@code /api/integration/tenders/attachments/download}，
 * 方法级 {@code @PreAuthorize("permitAll()")} 公开访问，无需 XiYu 登录态也无需 API Key
 * （CO-280 设计：CRM 用户浏览器点击即下载，无法携带认证凭证）。
 *
 * <p><b>安全姿态（URL-as-capability）</b>：持有 URL 即可下载，URL 本身即访问凭证，
 * 且端点 URL 永久有效（obs-direct 场景下载时实时签发 1 小时预签名 URL 后 302 重定向）。
 * 若未来需要收紧，可改为要求 X-API-Key（{@code ApiKeyAuthenticationFilter} 已支持
 * Header 与 URL 参数两种方式），但需 CRM 方配合改造，属集成契约变更。
 *
 * <p>支持的 fileUrl 格式：
 * <ul>
 *   <li>{@code obs-direct:{uploadId}}：OBS 直传文件，实时签发预签名 URL 后 302 重定向</li>
 *   <li>{@code doc-insight://<relativePath>}：XiYu 本地存储文件</li>
 *   <li>{@code http(s)://<url>}：外部 URL 代理下载（CRM 推送附件）</li>
 * </ul>
 *
 * <p>认证要求：无（{@code permitAll()}，见上方安全姿态说明）。
 * 详见 {@code SecurityConfig}。
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
     * <p>支持 {@code obs-direct:}（302 重定向 OBS）、{@code doc-insight://}（本地存储）
     * 和 {@code http(s)://}（外部代理下载）三种 URL 格式。
     * 响应头包含 {@code Content-Disposition: attachment}，浏览器会触发下载而非导航。
     *
     * @param fileUrl 文件 URL，支持 obs-direct: / doc-insight:// / http(s):// 开头
     * @return 文件流响应
     * @throws org.springframework.web.server.ResponseStatusException 400（参数非法）/ 404（文件不存在）/ 502（外部下载失败）
     */
    @GetMapping("/download")
    @Operation(summary = "CRM 附件下载", description = "下载附件（公开访问无需认证），支持 obs-direct:（302 重定向 OBS）/ doc-insight:// / http(s):// 三种 URL 格式")
    public ResponseEntity<Resource> download(@RequestParam("fileUrl") String fileUrl) {
        log.info("INTEGRATION GET /api/integration/tenders/attachments/download - fileUrl={}",
                fileUrl != null && fileUrl.length() > 100 ? fileUrl.substring(0, 100) + "..." : fileUrl);
        return downloadService.download(fileUrl);
    }
}
