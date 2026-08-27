package com.xiyu.bid.integration.tenderevent.infrastructure.sdk;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * 标讯事件链路追踪 Snowflake ID 生成器（纯工具类，无外部环境依赖）。
 *
 * <p>生成规则完全对齐西域 SDK 规范：
 * <ul>
 *   <li>{@code traceId}: {@code "t" + 18位数字}（总长度不超过 19 位）</li>
 *   <li>{@code spanId}: {@code "s" + 18位数字}（总长度不超过 19 位）</li>
 * </ul>
 * 客户事件库 trace_id / span_id 字段为 varchar(19)。
 */
public final class TenderEventSnowflakeUtil {

    private static final long START_EPOCH = 1609430400000L; // 2021-01-01 00:00:00 UTC
    private static final long WORKER_ID_BITS = 5L;
    private static final long DATACENTER_ID_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;

    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

    private final long workerId;
    private final long datacenterId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    private static final TenderEventSnowflakeUtil INSTANCE = new TenderEventSnowflakeUtil();

    private TenderEventSnowflakeUtil() {
        this.workerId = initWorkerId();
        this.datacenterId = initDatacenterId();
    }

    public static synchronized String nextTraceId() {
        return "t" + INSTANCE.nextId();
    }

    public static synchronized String nextSpanId() {
        return "s" + INSTANCE.nextId();
    }

    public synchronized long nextId() {
        long timestamp = System.currentTimeMillis();

        if (timestamp < lastTimestamp) {
            timestamp = lastTimestamp; // 容忍小幅时钟回拨
        }

        if (lastTimestamp == timestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = timestamp;

        return ((timestamp - START_EPOCH) << TIMESTAMP_LEFT_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    private long tilNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }

    private static long initWorkerId() {
        try {
            byte[] ip = InetAddress.getLocalHost().getAddress();
            return (ip[ip.length - 1] & 0xFF) % (MAX_WORKER_ID + 1);
        } catch (UnknownHostException | SecurityException e) {
            return 1L;
        }
    }

    private static long initDatacenterId() {
        try {
            String host = InetAddress.getLocalHost().getHostName();
            if (host == null || host.isBlank()) {
                return 1L;
            }
            int sum = 0;
            for (char c : host.toCharArray()) {
                sum += c;
            }
            return (sum & 0x7FFFFFFF) % (MAX_DATACENTER_ID + 1);
        } catch (UnknownHostException | SecurityException e) {
            return 1L;
        }
    }
}
