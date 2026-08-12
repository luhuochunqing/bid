package com.xiyu.bid.integration.tenderevent.infrastructure.sdk;

import com.xiyu.bid.integration.tenderevent.application.TenderEventPublishCommand;
import com.xiyu.bid.integration.tenderevent.domain.TenderEventCode;
import com.xiyu.bid.integration.tenderevent.domain.TenderEventPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("TenderEventSdkProducer - 直连 /eventbus/publishEvent")
class TenderEventSdkProducerTest {

    private static final String BASE_URL = "http://event-bus.local";

    private MockRestServiceServer server;
    private TenderEventSdkProducer producer;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        producer = new TenderEventSdkProducer(
                new TenderEventSdkProperties(false, BASE_URL, "bid"),
                restClient);
    }

    private TenderEventPublishCommand command() {
        return new TenderEventPublishCommand(
                TenderEventCode.BID_TENDER_CHANGE,
                new TenderEventPayload(123L, "ext-1"),
                "trace-1", "span-1", "parent-1");
    }

    @Test
    @DisplayName("发送成功：消息体结构正确、data 为自定义 map、trace 进 header")
    void publish_success_buildsBodyAndTraceHeaders() {
        server.expect(requestTo(BASE_URL + "/eventbus/publishEvent"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "serviceName": "bid",
                          "eventTopic": "BidTenderChange",
                          "eventSource": "bid",
                          "data": { "tenderId": 123, "externalId": "ext-1" }
                        }
                        """))
                .andExpect(header("traceId", "trace-1"))
                .andExpect(header("spanId", "span-1"))
                .andExpect(header("parentId", "parent-1"))
                .andRespond(withSuccess("{\"data\":{\"result\":true}}", MediaType.APPLICATION_JSON));

        boolean result = producer.publish(command());

        assertThat(result).isTrue();
        server.verify();
    }

    @Test
    @DisplayName("externalId 为空 → data 只含 tenderId")
    void publish_blankExternalId_omitsField() {
        server.expect(requestTo(BASE_URL + "/eventbus/publishEvent"))
                .andExpect(content().json("""
                        { "data": { "tenderId": 123 } }
                        """))
                .andRespond(withSuccess("{\"data\":{\"result\":true}}", MediaType.APPLICATION_JSON));

        TenderEventPublishCommand cmd = new TenderEventPublishCommand(
                TenderEventCode.BID_TENDER_CHANGE,
                new TenderEventPayload(123L, "  "),
                "t", "s", "p");
        boolean result = producer.publish(cmd);

        assertThat(result).isTrue();
        server.verify();
    }

    @Test
    @DisplayName("响应 result=false → 返回 false")
    void publish_responseResultFalse_returnsFalse() {
        server.expect(requestTo(BASE_URL + "/eventbus/publishEvent"))
                .andRespond(withSuccess("{\"data\":{\"result\":false}}", MediaType.APPLICATION_JSON));

        assertThat(producer.publish(command())).isFalse();
        server.verify();
    }

    @Test
    @DisplayName("服务端异常 → 返回 false（不抛异常）")
    void publish_serverError_returnsFalse() {
        server.expect(requestTo(BASE_URL + "/eventbus/publishEvent"))
                .andRespond(withServerError());

        assertThat(producer.publish(command())).isFalse();
        server.verify();
    }
}