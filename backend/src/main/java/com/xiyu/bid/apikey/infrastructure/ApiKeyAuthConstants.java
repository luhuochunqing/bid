package com.xiyu.bid.apikey.infrastructure;

import java.util.List;

/**
 * API Key 认证统一常量（CO-280 根因治理）。
 *
 * <p>集中管理 API Key 相关的 Header 名、查询参数名，
 * 避免散落在 Filter、Controller、URL 解析器等多处以字符串硬编码。
 *
 * <p><b>维护原则</b>：任何需要读取 API Key 的地方（认证 Filter、URL 生成、
 * 外部接口回传）都必须使用本类定义的常量，不得自己拼写字符串。
 */
public final class ApiKeyAuthConstants {

    private ApiKeyAuthConstants() {
        // 工具类，禁止实例化
    }

    /**
     * 支持的 API Key Header 名称（大小写敏感，按顺序匹配第一个非空值）。
     */
    public static final List<String> API_KEY_HEADERS = List.of("X-API-Key", "X-Api-Key");

    /**
     * 支持的 API Key 查询参数名称（URL ?api_key=xxx 场景，
     * 主要用途：浏览器无法携带自定义 HTTP Header 时的回退方案）。
     */
    public static final List<String> API_KEY_PARAMS = List.of("api_key", "api-key", "X-API-Key", "X-Api-Key");

    /**
     * 默认查询参数名（用于 URL 生成时附加 api_key=xxx）。
     */
    public static final String DEFAULT_QUERY_PARAM = "api_key";

    /**
     * API Key 认证成功后在 SecurityContext principal 中使用的前缀：
     * {@code "api-key:" + apiKeyId}。业务侧（如 {@code ApiKeyUserResolver}）据此识别
     * API Key 认证并反查属主；前缀格式变更时仅需改此处。
     */
    public static final String API_KEY_PRINCIPAL_PREFIX = "api-key:";

    /**
     * API Key 认证成功后写入 {@code authentication details} 的属主用户名键
     * （由 {@code ApiKeyAuthenticationFilter} 透传，业务侧零查询读取属主）。
     */
    public static final String DETAILS_CREATED_BY_KEY = "apiKeyCreatedBy";
}
