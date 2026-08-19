// Input: 相对路径 / 端点 URL / 待追加的 api_key
// Output: 补全 publicBaseUrl 的完整端点 URL、legacy 参数提取结果、端点归属判断
// Pos: integration.external — 附件下载端点 URL 构造助手（承载 publicBaseUrl 配置，供 TenderAttachmentUrlResolver 使用）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.integration.external;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 附件下载端点 URL 构造助手（CO-280 403 修复体系成员）。
 *
 * <p>承载 {@code xiyu.public-base-url} 配置与两个下载端点的路径常量，
 * 提供纯 URL 构造/判断能力：相对路径补全、legacy {@code /api/doc-insight/download}
 * 参数提取、集成下载端点归属判断。转换策略编排见 {@link TenderAttachmentUrlResolver}。
 */
@Component
public class IntegrationEndpointUrls {

    /** CRM 集成下载端点路径（permitAll，URL-as-capability，见 TenderAttachmentDownloadController）。 */
    public static final String INTEGRATION_DOWNLOAD_PATH = "/api/integration/tenders/attachments/download";

    /** 集成下载端点带 query 前缀（拼接参数用）。 */
    public static final String INTEGRATION_DOWNLOAD_QUERY_PREFIX = INTEGRATION_DOWNLOAD_PATH + "?";

    /** 旧 XiYu 内部下载端点路径（CO-280 前的附件下载地址，需重定向到新端点）。 */
    public static final String LEGACY_DOC_INSIGHT_PATH = "/api/doc-insight/download";

    /** 旧端点带 query 前缀（legacy 参数提取用）。 */
    public static final String LEGACY_DOC_INSIGHT_QUERY_PREFIX = LEGACY_DOC_INSIGHT_PATH + "?";

    /** 公开端点根地址（如 https://winbid-test.ehsy.com），未配置时为空串（同源部署）。 */
    private final String publicBaseUrl;

    public IntegrationEndpointUrls(@Value("${xiyu.public-base-url:}") String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    /** 是否配置了 publicBaseUrl（未配置时相对路径无法跨系统访问）。 */
    public boolean hasPublicBaseUrl() {
        return publicBaseUrl != null && !publicBaseUrl.isBlank();
    }

    /** 若配置了 publicBaseUrl，将相对路径补全为完整 URL；否则原样返回。 */
    public String prependBase(String relative) {
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            return relative;
        }
        return publicBaseUrl + relative;
    }

    /** publicBaseUrl + 指定路径的完整前缀；未配置 publicBaseUrl 时返回 null。 */
    private String ownAbsolutePathPrefix(String path) {
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            return null;
        }
        String baseUrl = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
        return baseUrl + path;
    }

    /** 判断 URL 是否指向本平台的集成下载端点（相对路径或 publicBaseUrl 绝对路径）。 */
    public boolean isOwnIntegrationDownloadUrl(String url) {
        if (url == null) return false;
        if (url.startsWith(INTEGRATION_DOWNLOAD_PATH)) {
            return true;
        }
        String ownAbsolute = ownAbsolutePathPrefix(INTEGRATION_DOWNLOAD_PATH);
        return ownAbsolute != null && url.startsWith(ownAbsolute);
    }

    /**
     * 提取本平台旧 doc-insight 端点 URL 的 query 参数部分。
     * 兼容相对路径与 publicBaseUrl 绝对路径；非旧端点 URL 返回 null。
     */
    public String extractOwnLegacyDocInsightParams(String url) {
        if (url.startsWith(LEGACY_DOC_INSIGHT_QUERY_PREFIX)) {
            return url.substring(LEGACY_DOC_INSIGHT_QUERY_PREFIX.length());
        }
        String ownAbsoluteLegacy = ownAbsolutePathPrefix(LEGACY_DOC_INSIGHT_PATH);
        if (ownAbsoluteLegacy != null && url.startsWith(ownAbsoluteLegacy + "?")) {
            return url.substring(ownAbsoluteLegacy.length() + 1);
        }
        return null;
    }
}
