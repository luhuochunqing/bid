package com.xiyu.bid.casework.application;

import com.sun.net.httpserver.HttpServer;
import com.xiyu.bid.scoreparse.application.LoadedBidFile;
import com.xiyu.bid.scoreparse.application.StreamingBidFileFetcher;
import com.xiyu.bid.scoreparse.application.TempBidFileStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.List;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class BidDocumentLoaderIsolationIntegrationTest {
    @TempDir Path tempDir;

    @Test
    void threeConcurrentLargeFileDownloadsUseIndependentPartFiles() throws Exception {
        byte[] payload = new byte[256 * 1024];
        for (int i = 0; i < payload.length; i++) payload[i] = (byte) (i % 251);
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/bid.docx", exchange -> {
            exchange.sendResponseHeaders(200, payload.length);
            exchange.getResponseBody().write(payload);
            exchange.close();
        });
        server.start();
        try {
            TempBidFileStore store = new TempBidFileStore(tempDir.toString(), 10 * 1024 * 1024);
            StreamingBidFileFetcher fetcher = new StreamingBidFileFetcher(store, 1024, 1024 * 1024, 30);
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/bid.docx";
            try (var pool = Executors.newFixedThreadPool(3)) {
                var futures = List.of("run-a", "run-b", "run-c").stream()
                        .map(id -> pool.submit(() -> fetcher.fetch(url, id, "bid.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")))
                        .toList();
                List<Path> paths = futures.stream().map(f -> {
                    try { return ((LoadedBidFile.OnDisk) f.get()).tempFile(); }
                    catch (Exception e) { throw new AssertionError(e); }
                }).toList();
                assertThat(paths).containsExactlyInAnyOrder(
                        tempDir.resolve("run-a.part"), tempDir.resolve("run-b.part"), tempDir.resolve("run-c.part"));
                String expected = hex(MessageDigest.getInstance("SHA-256").digest(payload));
                assertThat(paths).allSatisfy(path -> {
                    try { assertThat(hex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)))).isEqualTo(expected); }
                    catch (Exception e) { throw new AssertionError(e); }
                });
                store.release("run-a");
                assertThat(paths.get(1)).exists();
                assertThat(paths.get(2)).exists();
            }
        } finally {
            server.stop(0);
        }
    }

    private static String hex(byte[] bytes) { return java.util.HexFormat.of().formatHex(bytes); }
}
