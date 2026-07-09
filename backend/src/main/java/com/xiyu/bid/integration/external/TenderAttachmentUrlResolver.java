// Input: fileUrl 字符串（obs-direct: / doc-insight:// / http(s):// / /api/... 相对路径）
// Output: 转换后的下载 URL（OBS 预签名 URL / XiYu 内部端点 / CRM 集成端点）
// Pos: integration.external — 附件 URL 转换器，承载 publicBaseUrl 配置和端点选择逻辑
package com.xiyu.bid.integration.external;

import com.xiyu.bid.file.application.ObsShareUrlSigner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.xiyu.bid.apikey.infrastructure.ApiKeyAuthConstants.DEFAULT_QUERY_PARAM;

/**
 * 附件下载 URL 转换器（CO-280 403 修复）。
 *
 * <p>三条转换路径：obs-direct:{uploadId} → OBS 预签名 URL（{@link ObsShareUrlSigner}）；
 * doc-insight:// → XiYu 内部端点；http(s):// → 原样返回。
 * {@code publicBaseUrl} 通过 {@link Value @Value} 注入静态字段，
 * {@code obsShareUrlSigner} 通过 {@link Autowired @Autowired} 注入静态字段。
 */
@Component
public class TenderAttachmentUrlResolver {

    /** 公开端点根地址（如 https://winbid-test.ehsy.com）。 */
    private static String publicBaseUrl;

    @Value("${xiyu.public-base-url:}")
    public void setPublicBaseUrl(String value) {
        TenderAttachmentUrlResolver.publicBaseUrl = value;
    }

    /** OBS 分享 URL 签发器，单元测试中为 null。 */
    private static ObsShareUrlSigner obsShareUrlSigner;

    @Autowired
    public void setObsShareUrlSigner(ObsShareUrlSigner signer) {
        TenderAttachmentUrlResolver.obsShareUrlSigner = signer;
    }

