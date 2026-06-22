package com.xiyu.bid.integration.external;

/**
 * 调用方上下文（CO-280 根因治理）。
 *
 * <p>统一表示"谁在调用当前 API / 当前请求属于谁"，
 * 使 URL 解析器、Mapper 层能自动选择正确的端点和认证方式，
 * 避免各个 Controller 自己手写"取 api_key → 调 mapper → 拼 URL"的重复逻辑。
 *
 * <p>两种调用方类型：
 * <ul>
 *   <li>{@link CallerType#INTERNAL_USER}：内部用户，通过 JWT/Bearer token 认证，
 *       访问 /api/doc-insight/download 端点。</li>
 *   <li>{@link CallerType#EXTERNAL_SYSTEM}：外部系统（CRM 等），通过 API Key 认证，
 *       访问 /api/integration/tenders/attachments/download 端点。</li>
 * </ul>
 *
 * <p>使用方式：
 * <pre>{@code
 * // 内部场景
 * CallerContext ctx = CallerContext.internalUser();
 *
 * // 外部 API 场景
 * CallerContext ctx = CallerContext.externalSystem("xiyu_sk_...");
 *
 * // 转换 URL
 * String url = TenderAttachmentUrlResolver.resolve(fileUrl, ctx);
 * }</pre>
 */
public final class CallerContext {

    public enum CallerType {
        /** 内部平台用户（Bearer Token 认证）。 */
        INTERNAL_USER,
        /** 外部系统（X-API-Key Header 或 URL 参数认证）。 */
        EXTERNAL_SYSTEM
    }

    private final CallerType type;
    private final String apiKey;

    private CallerContext(CallerType type, String apiKey) {
        this.type = type;
        this.apiKey = apiKey;
    }

    /** 内部用户上下文（无需 API Key）。 */
    public static CallerContext internalUser() {
        return new CallerContext(CallerType.INTERNAL_USER, null);
    }

    /** 外部系统上下文（携带 API Key）。 */
    public static CallerContext externalSystem(String apiKey) {
        return new CallerContext(CallerType.EXTERNAL_SYSTEM, apiKey);
    }

    public CallerType type() { return type; }
    public String apiKey() { return apiKey; }
    public boolean isExternalSystem() { return type == CallerType.EXTERNAL_SYSTEM; }
    public boolean isInternalUser() { return type == CallerType.INTERNAL_USER; }
}
