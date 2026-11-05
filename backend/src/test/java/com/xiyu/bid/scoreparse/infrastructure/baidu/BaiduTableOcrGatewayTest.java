package com.xiyu.bid.scoreparse.infrastructure.baidu;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

class BaiduTableOcrGatewayTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"username,86400,1", "userName,86400,1", "username,0,3"})
    void sendsOriginalParamsAsJsonStringAndUnwrapsBothResultTypes(String usernameField, long tokenLifetime, int expectedLogins) throws Exception {
        byte[] pdf;
        try (var document = new org.apache.pdfbox.pdmodel.PDDocument();
             var output = new java.io.ByteArrayOutputStream()) {
            document.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            document.save(output); pdf = output.toByteArray();
        }
        var mapper = new ObjectMapper();
        var logins = new AtomicInteger();
        var searches = new AtomicInteger();
        var failure = new AtomicReference<Throwable>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/user/login", exchange -> {
            logins.incrementAndGet();
            try {
                var login = mapper.readTree(exchange.getRequestBody());
                assertThat(login.path(usernameField).asText()).isEqualTo("tester");
                assertThat(login.path("password").asText()).isEqualTo("password");
                assertThat(exchange.getRequestURI().getQuery()).isNull();
            } catch (Throwable t) { failure.set(t); }
            byte[] body = ("{\"success\":true,\"code\":\"000000\",\"data\":{\"token\":\"test-token\",\"tokenRemainTime\":" + tokenLifetime + "}}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body); exchange.close();
        });
        server.createContext("/search", exchange -> {
            try {
                assertThat(exchange.getRequestHeaders().getFirst("satoken")).isEqualTo("test-token");
                assertThat(exchange.getRequestHeaders().getFirst("Content-Type")).isEqualTo("application/json");
                assertThat(exchange.getRequestURI().getQuery()).isNull();
                var request = mapper.readTree(exchange.getRequestBody());
                assertThat(request.path("code").asText()).isEqualTo("BAIDU_OCR_DOC_API");
                assertThat(request.path("noCache").asInt()).isEqualTo(1);
                assertThat(request.path("params").isTextual()).isTrue();
                var params = mapper.readTree(request.path("params").asText());
                assertThat(params.path("pdf_file").asText()).isEqualTo(Base64.getEncoder().encodeToString(pdf));
                assertThat(params.path("pdf_file_num").asText()).isEqualTo("1");
            } catch (Throwable t) { failure.set(t); }
            int count = searches.incrementAndGet();
            Object result = count == 1 ? "{\"table_num\":0,\"log_id\":123}" : mapper.readTree("{\"table_num\":0,\"log_id\":123}");
            byte[] body = mapper.writeValueAsBytes(java.util.Map.of("success", true,"code","000000","data",java.util.Map.of("isSuccess",count == 3 ? 0 : 1,"result",result)));
            exchange.sendResponseHeaders(200,body.length);
            exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try {
            var props = new BaiduOcrProperties();
            props.setGatewayEnabled(true);
            // Exercise the same property binding used by application.yml.
            new org.springframework.boot.context.properties.bind.Binder(new org.springframework.boot.context.properties.source.MapConfigurationPropertySource(java.util.Map.of(
                "username","tester","password","password","login-username-field",usernameField))).bind("", org.springframework.boot.context.properties.bind.Bindable.ofInstance(props));
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            props.setTokenUrl(base + "/user/login"); props.setTableUrl(base + "/search");
            var provider = new BaiduOcrCredentialsProvider() {
                public boolean isEnabled() { return true; }
                public String getApiKey() { return "legacy-key"; }
                public String getSecretKey() { return "legacy-secret"; }
                public String getUsername() { return "tester"; }
                public String getPassword() { return "password"; }
            };
            props.setUsername("wrong-environment-user");
            props.setPassword("wrong-environment-password");
            var client = new BaiduTableOcrClient(props, mapper, provider);
            assertThat(client.extractTablesFromPdf(pdf,1).logId()).isEqualTo(123L);
            assertThat(client.extractTablesFromPdf(pdf,1).logId()).isEqualTo(123L);
            assertThatThrownBy(() -> client.extractTablesFromPdf(pdf,1)).isInstanceOf(java.io.IOException.class);
            assertThat(logins.get()).isEqualTo(expectedLogins);
            assertThat(failure.get()).isNull();
        } finally { server.stop(0); }
    }
    @Test
    void rejectsFailedLoginWithoutCallingSearchOrExposingCredentials() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/user/login", exchange -> {
            byte[] body = "{\"code\":\"50003\",\"message\":\"sensitive-password\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try {
            var props = new BaiduOcrProperties();
            props.setGatewayEnabled(true);
            props.setUsername("tester"); props.setPassword("sensitive-password");
            props.setTokenUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/user/login");
            var client = new BaiduTableOcrClient(props, new ObjectMapper());
            assertThatThrownBy(client::getAccessToken).isInstanceOf(java.io.IOException.class)
                    .hasMessageContaining("50003").hasMessageNotContaining("sensitive-password");
        } finally { server.stop(0); }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
        "{\"success\":false,\"code\":\"50003\"}",
        "{\"success\":true,\"code\":\"000000\",\"data\":{\"isSuccess\":0}}",
        "{\"success\":true,\"code\":\"000000\",\"data\":{\"isSuccess\":1,\"result\":{}}}",
        "{\"success\":true,\"code\":\"000000\",\"data\":{\"isSuccess\":1,\"result\":{\"table_num\":1}}}",
        "{\"success\":true,\"code\":\"000000\",\"data\":{\"isSuccess\":1,\"result\":{\"table_num\":null}}}",
        "{\"success\":true,\"code\":\"000000\",\"data\":{\"isSuccess\":1,\"result\":{\"table_num\":2,\"tables_result\":[]}}}",
        "{\"success\":true,\"code\":\"000000\",\"data\":{\"isSuccess\":1,\"result\":\"invalid-json\"}}"
    })
    void rejectsGatewayAndMalformedResultsInsteadOfReturningEmptyTables(String result) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/login", exchange -> {
            byte[] body = "{\"success\":true,\"code\":\"000000\",\"data\":{\"token\":\"token\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body); exchange.close();
        });
        server.createContext("/search", exchange -> {
            byte[] body = result.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try {
            var props = new BaiduOcrProperties();
            props.setGatewayEnabled(true);
            props.setUsername("tester"); props.setPassword("password");
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            props.setTokenUrl(base + "/login"); props.setTableUrl(base + "/search");
            var client = new BaiduTableOcrClient(props, new ObjectMapper());
            assertThatThrownBy(() -> client.extractTablesFromPdf(new byte[]{1}, null))
                    .isInstanceOf(java.io.IOException.class);
        } finally { server.stop(0); }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
        "{}", "{\"table_num\":1}", "{\"table_num\":null}",
        "{\"table_num\":-1}", "{\"table_num\":\"0\"}", "{\"table_num\":0.5}",
        "{\"table_num\":2147483648}", "{\"table_num\":0,\"tables_result\":null}",
        "{\"table_num\":1,\"tables_result\":[]}", "{\"table_num\":0,\"tables_result\":[{}]}",
        "{\"tables_result\":[]}", "{\"table_num\":1,\"tables_result\":{}}",
        "{\"error_code\":null}", "{\"error_code\":0}", "{\"error_code\":\"6\"}",
        "{\"table_num\":1,\"tables_result\":[null]}"
    })
    void rejectsIncompleteTableResultsWithoutNetwork(String result) throws Exception {
        var mapper = new ObjectMapper();
        var client = new BaiduTableOcrClient(new BaiduOcrProperties(), mapper);
        for (Object representation : new Object[]{mapper.readTree(result), result}) {
            String envelope = mapper.writeValueAsString(java.util.Map.of("success", true, "code", "000000",
                    "data", java.util.Map.of("isSuccess", 1, "result", representation)));
            assertThatThrownBy(() -> client.parseTableResponse(envelope)).isInstanceOf(java.io.IOException.class);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
        "{\"table_num\":0}", "{\"table_num\":0,\"tables_result\":[]}",
        "{\"table_num\":1,\"tables_result\":[{}]}", "{\"error_code\":6,\"error_msg\":\"denied\"}"
    })
    void preservesValidTableResultsAndBusinessErrorsWithoutNetwork(String result) throws Exception {
        var mapper = new ObjectMapper();
        var client = new BaiduTableOcrClient(new BaiduOcrProperties(), mapper);
        var expected = mapper.readTree(result);
        for (Object representation : new Object[]{expected, result}) {
            String envelope = mapper.writeValueAsString(java.util.Map.of("success", true, "code", "000000",
                    "data", java.util.Map.of("isSuccess", 1, "result", representation)));
            var response = client.parseTableResponse(envelope);
            assertThat(response.isSuccess()).isEqualTo(!expected.has("error_code"));
            assertThat(response.tablesResult()).hasSize(expected.path("table_num").asInt(0));
            assertThat(response.errorCode()).isEqualTo(expected.has("error_code") ? 6 : null);
        }
    }

    @Test
    void disabledDeploymentPropagatesSidecarFailureForMemoryAndDiskInputs() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/extract-tables", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        server.start();
        var file = java.nio.file.Files.createTempFile("ocr-deployment-gate-", ".pdf");
        try {
            var props = new BaiduOcrProperties();
            var provider = new BaiduOcrCredentialsProvider() {
                public boolean isEnabled() { return true; }
                public String getApiKey() { return null; }
                public String getSecretKey() { return null; }
            };
            var mapper = new ObjectMapper();
            var enricher = new BaiduTableArtifactEnricher(new BaiduTableOcrClient(props, mapper, provider)) {
                @Override
                public com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact enrichIfEnabled(
                        com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact baseline, byte[] bytes, String name) {
                    throw new AssertionError("Disabled deployment must not attempt OCR fallback");
                }
            };
            var extractor = new com.xiyu.bid.scoreparse.application.SidecarDocumentArtifactExtractor(
                    "http://127.0.0.1:" + server.getAddress().getPort(), "", 5, mapper, enricher);
            assertThatThrownBy(() -> extractor.extract(new byte[]{1}, "test.pdf"))
                    .isInstanceOf(com.xiyu.bid.scoreparse.application.SidecarDocumentArtifactExtractor.DocumentArtifactUnavailable.class)
                    .hasMessageContaining("503");
            assertThatThrownBy(() -> extractor.extract(file, "test.pdf"))
                    .isInstanceOf(com.xiyu.bid.scoreparse.application.SidecarDocumentArtifactExtractor.DocumentArtifactUnavailable.class)
                    .hasMessageContaining("503");
        } finally {
            server.stop(0);
            java.nio.file.Files.deleteIfExists(file);
        }
    }

    @Test
    void gatewayRequiresExplicitDeploymentOptInBeforeAnyRequest() {
        var props = new BaiduOcrProperties();
        props.setEnabled(true);
        props.setUsername("tester"); props.setPassword("password");
        props.setTokenUrl("http://127.0.0.1:1/login");
        var client = new BaiduTableOcrClient(props, new ObjectMapper());
        assertThatThrownBy(client::getAccessToken).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not enabled");
    }

}