    /**
     * 尝试将 obs-direct:{uploadId} 转换为 OBS 预签名 URL。
     * 非 obs-direct: 前缀返回 empty（让后续逻辑接管）；签发失败也返回 empty。
     */
    private static Optional<String> tryResolveObsDirect(String url) {
        if (url == null || !url.startsWith(ObsShareUrlSigner.OBS_DIRECT_PREFIX)) {
            return Optional.empty();
        }
        if (obsShareUrlSigner == null) {
            return Optional.empty();
        }
        return obsShareUrlSigner.trySign(url);
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
        // obs-direct: → OBS 预签名 URL（签发失败回退为原值）
        Optional<String> obsSigned = tryResolveObsDirect(u);
        if (obsSigned.isPresent()) {
            return obsSigned.get();
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
        // obs-direct: → OBS 预签名 URL（签发失败回退为原值）
        Optional<String> obsSigned = tryResolveObsDirect(u);
        if (obsSigned.isPresent()) {
            return obsSigned.get();
        }
        // 已是集成下载地址，幂等返回
        if (u.startsWith("/api/integration/tenders/attachments/download?")) {
            return prependPublicBaseUrl(u);
        }
        // 旧 /api/doc-insight/download? 格式重定向到新端点
        String legacyDocInsightParams = extractOwnLegacyDocInsightParams(u);
        if (legacyDocInsightParams != null) {
            return prependPublicBaseUrl("/api/integration/tenders/attachments/download?" + legacyDocInsightParams);
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
        // obs-direct: → OBS 预签名 URL（签发失败回退为原值）
        Optional<String> obsSigned = tryResolveObsDirect(url);
        if (obsSigned.isPresent()) {
            return obsSigned.get();
        }
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
        // obs-direct: → OBS 预签名 URL（签发失败回退为原值）
        Optional<String> obsSigned = tryResolveObsDirect(url);
        if (obsSigned.isPresent()) {
            return obsSigned.get();
        }
        if (url.startsWith("doc-insight://")) {
            return toIntegrationDownloadUrl(url);
        }
        // 旧 /api/doc-insight/download? 格式重定向到新端点
        String legacyDocInsightParams = extractOwnLegacyDocInsightParams(url);
        if (legacyDocInsightParams != null) {
            return prependPublicBaseUrl("/api/integration/tenders/attachments/download?" + legacyDocInsightParams);
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

    /**
     * 将 URL 转换为集成下载端点，并附加 api_key 查询参数（CO-280 修复）。
     *
     * <p>CRM 用户在浏览器中直接点击下载链接时，无法携带自定义 HTTP Header。
     * 通过将 api_key 附加到 URL 查询参数，配合 {@code ApiKeyAuthenticationFilter}
     * 同时支持 Header 和 URL 参数两种认证方式，实现"点击即下载"。
     *
     * @param url 原始 fileUrl（doc-insight:// / http(s):// / 已是下载地址的 URL）
     * @param apiKey 明文 API Key（为 null 或空串时退化为无参数版本）
     * @return 集成下载端点 URL，含 api_key 查询参数（当 apiKey 非空时）
     */
    public static String toIntegrationFullUrl(String url, String apiKey) {
        String result = toIntegrationFullUrl(url);
        if (apiKey != null && !apiKey.isBlank() && isOwnIntegrationDownloadUrl(result)) {
            return appendApiKeyParam(result, apiKey);
        }
        return result;
    }

    /**
     * 统一入口：根据调用方上下文选择正确的端点和认证方式。
     *
     * <ul>
     *   <li>内部用户 → {@link #toDownloadUrl(String)}</li>
     *   <li>外部系统且有 apiKey → {@link #toIntegrationFullUrl(String, String)}</li>
     *   <li>外部系统但无 apiKey → {@link #toIntegrationFullUrl(String)}</li>
     * </ul>
     */
    public static String resolve(String fileUrl, CallerContext context) {
        if (context == null || context.isInternalUser()) {
            return toDownloadUrl(fileUrl);
        }
        String apiKey = context.apiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            return toIntegrationFullUrl(fileUrl, apiKey);
        }
        return toIntegrationFullUrl(fileUrl);
    }

    /**
     * 批量转换 URL，返回与输入同大小的列表，null/空值保留原样。
     *
     * @see #resolve(String, CallerContext)
     */
    public static List<String> resolveBatch(List<String> fileUrls, CallerContext context) {
        if (fileUrls == null) return null;
        List<String> result = new ArrayList<>(fileUrls.size());
        for (String url : fileUrls) {
            result.add(resolve(url, context));
        }
        return result;
    }

    private static String extractOwnLegacyDocInsightParams(String url) {
        String legacyPath = "/api/doc-insight/download?";
        if (url.startsWith(legacyPath)) {
            return url.substring(legacyPath.length());
        }
        String ownAbsoluteLegacyPath = ownAbsolutePathPrefix(legacyPath);
        if (ownAbsoluteLegacyPath != null && url.startsWith(ownAbsoluteLegacyPath)) {
            return url.substring(ownAbsoluteLegacyPath.length());
        }
        return null;
    }

    private static boolean isOwnIntegrationDownloadUrl(String url) {
        if (url == null) return false;
        String integrationPath = "/api/integration/tenders/attachments/download";
        if (url.startsWith(integrationPath)) {
            return true;
        }
        String ownAbsoluteIntegrationPath = ownAbsolutePathPrefix(integrationPath);
        return ownAbsoluteIntegrationPath != null && url.startsWith(ownAbsoluteIntegrationPath);
    }

    private static String ownAbsolutePathPrefix(String path) {
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            return null;
        }
        String baseUrl = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
        return baseUrl + path;
    }

    /** 若配置了 publicBaseUrl，将相对路径补全为完整 URL；否则原样返回。 */
    private static String prependPublicBaseUrl(String relative) {
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            return relative;
        }
        return publicBaseUrl + relative;
    }

    /** 附加 api_key 查询参数到 URL，正确处理已有 query string 的情况。 */
    private static String appendApiKeyParam(String url, String apiKey) {
        char separator = url.contains("?") ? '&' : '?';
        return url + separator + DEFAULT_QUERY_PARAM + "=" + apiKey;
    }
}
