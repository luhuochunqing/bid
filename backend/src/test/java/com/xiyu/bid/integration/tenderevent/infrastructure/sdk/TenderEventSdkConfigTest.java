package com.xiyu.bid.integration.tenderevent.infrastructure.sdk;

import static org.assertj.core.api.Assertions.assertThat;

import com.xiyu.bid.integration.tenderevent.application.TenderEventPublishPort;
import java.io.IOException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * SDK 装配隔离测试：容器正常创建 Sender/Producer 与 Registrar bean。
 */
@DisplayName("TenderEventSdkConfig - SDK 容器装配测试")
class TenderEventSdkConfigTest {

    private static MockWebServer mockWebServer;

    @BeforeAll
    static void startServer() throws Exception {
        mockWebServer = new MockWebServer();
        // 预设注册成功的响应，使 ClientRegisterComponent 收到 00000 后立即退出循环
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"code\":\"00000\",\"data\":{\"result\":true},\"msg\":\"OK\"}")
                .setHeader("Content-Type", "application/json"));
        mockWebServer.start();
    }

    @AfterAll
    static void stopServer() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("容器装配（启用）：Sender/Producer/Registrar bean 正常创建")
    void context_wiresSenderAndProducer_whenEnabled() {
        new ApplicationContextRunner()
                .withConfiguration(UserConfigurations.of(TenderEventSdkConfig.class))
                .withPropertyValues(
                        "xiyu.integrations.tender-event.sdk.enabled=true",
                        "xiyu.integrations.tender-event.sdk.serverRegisterUrl=" + mockWebServer.url("/").toString().replaceAll("/$", ""),
                        "xiyu.integrations.tender-event.sdk.serviceName=bid")
                .run(context -> {
                    assertThat(context).hasSingleBean(TenderEventSdkSender.class);
                    assertThat(context).hasSingleBean(TenderEventPublishPort.class);
                    assertThat(context).hasSingleBean(TenderEventRegistrar.class);
                });
    }

    @Test
    @DisplayName("容器装配（未启用）：使用 disabled 兜底端口，不装配 Registrar")
    void context_disabledPort_whenNotEnabled() {
        new ApplicationContextRunner()
                .withConfiguration(UserConfigurations.of(TenderEventSdkConfig.class))
                .withPropertyValues("xiyu.integrations.tender-event.sdk.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(TenderEventRegistrar.class);
                    assertThat(context).hasSingleBean(TenderEventPublishPort.class);
                });
    }
}
