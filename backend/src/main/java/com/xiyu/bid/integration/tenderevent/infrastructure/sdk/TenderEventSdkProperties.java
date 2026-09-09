package com.xiyu.bid.integration.tenderevent.infrastructure.sdk;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 标讯事件推送 SDK 配置。
 *
 * <p>复用西域事件 SDK 的 {@code /eventbus/publishEvent} 约定，仅配置发送所需的
 * 服务注册地址与业务系统标识。默认关闭，生产环境通过环境变量开启。
 *
 * @param enabled          是否启用标讯事件推送
 * @param serverRegisterUrl 事件总线服务注册地址（形如 {@code http://eventbus:8080}）
 * @param serviceName      业务系统标识（消息体 {@code serviceName} 字段）
 * @param advertisedHost   注册/续约时向事件总线宣告的对外主机
 * @param advertisedPort   注册/续约时向事件总线宣告的对外端口
 */
@ConfigurationProperties(prefix = "xiyu.integrations.tender-event.sdk")
public record TenderEventSdkProperties(
        boolean enabled,
        String serverRegisterUrl,
        String serviceName,
        String advertisedHost,
        int advertisedPort
) {

    public TenderEventSdkProperties {
        serviceName = (serviceName == null || serviceName.isBlank()) ? "bid" : serviceName;
    }
}
