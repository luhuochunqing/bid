package com.xiyu.bid.integration.tenderevent.infrastructure.sdk;

import com.ehsy.eventlibrary.clientsdk.util.SnowflakeIdUtil;
import java.math.BigInteger;
import java.security.SecureRandom;

/**
 * 标讯事件链路 ID 长度适配器。
 *
 * <p>生产 Pod 存在 {@code HOSTNAME} 时由客户 SDK {@link SnowflakeIdUtil#generateId()} 生成底层 ID，
 * 本类不维护 worker、datacenter 或时钟状态。完整 long 使用无损 base36 编码：
 * <ul>
 *   <li>{@code traceId}: {@code t + base36(sdkSnowflake)}（最长 14 位）</li>
 *   <li>{@code spanId}: {@code s + base36(sdkSnowflake)}（最长 14 位）</li>
 * </ul>
 * 无 {@code HOSTNAME} 的本地环境使用 87 位 {@link SecureRandom} 随机值兜底，避免触发当前 SDK
 * 静态初始化的空指针；随机值转 base36 后最长 17 位，加类型前缀后仍不超过 18 位。
 */
public final class TenderEventSnowflakeUtil {

    private static final int RANDOM_BYTES = 11;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private TenderEventSnowflakeUtil() {
    }

    public static String nextTraceId() {
        return "t" + nextEncodedId();
    }

    public static String nextSpanId() {
        return "s" + nextEncodedId();
    }

    static String encode(long id) {
        return Long.toUnsignedString(id, 36);
    }

    private static String nextEncodedId() {
        String hostname = System.getenv("HOSTNAME");
        if (hostname != null && !hostname.isBlank()) {
            return encode(SnowflakeIdUtil.generateId());
        }
        byte[] bytes = new byte[RANDOM_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        bytes[0] &= 0x7F;
        return new BigInteger(1, bytes).toString(36);
    }
}
