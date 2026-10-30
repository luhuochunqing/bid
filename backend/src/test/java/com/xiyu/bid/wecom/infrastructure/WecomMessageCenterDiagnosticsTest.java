package com.xiyu.bid.wecom.infrastructure;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sun.net.httpserver.HttpServer;
import com.xiyu.bid.wecom.config.WecomMessageCenterProperties;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class WecomMessageCenterDiagnosticsTest {
    @Test
    void businessFailureLogsResponseTraceWithoutMessageBodyOrApplicationCode() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> requestBody = new AtomicReference<>();
        server.createContext("/qywx/sendMSG", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"code\":500,\"message\":\"user mailbox missing\",\"trace\":\"trace-1393\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var body = exchange.getResponseBody()) {
                body.write(response);
            }
        });
        Logger logger = (Logger) LoggerFactory.getLogger(WecomMessageCenterClient.class);
        ListAppender<ILoggingEvent> diagnostics = new ListAppender<>();
        diagnostics.start();
        logger.addAppender(diagnostics);
        server.start();
        try {
            WecomMessageCenterProperties properties = new WecomMessageCenterProperties();
            properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            properties.setApplicationCode("private-app-code");
            var client = new WecomMessageCenterClient(new RestTemplateBuilder(), properties);
            var result = client.sendMessage("E007", "private-message-body", "employee@ehsy.com");
            var request = new ObjectMapper().readTree(requestBody.get());
            assertThat(request.path("email").asText()).isEqualTo("employee@ehsy.com");
            assertThat(request.path("userName").asText()).isEqualTo("E007");
            assertThat(result.code()).isEqualTo(500);
            assertThat(result.trace()).isEqualTo("trace-1393");
            assertThat(diagnostics.list).extracting(ILoggingEvent::getFormattedMessage)
                    .anySatisfy(message -> assertThat(message).contains(
                            "userName=E007", "code=500", "user mailbox missing", "trace=trace-1393"));
            assertThat(diagnostics.list).extracting(ILoggingEvent::getFormattedMessage)
                    .allSatisfy(message -> assertThat(message)
                            .doesNotContain("private-app-code", "private-message-body"));
        } finally {
            server.stop(0);
            logger.detachAppender(diagnostics);
            diagnostics.stop();
        }
    }
    @Test
    void legacyCallOmitsEmailAndSuccessfulResponseDoesNotLogFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> requestBody = new AtomicReference<>();
        server.createContext("/qywx/sendMSG", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"code\":0,\"message\":\"ok\",\"trace\":\"legacy\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (var body = exchange.getResponseBody()) {
                body.write(response);
            }
        });
        Logger logger = (Logger) LoggerFactory.getLogger(WecomMessageCenterClient.class);
        ListAppender<ILoggingEvent> diagnostics = new ListAppender<>();
        diagnostics.start();
        logger.addAppender(diagnostics);
        server.start();
        try {
            WecomMessageCenterProperties properties = new WecomMessageCenterProperties();
            properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            var client = new WecomMessageCenterClient(new RestTemplateBuilder(), properties);
            assertThat(client.sendMessage("E007", "legacy-message").code()).isZero();
            assertThat(new ObjectMapper().readTree(requestBody.get()).has("email")).isFalse();
            assertThat(diagnostics.list).extracting(ILoggingEvent::getFormattedMessage)
                    .noneMatch(message -> message.contains("business failure"));
        } finally {
            server.stop(0);
            logger.detachAppender(diagnostics);
            diagnostics.stop();
        }
    }

}
