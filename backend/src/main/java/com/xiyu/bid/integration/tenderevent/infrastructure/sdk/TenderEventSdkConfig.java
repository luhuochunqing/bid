package com.xiyu.bid.integration.tenderevent.infrastructure.sdk;

import com.ehsy.eventlibrary.clientsdk.config.properties.ServiceRegisterProperties;
import com.ehsy.eventlibrary.clientsdk.systeminteraction.service.SystemInteractiveService;
import com.ehsy.eventlibrary.clientsdk.systeminteraction.service.impl.SystemInteractiveServiceImpl;
import com.xiyu.bid.integration.tenderevent.application.TenderEventPublishPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * 标讯事件推送 SDK 装配。
 *
 * <p>启用（{@code xiyu.integrations.tender-event.sdk.enabled=true}）时注册真实生产者
 * {@link TenderEventSdkProducer}，经 SDK 的 {@code SystemInteractiveService} 推送事件总线；
 * 未启用时注册 no-op 兜底端口，保证编排服务 {@link TenderEventPublishPort} 始终可注入。
 *
 * <p>{@code SystemInteractiveServiceImpl} 通过 {@code @Import} 单类注册为 bean（其
 * {@code @Autowired} 的 {@link ServiceRegisterProperties} 字段由下方 bean 满足）。
 * 不能 {@code @Import} SDK 的 {@code SDKClientConfiguration}：那会连带激活 Kafka 生产者、
 * ZooKeeper 偏移管理与客户端注册等消费侧组件，发布场景用不到。
 */
@Configuration
@EnableConfigurationProperties(TenderEventSdkProperties.class)
@Import(SystemInteractiveServiceImpl.class)
public class TenderEventSdkConfig {

    /** SDK 发送服务依赖的服务注册配置：serviceName 标识业务系统，serverRegisterUrl 指向事件总线。 */
    @Bean
    @Primary
    public ServiceRegisterProperties serviceRegisterProperties(TenderEventSdkProperties properties) {
        ServiceRegisterProperties registerProperties = new ServiceRegisterProperties();
        registerProperties.setServiceName(properties.serviceName());
        registerProperties.setServerRegisterUrl(properties.serverRegisterUrl());
        return registerProperties;
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "xiyu.integrations.tender-event.sdk",
            name = "enabled",
            havingValue = "true"
    )
    public TenderEventPublishPort tenderEventPublishPort(TenderEventSdkProperties properties,
                                                         ServiceRegisterProperties registerProperties) {
        return new TenderEventSdkProducer(properties, registerProperties);
    }

    /** 未启用时兜底端口：不发送任何事件。 */
    @Bean
    @ConditionalOnMissingBean(TenderEventPublishPort.class)
    public TenderEventPublishPort disabledTenderEventPublishPort() {
        return command -> false;
    }
}
