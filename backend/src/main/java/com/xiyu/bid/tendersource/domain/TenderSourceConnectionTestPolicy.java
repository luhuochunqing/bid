package com.xiyu.bid.tendersource.domain;

import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.io.IOException;
import java.net.UnknownHostException;

/**
 * 标讯源连接测试策略（Pure Core）
 * 
 * 纯核心业务逻辑：验证标讯源API端点的连通性
 * - 不修改入参
 * - 不读写数据库
 * - 不记录日志
 * - 返回可预测的结果
 */
public final class TenderSourceConnectionTestPolicy {

    private static final String THIRD_PARTY_PLATFORM = "第三方商机服务";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private TenderSourceConnectionTestPolicy() {
    }

    /**
     * 测试第三方商机服务的API连接
     * 
     * @param platform 平台名称
     * @param apiEndpoint API端点地址
     * @param apiKey API密钥
     * @return 连接测试结果
     */
    public static TenderSourceConnectionResult testThirdPartyConnection(
            String platform,
            String apiEndpoint,
            String apiKey) {
        
        if (!THIRD_PARTY_PLATFORM.equals(platform)) {
            return TenderSourceConnectionResult.failure("仅支持测试「第三方商机服务」平台的连接");
        }

        if (apiEndpoint == null || apiEndpoint.isBlank()) {
            return TenderSourceConnectionResult.failure("API端点不能为空");
        }

        if (apiKey == null || apiKey.isBlank()) {
            return TenderSourceConnectionResult.failure("API密钥不能为空");
        }

        URI uri;
        try {
            String trimmed = apiEndpoint.trim();
            if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                return TenderSourceConnectionResult.failure("API端点格式无效");
            }
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException e) {
            return TenderSourceConnectionResult.failure("API端点格式无效");
        }

        return performHttpTest(uri, apiKey.trim());
    }

    private static boolean isPrivateAddress(InetAddress addr) {
        if (addr.isLoopbackAddress() || addr.isLinkLocalAddress()) {
            return true;
        }
        if (addr.isSiteLocalAddress()) {
            return true;
        }
        if (addr instanceof Inet4Address) {
            byte[] b = addr.getAddress();
            // 172.16.0.0 – 172.31.255.255
            if (b[0] == (byte) 172) {
                int second = b[1] & 0xFF;
                return second >= 16 && second <= 31;
            }
        }
        if (addr instanceof Inet6Address inet6) {
            // IPv6 映射的 IPv4 内网地址 (::ffff:a.b.c.d)
            byte[] bytes = inet6.getAddress();
            if (bytes.length == 16
                    && bytes[10] == (byte) 0xff && bytes[11] == (byte) 0xff) {
                return isPrivateAddress(addrMappedToIPv4(inet6));
            }
        }
        return false;
    }

    private static InetAddress addrMappedToIPv4(Inet6Address addr) {
        byte[] bytes = addr.getAddress();
        byte[] v4bytes = new byte[4];
        System.arraycopy(bytes, 12, v4bytes, 0, 4);
        try {
            return InetAddress.getByAddress(v4bytes);
        } catch (java.net.UnknownHostException e) {
            return addr;
        }
    }

    private static TenderSourceConnectionResult checkPrivateAddress(URI uri) {
        try {
            InetAddress inetAddr = InetAddress.getByName(uri.getHost());
            if (isPrivateAddress(inetAddr)) {
                return TenderSourceConnectionResult.failure("禁止访问内网私有地址");
            }
            // 额外解析所有 IP，防止 DNS rebinding
            InetAddress[] allAddrs = InetAddress.getAllByName(uri.getHost());
            for (InetAddress a : allAddrs) {
                if (isPrivateAddress(a)) {
                    return TenderSourceConnectionResult.failure("禁止访问内网私有地址");
                }
            }
        } catch (UnknownHostException e) {
            // 解析失败交给 performHttpTest 的异常处理
        }
        return null;
    }

    private static TenderSourceConnectionResult performHttpTest(URI uri, String apiKey) {
        TenderSourceConnectionResult ssrfCheck = checkPrivateAddress(uri);
        if (ssrfCheck != null) {
            return ssrfCheck;
        }

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();

            if (statusCode >= 200 && statusCode < 300) {
                return TenderSourceConnectionResult.success();
            } else if (statusCode == 401 || statusCode == 403) {
                return TenderSourceConnectionResult.failure("认证失败，请检查API密钥");
            } else if (statusCode == 404) {
                return TenderSourceConnectionResult.failure("API端点未找到，请检查URL");
            } else {
                return TenderSourceConnectionResult.failure("服务器返回错误状态码: " + statusCode);
            }
        } catch (HttpTimeoutException e) {
            return TenderSourceConnectionResult.failure("连接超时，请检查API端点是否可访问");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return TenderSourceConnectionResult.failure("请求被中断");
        } catch (ConnectException e) {
            return TenderSourceConnectionResult.failure("无法连接到服务器，请检查API端点");
        } catch (UnknownHostException e) {
            return TenderSourceConnectionResult.failure("无法解析域名，请检查API端点地址");
        } catch (IOException e) {
            return TenderSourceConnectionResult.failure("IO错误，请检查网络连接");
        } catch (IllegalArgumentException e) {
            return TenderSourceConnectionResult.failure("HTTP请求构建失败: " + e.getMessage());
        } catch (RuntimeException e) {
            return TenderSourceConnectionResult.failure("连接失败: " + extractRootMessage(e));
        }
    }

    private static String extractRootMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        String message = cursor.getMessage();
        return message != null && !message.isBlank() ? message : "未知错误";
    }
}
