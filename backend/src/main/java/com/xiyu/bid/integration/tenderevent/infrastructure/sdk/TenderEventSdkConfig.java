package com.xiyu.bid.integration.tenderevent.infrastructure.sdk;

import com.ehsy.eventlibrary.clientsdk.config.properties.ServiceRegisterProperties;
import com.ehsy.eventlibrary.clientsdk.systeminteraction.service.SystemInteractiveService;
import com.ehsy.eventlibrary.clientsdk.systeminteraction.service.impl.SystemInteractiveServiceImpl;
import com.xiyu.bid.integration.tenderevent.application.TenderEventPublishPort;
import java.lang.reflect.Field;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * 标讯事件推送 SDK 装配。
 *
 * <p>启用（{@code xiyu.integrations.tender-event.sdk.enabled=true}）时注册真实生产者
 * {@link TenderEventSdkProducer}，经 SDK 的 {@code SystemInteractiveService.sendEvent}
 * 推送事件总线；
 * 未启用时注册 no-op 兜底端口，保证编排服务 {@link TenderEventPublishPort} 始终可注入。
 *
 * <p><b>SDK 的 ServiceRegisterProperties 与 SystemInteractiveServiceImpl 都不能注册为
 * Spring bean</b>，两者在容器里都会被污染（2026-08-27 实测）：
 * <ul>
 *   <li>属性类自带类级 {@code @ConfigurationProperties(prefix="client.register")}，容器管理的
 *       实例会被组织消费侧环境值（BidSystemOrgConsumer）重新绑定覆盖 setter 设入的 bid，
 *       SDK 发出错误服务名后网关返回 {@code result:false}（探针对照：bid→受理成功）</li>
 *   <li>SDK 服务类的 {@code @Autowired} 字段会被 AutowiredAnnotationBeanPostProcessor
 *       再次注入——容器里存在任何 ServiceRegisterProperties bean 都会顶掉反射注入的干净配置</li>
 * </ul>
 * 因此 SDK 实例由本配置类手工创建后直接通过构造器交给发布器，不注册成 Spring bean；也不能
 * {@code @Import} SDKClientConfiguration（连带激活 Kafka/ZK/注册等消费侧组件）。
 *
 * <p><b>注册+续约生命周期（2026-08-27 根因修复）</b>：事件总线要求生产方先通过
 * {@code POST /eventbus/register}（body: serviceName/serviceAddress/enableRegister）
 * 完成服务注册，并持续通过 {@code POST /eventbus/renew} 保持心跳续约，总线才会
 * 将 publishEvent 路由到订阅方。此前完全跳过了这个生命周期，导致 bid 服务名从未
 * 在线，网关受理 publishEvent 后直接丢弃事件（code=00000 只代表 HTTP 格式合规）。
 * 修复方案：手工装配 {@link TenderEventRegistrar}（内含 ClientRegisterComponent +
 * ServiceRenewalComponent），不引入 SDKClientConfiguration（避免连带激活 Kafka/ZK）。
 */
@Configuration
@EnableConfigurationProperties(TenderEventSdkProperties.class)
public class TenderEventSdkConfig {

    public static SystemInteractiveService newSdkService(TenderEventSdkProperties properties) {
        ServiceRegisterProperties sdkProps = new ServiceRegisterProperties();
        sdkProps.setServiceName(properties.serviceName());
        sdkProps.setServerRegisterUrl(properties.serverRegisterUrl());
        SystemInteractiveServiceImpl service = new SystemInteractiveServiceImpl();
        try {
            Field field = SystemInteractiveServiceImpl.class.getDeclaredField("clientSdkConfig");
            field.setAccessible(true);
            field.set(service, sdkProps);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法向 SDK 服务注入服务注册配置", e);
        }
        return service;
    }

    /**
     * 注册+续约启动器（根因修复）：由 {@link TenderEventRegistrar} 在
     * {@code ApplicationReadyEvent} 后触发 {@code POST /eventbus/register}，避免 EventBus
     * 同步回调命中尚未就绪的 Web 端口；注册成功后开始周期性
     * {@code POST /eventbus/renew} 心跳。
     *
     * <p>仅在 SDK 启用时装配（{@code enabled=true}），防止未配置时反射注入失败。
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(
            prefix = "xiyu.integrations.tender-event.sdk",
            name = "enabled",
            havingValue = "true"
    )
    public TenderEventRegistrar tenderEventRegistrar(
            TenderEventSdkProperties properties,
            Environment environment) {
        return new TenderEventRegistrar(properties, environment);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "xiyu.integrations.tender-event.sdk",
            name = "enabled",
            havingValue = "true"
    )
    public TenderEventPublishPort tenderEventPublishPort(TenderEventSdkProperties properties) {
        return new TenderEventSdkProducer(newSdkService(properties));
    }

    @Bean
    public OpportunityPredictionEventPublisher opportunityPredictionEventPublisher(
            TenderEventSdkProperties properties) {
        return new OpportunityPredictionEventPublisher(properties, newSdkService(properties));
    }

    /** 未启用时兜底端口：不发送任何事件。 */
    @Bean
    @ConditionalOnMissingBean(TenderEventPublishPort.class)
    public TenderEventPublishPort disabledTenderEventPublishPort() {
        return command -> false;
    }
}
