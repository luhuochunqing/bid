package com.xiyu.bid.integration.tenderevent.infrastructure.sdk;

import com.xiyu.bid.integration.tenderevent.application.TenderEventPublishPort;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 标讯事件推送 SDK 装配。
 *
 * <p>启用（{@code xiyu.integrations.tender-event.sdk.enabled=true}）时注册真实生产者
 * {@link TenderEventSdkProducer}，直连事件总线 {@code /eventbus/publishEvent}；
 * 未启用时注册 no-op 兜底端口，保证编排服务 {@link TenderEventPublishPort} 始终可注入。
 */
@Configuration
@EnableConfigurationProperties(TenderEventSdkProperties.class)
public class TenderEventSdkConfig {

    @Bean
    @ConditionalOnProperty(
            prefix = "xiyu.integrations.tender-event.sdk",
            name = "enabled",
            havingValue = "true"
    )
    public TenderEventPublishPort tenderEventPublishPort(TenderEventSdkProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        RestClient restClient = RestClient.builder()
                .baseUrl(properties.serverRegisterUrl())
                .requestFactory(factory)
                .build();
        return new TenderEventSdkProducer(properties, restClient);
    }

    /** 未启用时兜底端口：不发送任何事件。 */
    @Bean
    @ConditionalOnMissingBean(TenderEventPublishPort.class)
    public TenderEventPublishPort disabledTenderEventPublishPort() {
        return command -> false;
    }
}