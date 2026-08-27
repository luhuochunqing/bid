package com.xiyu.bid.integration.tenderevent.infrastructure.sdk;

import static org.assertj.core.api.Assertions.assertThat;

import com.xiyu.bid.integration.tenderevent.application.TenderEventPublishPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * SDK 装配隔离测试：容器正常创建 Sender/Producer 与 Registrar bean。
 */
@DisplayName("TenderEventSdkConfig - SDK 容器装配测试")
class TenderEventSdkConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(UserConfigurations.of(TenderEventSdkConfig.class))
            .withPropertyValues(
                    "xiyu.integrations.tender-event.sdk.enabled=true",
                    "xiyu.integrations.tender-event.sdk.serverRegisterUrl=http://event-bus.local",
                    "xiyu.integrations.tender-event.sdk.serviceName=bid");

    @Test
    @DisplayName("容器装配：Sender/Producer/Registrar bean 正常创建")
    void context_wiresSenderAndProducer_withoutSdkBeans() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(TenderEventSdkSender.class);
            assertThat(context).hasSingleBean(TenderEventPublishPort.class);
            assertThat(context).hasSingleBean(TenderEventRegistrar.class);
        });
    }
}
