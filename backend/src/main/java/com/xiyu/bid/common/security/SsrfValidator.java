package com.xiyu.bid.common.security;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * 校验外部 AI Provider 的 baseUrl 是否安全（SSRF 防护）。
 *
 * <p>允许：HTTP/HTTPS、任意公网域名、loopback、内网 IP（10/172.16/192.168）。
 * <p>禁止：云元数据地址（169.254.0.0/16）、0.0.0.0/8、240.0.0.0/4、广播地址、URL userinfo。
 */
public final class SsrfValidator {

    private SsrfValidator() {
    }

    /**
     * 校验 baseUrl，非法时抛 IllegalArgumentException。
     */
    public static void validate(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("自定义 Provider baseUrl 不能为空");
        }

        URI uri;
        try {
            uri = new URI(baseUrl.trim());
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("自定义 Provider baseUrl 格式无效", exception);
        }

        String scheme = uri.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("自定义 Provider baseUrl 必须是 http 或 https 协议");
        }

        if (uri.getRawUserInfo() != null) {
            throw new IllegalArgumentException("自定义 Provider baseUrl 不允许包含 userinfo（user:pass@）");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("自定义 Provider baseUrl 缺少 host");
        }

        String normalizedHost = host.toLowerCase(Locale.ROOT);
        validateHost(normalizedHost);
    }

    private static void validateHost(String host) {
        if (isIpv4Literal(host)) {
            validateIpv4(host);
            return;
        }
        if (isIpv6Literal(host)) {
            validateIpv6(host);
            return;
        }
        // 域名：不做白名单限制
    }

    private static boolean isIpv4Literal(String host) {
        if (!host.contains(".")) return false;
        String[] parts = host.split("\\.");
        if (parts.length != 4) return false;
        for (String part : parts) {
            try {
                int octet = Integer.parseInt(part);
                if (octet < 0 || octet > 255) return false;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return true;
    }

    private static boolean isIpv6Literal(String host) {
        return host.contains(":");
    }

    private static void validateIpv4(String host) {
        long ip = parseIpv4ToLong(host);
        if (inRange(ip, 169L << 24 | 254L << 16, 0xFFFF0000L)) {       // 169.254.0.0/16
            throw new IllegalArgumentException("自定义 Provider baseUrl 不允许指向该地址（link-local 云元数据）");
        }
        if (inRange(ip, 0L, 0xFF000000L)) {                            // 0.0.0.0/8
            throw new IllegalArgumentException("自定义 Provider baseUrl 不允许指向该地址（本网络）");
        }
        if (inRange(ip, 240L << 24, 0xF0000000L)) {                    // 240.0.0.0/4
            throw new IllegalArgumentException("自定义 Provider baseUrl 不允许指向该地址（保留地址）");
        }
        if (ip == 0xFFFFFFFFL) {                                       // 255.255.255.255
            throw new IllegalArgumentException("自定义 Provider baseUrl 不允许指向该地址（广播地址）");
        }
        // 允许：127.0.0.0/8、10.0.0.0/8、172.16.0.0/12、192.168.0.0/16、公网 IP
    }

    private static void validateIpv6(String host) {
        // 简化处理：禁止 ::（未指定）和 fe80::/10（link-local）
        String compressed = host.toLowerCase(Locale.ROOT);
        if (compressed.equals("::")) {
            throw new IllegalArgumentException("自定义 Provider baseUrl 不允许指向该地址（未指定地址）");
        }
        if (compressed.startsWith("fe80:") || compressed.startsWith("fe9") || compressed.startsWith("fea") || compressed.startsWith("feb")) {
            throw new IllegalArgumentException("自定义 Provider baseUrl 不允许指向该地址（link-local）");
        }
        // 允许 ::1 loopback、fc00::/7 ULA、公网 IPv6
    }

    private static long parseIpv4ToLong(String host) {
        String[] parts = host.split("\\.");
        long result = 0;
        for (String part : parts) {
            result = (result << 8) | Integer.parseInt(part);
        }
        return result;
    }

    private static boolean inRange(long ip, long networkStart, long mask) {
        return (ip & mask) == (networkStart & mask);
    }
}
