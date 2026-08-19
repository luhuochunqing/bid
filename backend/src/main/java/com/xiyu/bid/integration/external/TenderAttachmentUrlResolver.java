// Input: fileUrl 字符串（obs-direct: / doc-insight:// / http(s):// / /api/... 相对路径）
// Output: 转换后的下载 URL（OBS 预签名 URL / XiYu 内部端点 / CRM 集成端点）
// Pos: integration.external — 附件 URL 转换器，按调用方上下文选择端点与认证方式
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.integration.external;

import com.xiyu.bid.file.application.ObsShareUrlSigner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * <p>三条转换路径：obs-direct:{uploadId}：内部用户 → OBS 预签名 URL（{@link ObsShareUrlSigner}），
 * 外部系统 → 集成下载端点 URL（永久有效，下载时实时签发）；
 * doc-insight:// → XiYu 内部端点；http(s):// → 原样返回。
 *
 * <p>依赖均走构造器注入：端点 URL 构造委托 {@link IntegrationEndpointUrls}
 * （承载 publicBaseUrl 配置）；{@code obsShareUrlSigner} 单元测试中可传 null。
 */
@Component
public class TenderAttachmentUrlResolver {

    private static final Logger log = LoggerFactory.getLogger(TenderAttachmentUrlResolver.class);

    private final IntegrationEndpointUrls endpointUrls;
    private final ObsShareUrlSigner obsShareUrlSigner;

    public TenderAttachmentUrlResolver(IntegrationEndpointUrls endpointUrls, ObsShareUrlSigner obsShareUrlSigner) {
        this.endpointUrls = endpointUrls;
        this.obsShareUrlSigner = obsShareUrlSigner;
    }

