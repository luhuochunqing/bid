package com.xiyu.bid.integration.tenderevent.infrastructure.sdk;

import static org.assertj.core.api.Assertions.assertThat;

import com.ehsy.eventlibrary.clientsdk.config.properties.ServiceRegisterProperties;
import com.ehsy.eventlibrary.clientsdk.systeminteraction.service.impl.SystemInteractiveServiceImpl;
import com.xiyu.bid.integration.tenderevent.application.TenderEventPublishPort;
import java.lang.reflect.Field;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * SDK 装配隔离测试：即使组织事件消费侧的 {@code client.register.service-name} 环境值存在，
 * 标讯 SDK 服务的 serviceName 也必须保持 bid。
 *
 * <p>回归背景（2026-08-27 实测）：{@code ServiceRegisterProperties} 自带类级
 * {@code @ConfigurationProperties(prefix="client.register")}，容器管理的实例会被环境值
 * 重新绑定——曾把手工 set 的 bid 覆盖成 BidSystemOrgConsumer，SDK 发出错误服务名后网关
 * 返回 {@code result:false}（探针对照：bid→受理成功，BidSystemOrgConsumer→false）；
 * SDK 服务类的 @Autowired 字段在容器里也会被再次注入污染。两者都必须手工构造、
 * 藏进 {@link TenderEventSdkSender} 私有字段（无注解字段，容器不触碰）。
 */
@DisplayName("TenderEventSdkConfig - SDK serviceName 与 client.register 环境绑定隔离")
class TenderEventSdkConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(UserConfigurations.of(TenderEventSdkConfig.class))
            .withPropertyValues(
                    "xiyu.integrations.tender-event.sdk.enabled=true",
                    "xiyu.integrations.tender-event.sdk.serverRegisterUrl=http://event-bus.local",
                    "xiyu.integrations.tender-event.sdk.serviceName=bid",
                    // 模拟组织事件消费侧环境值：若 SDK 属性/服务被容器管理，bid 会被它污染
                    "client.register.serviceName=BidSystemOrgConsumer",
                    "client.register.serverRegisterUrl=http://org-event-bus.local");

    @Test
    @DisplayName("SDK 服务手工构造：client.register.* 环境值不污染 bid / 标讯网关地址")
    void newSdkService_isolatedFromClientRegisterBinding() throws Exception {
        TenderEventSdkProperties properties = new TenderEventSdkProperties(
                true, "http://event-bus.local", "bid");

        SystemInteractiveServiceImpl service = (SystemInteractiveServiceImpl)
                TenderEventSdkConfig.newSdkService(properties);
        ServiceRegisterProperties sdkProps = readClientSdkConfig(service);

        assertThat(sdkProps.getServiceName())
                .as("SDK serviceName 必须是标讯的 bid，不能被 client.register 绑定覆盖")
                .isEqualTo("bid");
        assertThat(sdkProps.getServerRegisterUrl()).isEqualTo("http://event-bus.local");
    }

    @Test
    @DisplayName("容器装配：Sender/Producer bean 正常创建，SDK 实例不暴露为 bean")
    void context_wiresSenderAndProducer_withoutSdkBeans() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(TenderEventSdkSender.class);
            assertThat(context).hasSingleBean(TenderEventPublishPort.class);
            // SDK 类型绝不注册为 bean（容器管理的 SDK 实例会被绑定/注入污染）
            assertThat(context.getBeansOfType(
                    com.ehsy.eventlibrary.clientsdk.config.properties.ServiceRegisterProperties.class))
                    .as("ServiceRegisterProperties 不得成为容器 bean").isEmpty();
            assertThat(context.getBeansOfType(
                    com.ehsy.eventlibrary.clientsdk.systeminteraction.service.SystemInteractiveService.class))
                    .as("SystemInteractiveService 不得成为容器 bean").isEmpty();
        });
    }

    /** SDK 服务用私有字段持有配置（无 getter），反射读取断言。 */
    private static ServiceRegisterProperties readClientSdkConfig(SystemInteractiveServiceImpl service)
            throws NoSuchFieldException, IllegalAccessException {
        Field field = SystemInteractiveServiceImpl.class.getDeclaredField("clientSdkConfig");
        field.setAccessible(true);
        return (ServiceRegisterProperties) field.get(service);
    }
}
