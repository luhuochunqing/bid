// Input: fileUrl 字符串（doc-insight:// / http(s):// / /api/... 相对路径）
// Output: 转换后的下载 URL（XiYu 内部端点 或 CRM 集成端点）
// Pos: integration.external — 附件 URL 转换器，承载 publicBaseUrl 配置和端点选择逻辑
package com.xiyu.bid.integration.external;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 附件下载 URL 转换器（CO-280 403 修复）。
 *
 * <p>承载两条 URL 转换路径：
 * <ul>
 *   <li>{@link #toDownloadUrl}：XiYu 内部下载端点 {@code /api/doc-insight/download}，
 *       需要 XiYu 登录态。适用于 XiYu 内部用户（通过 {@code TenderQueryService} / {@code TenderMapper}）。</li>
 *   <li>{@link #toIntegrationDownloadUrl}：CRM 集成下载端点 {@code /api/integration/tenders/attachments/download}，
 *       走 X-API-Key 认证。适用于 CRM 跨系统访问。</li>
 * </ul>
 *
 * <p>从 {@code TenderIntegrationMapper} 抽取，避免 mapper 类超过 300 行行数预算。
 * {@code publicBaseUrl} 配置通过 {@link Value @Value} 注入到静态字段，
 * 使静态方法也能读取。
 */
@Component
public class TenderAttachmentUrlResolver {

    /**
     * 公开端点根地址（如 https://winbid-test.ehsy.com）。
     * 用于生成可跨域访问的完整下载 URL，供外部系统（如 CRM）直接渲染。
     * 开发环境默认为空，返回相对路径（同源部署）。
     */
    private static String publicBaseUrl;

    @Value("${xiyu.public-base-url:}")
    public void setPublicBaseUrl(String value) {
        TenderAttachmentUrlResolver.publicBaseUrl = value;
    }

    /**
     * 构造 XiYu 内部下载端点 URL。
     *
     * <p>生成 {@code /api/doc-insight/download} 端点 URL，
     * 需要 XiYu 登录态（{@code @PreAuthorize("isAuthenticated()")}）。
     * 适用于 XiYu 内部用户访问附件的场景。
     *
     * <p>若配置了 xiyu.public-base-url，返回完整 URL（供跨域访问）；
     * 否则返回相对路径（同源部署场景）。
     * 幂等：已是下载地址的不再二次包装（CO-283）。
     */
    public static String toDownloadUrl(String u) {
        if (u == null || u.isBlank()) {
            return u;
        }
        if (u.startsWith("/api/doc-insight/download?")) {
            return prependPublicBaseUrl(u);
        }
        if (u.startsWith("doc-insight://")) {
            return prependPublicBaseUrl("/api/doc-insight/download?fileUrl="
                    + URLEncoder.encode(u, StandardCharsets.UTF_8));
        }
        return u;
    }

    /**
     * 构造 CRM 集成下载端点 URL（CO-280 403 修复）。
     *
     * <p>生成 {@code /api/integration/tenders/attachments/download} 端点 URL，
     * 该端点走 {@code ApiKeyAuthenticationFilter}（X-API-Key 头），不需要 XiYu 登录态。
     * 适用于 CRM 跨系统访问附件的场景。
     *
     * <p>若配置了 xiyu.public-base-url，返回完整 URL（供 CRM 跨域访问）；
     * 否则返回相对路径。
     *
     * @param u 原始 fileUrl，支持 doc-insight:// / http(s):// / 已是下载地址的 URL
     * @return 集成下载端点 URL
     */
    public static String toIntegrationDownloadUrl(String u) {
        if (u == null || u.isBlank()) {
            return u;
        }
        // 已是集成下载地址，幂等返回
        if (u.startsWith("/api/integration/tenders/attachments/download?")) {
            return prependPublicBaseUrl(u);
        }
        // 旧 /api/doc-insight/download? 格式重定向到新端点
        if (u.startsWith("/api/doc-insight/download?")) {
            String params = u.substring("/api/doc-insight/download?".length());
            return prependPublicBaseUrl("/api/integration/tenders/attachments/download?" + params);
        }
        // doc-insight:// 转换为集成下载端点
        if (u.startsWith("doc-insight://")) {
            return prependPublicBaseUrl("/api/integration/tenders/attachments/download?fileUrl="
                    + URLEncoder.encode(u, StandardCharsets.UTF_8));
        }
        // http(s):// 外部 URL 原样返回
        return u;
    }

    /**
     * 将相对路径 /api/... 补全为完整 URL（若配置了 publicBaseUrl）。
     * 用于处理已被 TenderMapper.toDTO() 转换过的 URL（doc-insight:// → /api/...）。
     * http(s):// 等已是完整 URL 的直接返回。
     *
     * <p>注意：此方法生成的是 XiYu 内部下载端点 URL，需要 XiYu 登录态。
     */
    public static String toFullUrl(String url) {
        if (url == null) return null;
        if (url.startsWith("doc-insight://")) {
            return toDownloadUrl(url);
        }
        if (url.startsWith("/api/")) {
            return prependPublicBaseUrl(url);
        }
        return url;
    }

    /**
     * 将 doc-insight:// 格式的 URL 转换为 CRM 集成下载端点 URL（CO-280 403 修复）。
     * 同时处理已被 TenderMapper.toDTO() 转换为 /api/... 相对路径的 URL。
     * http(s):// 等已是完整 URL 的直接返回。
     */
    public static String toIntegrationFullUrl(String url) {
        if (url == null) return null;
        if (url.startsWith("doc-insight://")) {
            return toIntegrationDownloadUrl(url);
        }
        // 旧 /api/doc-insight/download? 格式重定向到新端点
        if (url.startsWith("/api/doc-insight/download?")) {
            String params = url.substring("/api/doc-insight/download?".length());
            return prependPublicBaseUrl("/api/integration/tenders/attachments/download?" + params);
        }
        // 已是集成下载地址
        if (url.startsWith("/api/integration/tenders/attachments/download?")) {
            return prependPublicBaseUrl(url);
        }
        if (url.startsWith("/api/")) {
            return prependPublicBaseUrl(url);
        }
        return url;
    }

    /** 若配置了 publicBaseUrl，将相对路径补全为完整 URL；否则原样返回。 */
    private static String prependPublicBaseUrl(String relative) {
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            return relative;
        }
        return publicBaseUrl + relative;
    }
}