    /**
     * 尝试将 obs-direct:{uploadId} 转换为 OBS 预签名 URL。
     * 非 obs-direct: 前缀返回 empty（让后续逻辑接管）；签发失败也返回 empty。
     */
    private Optional<String> tryResolveObsDirect(String url) {
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
    public String toDownloadUrl(String u) {
        if (u == null || u.isBlank()) {
            return u;
        }
        // obs-direct: → OBS 预签名 URL（签发失败回退为原值）
        Optional<String> obsSigned = tryResolveObsDirect(u);
        if (obsSigned.isPresent()) {
            return obsSigned.get();
        }
        if (u.startsWith(IntegrationEndpointUrls.LEGACY_DOC_INSIGHT_QUERY_PREFIX)) {
            return endpointUrls.prependBase(u);
        }
        if (u.startsWith("doc-insight://")) {
            return endpointUrls.prependBase(IntegrationEndpointUrls.LEGACY_DOC_INSIGHT_QUERY_PREFIX + "fileUrl="
                    + URLEncoder.encode(u, StandardCharsets.UTF_8));
        }
        return u;
    }

    /**
     * 构造 CRM 集成下载端点 URL（CO-280 403 修复）。
     *
     * <p>生成 {@code /api/integration/tenders/attachments/download} 端点 URL，
     * 该端点公开访问（permitAll，URL-as-capability），不需要 XiYu 登录态。
     * 适用于 CRM 跨系统访问附件的场景。
     *
     * <p>若配置了 xiyu.public-base-url，返回完整 URL（供 CRM 跨域访问）；
     * 否则返回相对路径。
     *
     * @param u 原始 fileUrl，支持 doc-insight:// / http(s):// / 已是下载地址的 URL
     * @return 集成下载端点 URL
     */
    public String toIntegrationDownloadUrl(String u) {
        if (u == null || u.isBlank()) {
            return u;
        }
        String endpointUrl = toIntegrationEndpointUrlOrNull(u);
        if (endpointUrl != null) {
            return endpointUrl;
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
    public String toFullUrl(String url) {
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
            return endpointUrls.prependBase(url);
        }
        return url;
    }

    /**
     * 将 doc-insight:// 格式的 URL 转换为 CRM 集成下载端点 URL（CO-280 403 修复）。
     * 同时处理已被 TenderMapper.toDTO() 转换为 /api/... 相对路径的 URL。
     * http(s):// 等已是完整 URL 的直接返回。
     */
    public String toIntegrationFullUrl(String url) {
        if (url == null) return null;
        String endpointUrl = toIntegrationEndpointUrlOrNull(url);
        if (endpointUrl != null) {
            return endpointUrl;
        }
        if (url.startsWith("/api/")) {
            return endpointUrls.prependBase(url);
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
    public String toIntegrationFullUrl(String url, String apiKey) {
        String result = toIntegrationFullUrl(url);
        if (apiKey != null && !apiKey.isBlank() && endpointUrls.isOwnIntegrationDownloadUrl(result)) {
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
    public String resolve(String fileUrl, CallerContext context) {
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
    public List<String> resolveBatch(List<String> fileUrls, CallerContext context) {
        if (fileUrls == null) return null;
        List<String> result = new ArrayList<>(fileUrls.size());
        for (String url : fileUrls) {
            result.add(resolve(url, context));
        }
        return result;
    }

    /**
     * 集成下载端点的共享分发（toIntegrationDownloadUrl / toIntegrationFullUrl 共用，避免双入口重复实现）。
     *
     * <p>按优先级处理：obs-direct:（包装为永久端点 URL，端点内实时签发预签名 URL，
     * 修复 CRM 侧链接 1 小时过期）→ 已是集成端点（幂等）→ 旧 doc-insight 端点（重定向）
     * → doc-insight://（转换）；均不命中返回 null 由调用方接管。
     */
    private String toIntegrationEndpointUrlOrNull(String u) {
        if (u.startsWith(ObsShareUrlSigner.OBS_DIRECT_PREFIX)) {
            return wrapObsDirectAsIntegrationUrl(u);
        }
        if (u.startsWith(IntegrationEndpointUrls.INTEGRATION_DOWNLOAD_QUERY_PREFIX)) {
            return endpointUrls.prependBase(u);
        }
        String legacyDocInsightParams = endpointUrls.extractOwnLegacyDocInsightParams(u);
        if (legacyDocInsightParams != null) {
            return endpointUrls.prependBase(
                    IntegrationEndpointUrls.INTEGRATION_DOWNLOAD_QUERY_PREFIX + legacyDocInsightParams);
        }
        if (u.startsWith("doc-insight://")) {
            return endpointUrls.prependBase(IntegrationEndpointUrls.INTEGRATION_DOWNLOAD_QUERY_PREFIX + "fileUrl="
                    + URLEncoder.encode(u, StandardCharsets.UTF_8));
        }
        return null;
    }

    /**
     * 将 obs-direct:{uploadId} 包装为 CRM 集成下载端点 URL。
     *
     * <p>端点 URL 本身永久有效（fileUrl 参数为伪协议原值），CRM 每次访问时由
     * TenderAttachmentDownloadService 实时签发预签名 URL 并 302 重定向，
     * 从根本上解决回调下发的 URL 过期问题。</p>
     */
    private String wrapObsDirectAsIntegrationUrl(String u) {
        if (!endpointUrls.hasPublicBaseUrl()) {
            log.warn("xiyu.public-base-url 未配置，外部系统收到的 obs-direct 下载 URL 为相对路径（{}），"
                    + "跨系统场景将不可用，请检查部署配置", u);
        }
        return endpointUrls.prependBase(IntegrationEndpointUrls.INTEGRATION_DOWNLOAD_QUERY_PREFIX + "fileUrl="
                + URLEncoder.encode(u, StandardCharsets.UTF_8));
    }

    /** 附加 api_key 查询参数到 URL，正确处理已有 query string 的情况。 */
    private static String appendApiKeyParam(String url, String apiKey) {
        char separator = url.contains("?") ? '&' : '?';
        return url + separator + DEFAULT_QUERY_PARAM + "=" + apiKey;
    }
}
