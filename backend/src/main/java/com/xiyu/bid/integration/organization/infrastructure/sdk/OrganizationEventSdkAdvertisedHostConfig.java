package com.xiyu.bid.integration.organization.infrastructure.sdk;

import com.ehsy.eventlibrary.clientsdk.config.properties.ServerConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

/**
 * 覆盖 SDK 的 ServerConfig Bean，解决上报地址为 127.0.0.1:18080 的问题。
 *
 * <p>SDK 原生 {@link ServerConfig#getHostAndPort()} 使用
 * {@link java.net.InetAddress#getLocalHost()} + {@code server.port} 拼地址，
 * 在内网服务器上会误取 127.0.0.1 和 Spring Boot 内部端口。
 *
 * <p>此类通过 @Primary 覆盖 SDK 的 Bean，从
 * {@code xiyu.integrations.organization.event-sdk.advertised-host} 和
 * {@code advertised-port} 读取对外暴露的地址。
 */
@Configuration
@ConditionalOnClass(name = "com.ehsy.eventlibrary.clientsdk.config.properties.ServerConfig")
@ConditionalOnProperty(
        prefix = "xiyu.integrations.organization.event-sdk",
        name = "enabled",
        havingValue = "true"
)
public class OrganizationEventSdkAdvertisedHostConfig {

    @Bean
    @Primary
    public ServerConfig serverConfig(Environment env) {
        return new AdvertisedServerConfig(env);
    }

    /**
     * 覆写 ServerConfig，使用可配置的对外上报地址。
     */
    static class AdvertisedServerConfig extends ServerConfig {

        private final String advertisedHost;
        private final int advertisedPort;

        AdvertisedServerConfig(Environment env) {
            super(env);
            this.advertisedHost = env.getProperty(
                    "xiyu.integrations.organization.event-sdk.advertised-host", "");
            this.advertisedPort = env.getProperty(
                    "xiyu.integrations.organization.event-sdk.advertised-port", int.class, 0);
        }

        @Override
        public String getHostAndPort() {
            String host = advertisedHost.isBlank() ? super.getHost() : advertisedHost.trim();
            int port = advertisedPort > 0 ? advertisedPort : getServerPort();
            return "http://" + host + ":" + port;
        }

        @Override
        public String getHost() {
            if (!advertisedHost.isBlank()) {
                return advertisedHost.trim();
            }
            return super.getHost();
        }
    }
}
