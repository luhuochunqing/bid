// Input: notification type / source entity / platform base URL
// Output: FormattedMessage record used to build 企微 textcard payload
// Pos: Pure Core/企微推送消息格式化器
package com.xiyu.bid.notification.outbound.core;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Pure formatter that assembles a WeCom textcard payload.
 *
 * <p>No Spring, no IO, no logging. Takes explicit inputs, returns a value.
 */
public final class WeComMessageFormatter {

    private static final int MAX_TITLE_LENGTH = 128;
    private static final int MAX_DESCRIPTION_LENGTH = 512;
    private static final String DEFAULT_BTN_TEXT = "查看详情";
    private static final String INBOX_PATH = "/inbox";

    // SSO OAuth 相关常量
    private static final String OAUTH_AUTHORIZE_URL = "https://open.weixin.qq.com/connect/oauth2/authorize";
    private static final String OAUTH_SCOPE = "snsapi_base";
    private static final String OAUTH_RESPONSE_TYPE = "code";
    private static final String OAUTH_REDIRECT_PATH = "/login";
    private static final String OAUTH_REDIRECT_PARAM = "redirect";

    /**
     * 消息推送场景的固定 state 值。
     * <p>WeComAuthController.callback 识别此值时跳过 Redis state 校验，
     * 因为消息推送是系统生成的 URL，用户点击是异步行为（可能几小时/几天后），
     * Redis 10 分钟 TTL 不适用。
     * <p>安全评估：state 的目的是防 CSRF。攻击者可构造 ?code=xxx&state=msg，
     * 但 code 是企微一次性凭证，攻击者拿到 code 也无法获取受害者身份。
     * 风险可接受。
     */
    public static final String OAUTH_STATE_FROM_MESSAGE = "msg";

    private WeComMessageFormatter() {
    }

    public record FormattedMessage(String title, String description, String url, String btnText) {
    }

    /**
     * 企微 SSO 配置参数。用于构造 OAuth 授权链接。
     * <p>null 或 {@link #isValid()} 为 false 时，format 返回直接业务 URL（向后兼容）。
     */
    public record WeComSsoParams(String corpId, String agentId) {
        public boolean isValid() {
            return corpId != null && !corpId.isBlank()
                && agentId != null && !agentId.isBlank();
        }
    }

    /**
     * 格式化企微 textcard payload。
     *
     * <p>URL 解析优先级（P0-1 修复）：
     * <ol>
     *   <li>{@code payloadTargetUrl} 非空且以 "/" 开头 → 拼接 platformBaseUrl + payloadTargetUrl</li>
     *   <li>否则回退到 sourceEntityType → 路径映射</li>
     *   <li>映射失败 → 回退到 /inbox</li>
     * </ol>
     *
     * <p>引入 payloadTargetUrl 的根因：某些通知（如文档变更 DOCUMENT_CHANGE）的合理跳转目标
     * 不是 sourceEntityType 对应的实体页（如 /document/editor/），而是关联项目的子页面
     * （/project/{id}/drafting）。payload.targetUrl 由业务侧精确指定，应优先于默认映射。
     */
    public static FormattedMessage format(
        String notificationTitle,
        String notificationType,
        String sourceEntityType,
        Long sourceEntityId,
        String platformBaseUrl
    ) {
        return format(notificationTitle, notificationType, sourceEntityType, sourceEntityId, platformBaseUrl, null, null);
    }

    public static FormattedMessage format(
        String notificationTitle,
        String notificationType,
        String sourceEntityType,
        Long sourceEntityId,
        String platformBaseUrl,
        String payloadTargetUrl
    ) {
        return format(notificationTitle, notificationType, sourceEntityType, sourceEntityId, platformBaseUrl, payloadTargetUrl, null);
    }

