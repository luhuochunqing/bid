package com.xiyu.bid.shared.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 外部 URL 安全校验工具，防止 SSRF 攻击。
 *
 * <p>检查规则：
 * <ul>
 *   <li>协议必须是 http 或 https</li>
 *   <li>主机名不能为 localhost 或私有 IP 地址（RFC 1918 + 环回 + 链路本地）</li>
 *   <li>若提供了域名白名单，主机名必须在白名单中</li>
 * </ul>
 */
public final class ExternalUrlGuard {

    private ExternalUrlGuard() {
    }

    /**
     * 校验外部 URL，防止 SSRF 攻击。
     *
     * @param uri              待校验的 URI
     * @param allowedHostsCsv  允许的主机名白名单（逗号分隔），为空时仅阻止私有地址
     * @throws ResponseStatusException 校验失败时抛出 400
     */
    public static void validate(URI uri, String allowedHostsCsv) {
        String scheme = uri.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "外部 URL 协议必须是 http 或 https");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "外部 URL 缺少有效主机名");
        }
        String normalizedHost = host.toLowerCase().trim();
        if (isPrivateOrLoopbackAddress(normalizedHost)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "禁止访问内部网络地址");
        }
        if (allowedHostsCsv != null && !allowedHostsCsv.isBlank()) {
            Set<String> allowed = Arrays.stream(allowedHostsCsv.split(","))
                    .map(String::trim).map(String::toLowerCase)
                    .filter(s -> !s.isEmpty()).collect(Collectors.toSet());
            if (!allowed.contains(normalizedHost)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "外部 URL 域名不在允许列表中");
            }
        }
    }

    /**
     * 判断主机名是否为 localhost 或私有 IP 地址。
     * 包括：环回地址、RFC 1918 私有地址、链路本地地址、任意本地地址。
     */
    static boolean isPrivateOrLoopbackAddress(String host) {
        if ("localhost".equalsIgnoreCase(host)) {
            return true;
        }
        if (host.startsWith("[") && host.endsWith("]")) {
            String inner = host.substring(1, host.length() - 1);
            if ("::1".equals(inner)) {
                return true;
            }
            try {
                InetAddress addr = InetAddress.getByName(inner);
                return addr.isLoopbackAddress() || addr.isSiteLocalAddress()
                        || addr.isAnyLocalAddress() || addr.isLinkLocalAddress();
            } catch (UnknownHostException e) {
                return true;
            }
        }
        try {
            InetAddress addr = InetAddress.getByName(host);
            return addr.isLoopbackAddress() || addr.isSiteLocalAddress()
                    || addr.isAnyLocalAddress() || addr.isLinkLocalAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
