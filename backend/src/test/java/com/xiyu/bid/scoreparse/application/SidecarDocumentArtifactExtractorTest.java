package com.xiyu.bid.scoreparse.application;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SidecarDocumentArtifactExtractorTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void requestsV21AndAcceptsCompatibleV20Response() throws Exception {
        AtomicReference<String> requestedVersion = new AtomicReference<>();
        SidecarDocumentArtifactExtractor extractor = extractor("2.0", requestedVersion);

        extractor.extract(new byte[]{1}, "source.docx");

        assertThat(requestedVersion.get()).isEqualTo("2.1");
    }

    @Test
    void rejectsMissingOrUnsupportedResponseSchemaVersion() {
        for (String version : new String[]{"", "2.2"}) {
            SidecarDocumentArtifactExtractor extractor = extractor(version, new AtomicReference<>());
            assertThatThrownBy(() -> extractor.extract(new byte[]{1}, "source.docx"))
                    .isInstanceOf(SidecarDocumentArtifactExtractor.DocumentArtifactUnavailable.class);
            stopServer();
            server = null;
        }
    }

    private SidecarDocumentArtifactExtractor extractor(String version, AtomicReference<String> requestedVersion) {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/extract-tables", exchange -> {
                requestedVersion.set(exchange.getRequestHeaders().getFirst("X-Schema-Version"));
                exchange.getRequestBody().readAllBytes();
                String schema = version.isBlank() ? "" : "\"schemaVersion\":\"" + version + "\",";
                byte[] body = ("{" + schema
                        + "\"artifact\":{\"documentId\":\"doc\",\"sha256\":\"sha\","
                        + "\"fileType\":\"DOCX\",\"extractionVersion\":\"table-v2\"},"
                        + "\"tables\":[],\"warnings\":[]}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("X-Schema-Version", version.isBlank() ? "2.1" : version);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            return new SidecarDocumentArtifactExtractor("http://127.0.0.1:" + server.getAddress().getPort(),
                    "", 5, mapper);
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }
}