    /**
     * 格式化企微 textcard payload（支持 SSO OAuth 授权链接构造）。
     *
     * <p>当 {@code ssoParams} 有效时，URL 字段返回 OAuth 授权链接（而非直接业务 URL），
     * 用户点击消息后会先走企微 OAuth 静默授权，拿到 code 后回到
     * {@code /login?redirect=<原目标 path>&code=xxx&state=msg}，
     * 由 Login.vue 调 callback 接口自动登录并跳回原目标页面。
     *
     * @param ssoParams SSO 配置；null 或无效则返回直接业务 URL（向后兼容）
     */
    public static FormattedMessage format(
        String notificationTitle,
        String notificationType,
        String sourceEntityType,
        Long sourceEntityId,
        String platformBaseUrl,
        String payloadTargetUrl,
        WeComSsoParams ssoParams
    ) {
        String safeTitle = truncate(defaultIfBlank(notificationTitle, "新通知"), MAX_TITLE_LENGTH);
        String typeLabel = humanType(notificationType);
        String description = truncate(typeLabel + " · " + safeTitle, MAX_DESCRIPTION_LENGTH);
        String url = buildUrl(platformBaseUrl, sourceEntityType, sourceEntityId, payloadTargetUrl, ssoParams);
        return new FormattedMessage(safeTitle, description, url, DEFAULT_BTN_TEXT);
    }

    private static String buildUrl(
        String platformBaseUrl,
        String sourceEntityType,
        Long sourceEntityId,
        String payloadTargetUrl,
        WeComSsoParams ssoParams
    ) {
        String base = defaultIfBlank(platformBaseUrl, "");
        String normalizedBase = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;

        // 1. 解析目标业务 URL（原逻辑，保持向后兼容）
        String targetUrl;
        if (payloadTargetUrl != null && payloadTargetUrl.startsWith("/")) {
            targetUrl = normalizedBase + payloadTargetUrl;
        } else {
            String sourcePath = resolveSourcePath(sourceEntityType, sourceEntityId);
            if (sourcePath == null) {
                targetUrl = normalizedBase + INBOX_PATH;
            } else {
                targetUrl = normalizedBase + sourcePath;
            }
        }

        // 2. 未启用 SSO → 返回直接业务 URL（向后兼容）
        if (ssoParams == null || !ssoParams.isValid()) {
            return targetUrl;
        }

        // 3. 启用 SSO → 构造 OAuth 授权链接
        // 提取 path 部分作为 redirect 参数（Vue Router router.push 只接受 path，不接受完整 URL）
        String targetPath = targetUrl.substring(normalizedBase.length());
        String loginUrlWithRedirect = normalizedBase + OAUTH_REDIRECT_PATH
            + "?" + OAUTH_REDIRECT_PARAM + "="
            + URLEncoder.encode(targetPath, StandardCharsets.UTF_8);
        String redirectUri = URLEncoder.encode(loginUrlWithRedirect, StandardCharsets.UTF_8);

        return OAUTH_AUTHORIZE_URL
            + "?appid=" + ssoParams.corpId()
            + "&redirect_uri=" + redirectUri
            + "&response_type=" + OAUTH_RESPONSE_TYPE
            + "&scope=" + OAUTH_SCOPE
            + "&agentid=" + ssoParams.agentId()
            + "&state=" + OAUTH_STATE_FROM_MESSAGE
            + "#wechat_redirect";
    }

    private static String resolveSourcePath(String entityType, Long entityId) {
        if (entityType == null || entityId == null || entityId <= 0) {
            return null;
        }
        return switch (entityType.toUpperCase(java.util.Locale.ROOT)) {
            case "PROJECT" -> "/project/" + entityId;
            case "BIDDING", "TENDER" -> "/bidding/" + entityId;
            case "DOCUMENT" -> "/document/editor/" + entityId;
            case "WAREHOUSE_EXPIRY_WARNING", "WAREHOUSE_EXPIRED_WARNING", "WAREHOUSE" -> "/knowledge/warehouse?id=" + entityId;
            default -> null;
        };
    }

    private static String humanType(String type) {
        if (type == null || type.isBlank()) {
            return "通知";
        }
        return switch (type.toUpperCase(java.util.Locale.ROOT)) {
            case "MENTION" -> "提及";
            case "APPROVAL" -> "审批";
            case "DEADLINE" -> "截止";
            case "TASK_UPDATE" -> "任务";
            case "DOCUMENT_CHANGE" -> "文档变更";
            case "SYSTEM" -> "系统";
            case "PENDING_INITIATION" -> "待立项";
            case "PENDING_CLOSURE_APPLICATION" -> "待结项";
            default -> "通知";
        };
    }

    private static String defaultIfBlank(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
