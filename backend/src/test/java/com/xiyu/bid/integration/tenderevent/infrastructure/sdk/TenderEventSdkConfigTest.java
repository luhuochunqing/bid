package com.xiyu.bid.integration.tenderevent.infrastructure.sdk;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.fastjson.JSONObject;
import com.ehsy.eventlibrary.clientsdk.config.properties.ServiceRenewalProperties;
import com.ehsy.eventlibrary.clientsdk.config.properties.ServerConfig;
import com.ehsy.eventlibrary.clientsdk.service.component.ClientRegisterComponent;
import com.ehsy.eventlibrary.clientsdk.service.component.ServiceRenewalComponent;
import com.xiyu.bid.integration.tenderevent.application.TenderEventPublishPort;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.env.MockEnvironment;

/**
 * SDK 装配隔离测试：两个发布器各自持有隔离 SDK 实例，不暴露 SDK bean。
 */
@DisplayName("TenderEventSdkConfig - SDK 容器装配测试")
class TenderEventSdkConfigTest {

    private MockWebServer mockWebServer;

    @BeforeEach
    void startServer() throws Exception {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterEach
    void stopServer() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("容器装配（启用）：应用就绪前不注册，就绪后启动注册和续约")
    void context_wiresIsolatedPublishers_whenEnabled() {
        mockWebServer.enqueue(jsonResponse(
                "{\"code\":\"00000\",\"data\":{\"result\":true},\"msg\":\"OK\"}"));
        mockWebServer.enqueue(jsonResponse(
                "{\"code\":\"00000\",\"data\":{\"result\":true},\"msg\":\"OK\"}"));

        new ApplicationContextRunner()
                .withConfiguration(UserConfigurations.of(TenderEventSdkConfig.class))
                .withPropertyValues(
                        "xiyu.integrations.tender-event.sdk.enabled=true",
                        "xiyu.integrations.tender-event.sdk.serverRegisterUrl=" + mockWebServer.url("/").toString().replaceAll("/$", ""),
                        "xiyu.integrations.tender-event.sdk.serviceName=bid",
                        "server.port=18080",
                        "xiyu.integrations.organization.event-sdk.advertised-host=172.16.38.78",
                        "xiyu.integrations.organization.event-sdk.advertised-port=8080")
                .run(context -> {
                    assertThat(context).hasSingleBean(TenderEventPublishPort.class);
                    assertThat(context).hasSingleBean(OpportunityPredictionEventPublisher.class);
                    assertThat(context).hasSingleBean(TenderEventRegistrar.class);
                    assertThat(context).doesNotHaveBean(
                            com.ehsy.eventlibrary.clientsdk.systeminteraction.service.SystemInteractiveService.class);

                    TenderEventRegistrar registrar = context.getBean(TenderEventRegistrar.class);
                    ServiceRenewalComponent renewalComponent = (ServiceRenewalComponent)
                            ReflectionTestUtils.getField(registrar, "renewalComponent");
                    assertThat(renewalComponent).isNotNull();
                    ServiceRenewalProperties renewalProperties = (ServiceRenewalProperties)
                            ReflectionTestUtils.getField(renewalComponent, "renewalProperties");
                    assertThat(renewalProperties).isNotNull();
                    assertThat(renewalProperties.getInitialDelay()).isEqualTo(3L);
                    assertThat(renewalProperties.getPeriod()).isEqualTo(3L);
                    assertThat(renewalProperties.getRenewalDuration()).isEqualTo(3000L);

                    assertNoRequest(300, TimeUnit.MILLISECONDS);
                    context.publishEvent(new ApplicationReadyEvent(
                            new SpringApplication(TenderEventSdkConfig.class),
                            new String[0],
                            context,
                            Duration.ZERO));

                    RecordedRequest registerRequest = takeRequest(5);
                    assertThat(registerRequest.getPath()).isEqualTo("/eventbus/register");
                    JSONObject registerBody = JSONObject.parseObject(registerRequest.getBody().readUtf8());
                    assertThat(registerBody.getString("serviceName")).isEqualTo("bid");
                    assertThat(registerBody.getBooleanValue("enableRegister")).isTrue();
                    assertThat(registerBody.getString("serviceAddress"))
                            .isEqualTo("http://172.16.38.78:8080");

                    RecordedRequest renewalRequest = takeRequest(6);
                    assertThat(renewalRequest.getPath()).isEqualTo("/eventbus/renew");
                    JSONObject renewalBody = JSONObject.parseObject(renewalRequest.getBody().readUtf8());
                    assertThat(renewalBody.getString("serviceName")).isEqualTo("bid");
                    assertThat(renewalBody.getString("serviceAddress"))
                            .isEqualTo("http://172.16.38.78:8080");
                    assertThat(renewalBody.getLongValue("renewalDuration")).isEqualTo(3000L);
                    assertThat(renewalBody.get("kafkaConsumerStatus"))
                            .as("SDK 续约携带当前 Kafka 消费器运行状态")
                            .isInstanceOf(Boolean.class);
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

    @Test
    @DisplayName("标讯专用 advertised 地址优先于组织 SDK 回退地址")
    void registrar_prefersTenderAdvertisedAddress() {
        TenderEventSdkProperties properties = new TenderEventSdkProperties(
                true, mockWebServer.url("/").toString(), "bid", "10.20.30.40", 9090);
        MockEnvironment environment = new MockEnvironment()
                .withProperty("server.port", "18080")
                .withProperty("xiyu.integrations.organization.event-sdk.advertised-host", "172.16.38.78")
                .withProperty("xiyu.integrations.organization.event-sdk.advertised-port", "8080");

        try (TenderEventRegistrar registrar = new TenderEventRegistrar(properties, environment)) {
            ClientRegisterComponent registerComponent = (ClientRegisterComponent)
                    ReflectionTestUtils.getField(registrar, "registerComponent");
            ServerConfig serverConfig = (ServerConfig)
                    ReflectionTestUtils.getField(registerComponent, "serverConfig");

            assertThat(serverConfig.getHostAndPort()).isEqualTo("http://10.20.30.40:9090");
        }
    }

    @Test
    @DisplayName("organization canonical 缺失时回退 legacy advertised 地址")
    void registrar_fallsBackToOrganizationLegacyAdvertisedAddress() {
        TenderEventSdkProperties properties = new TenderEventSdkProperties(
                true, mockWebServer.url("/").toString(), "bid", "", 0);
        MockEnvironment environment = new MockEnvironment()
                .withProperty("server.port", "18080")
                .withProperty("xiyu.org.event.advertised-host", "172.16.38.78")
                .withProperty("xiyu.org.event.advertised-port", "8080");

        try (TenderEventRegistrar registrar = new TenderEventRegistrar(properties, environment)) {
            assertThat(advertisedAddress(registrar)).isEqualTo("http://172.16.38.78:8080");
        }
    }

    @Test
    @DisplayName("标讯可只覆盖 advertised port，host 继续回退 organization canonical")
    void registrar_allowsIndependentAdvertisedFieldOverride() {
        TenderEventSdkProperties properties = new TenderEventSdkProperties(
                true, mockWebServer.url("/").toString(), "bid", "", 9090);
        MockEnvironment environment = new MockEnvironment()
                .withProperty("server.port", "18080")
                .withProperty("xiyu.integrations.organization.event-sdk.advertised-host", "172.16.38.78")
                .withProperty("xiyu.integrations.organization.event-sdk.advertised-port", "8080");

        try (TenderEventRegistrar registrar = new TenderEventRegistrar(properties, environment)) {
            assertThat(advertisedAddress(registrar)).isEqualTo("http://172.16.38.78:9090");
        }
    }

    private static String advertisedAddress(TenderEventRegistrar registrar) {
        ClientRegisterComponent registerComponent = (ClientRegisterComponent)
                ReflectionTestUtils.getField(registrar, "registerComponent");
        ServerConfig serverConfig = (ServerConfig)
                ReflectionTestUtils.getField(registerComponent, "serverConfig");
        return serverConfig.getHostAndPort();
    }

    private void assertNoRequest(long timeout, TimeUnit unit) {
        try {
            assertThat(mockWebServer.takeRequest(timeout, unit))
                    .as("SDK does not register before ApplicationReadyEvent")
                    .isNull();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while checking for an early SDK request", e);
        }
    }

    private RecordedRequest takeRequest(long timeoutSeconds) {
        try {
            RecordedRequest request = mockWebServer.takeRequest(timeoutSeconds, TimeUnit.SECONDS);
            assertThat(request).as("SDK request received within %s seconds", timeoutSeconds).isNotNull();
            return request;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for SDK request", e);
        }
    }

    private static MockResponse jsonResponse(String body) {
        return new MockResponse()
                .setBody(body)
                .setHeader("Content-Type", "application/json");
    }
}
