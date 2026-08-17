// Input: 内嵌 HttpServer 的流式响应（小/大/超限/声明超限四种）
// Output: InMemory 快路径 / OnDisk spill 内容一致 / 配额异常 + 清理 / header 预检拒绝
// Pos: scoreparse/application — StreamingBidFileFetcher 单元测试（spec 045 T004）
package com.xiyu.bid.scoreparse.application;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class StreamingBidFileFetcherTest {

    private static final String TASK_1 = "task-1";
    private static final String TASK_2 = "task-2";
    private static final String TASK_3 = "task-3";

    @TempDir
    Path tempDir;

    private HttpServer server;
    private TempBidFileStore store;
    private StreamingBidFileFetcher fetcher;
    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        store = new TempBidFileStore(tempDir);
        // 测试用缩小阈值：内存 buffer 4KB、单文件上限 16KB（生产行为由阈值比例保证）
        fetcher = new StreamingBidFileFetcher(store, 4 * 1024L, 16 * 1024L, 30);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void serveBytes(String path, byte[] body) {
        server.createContext(path, exchange -> {
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
            exchange.close();
        });
    }

    @Test
    void smallResponse_returnsInMemoryWithoutTempFile() throws Exception {
        byte[] body = new byte[1024];
        body[0] = 42;
        serveBytes("/small", body);

        LoadedBidFile result = fetcher.fetch(baseUrl + "/small", TASK_1, "投标文件.pdf", "application/pdf");

        assertThat(result).isInstanceOf(LoadedBidFile.InMemory.class);
        LoadedBidFile.InMemory inMemory = (LoadedBidFile.InMemory) result;
        assertThat(inMemory.content()).isEqualTo(body);
        assertThat(inMemory.fileName()).isEqualTo("投标文件.pdf");
        assertThat(inMemory.contentType()).isEqualTo("application/pdf");
        // 快路径零临时文件
        try (var files = Files.list(tempDir)) {
            assertThat(files.count()).isZero();
        }
    }

    @Test
    void largeResponse_spillsToOnDiskWithMatchingContent() throws Exception {
        byte[] body = new byte[8 * 1024];
        for (int i = 0; i < body.length; i++) {
            body[i] = (byte) i;
        }
        serveBytes("/large", body);

        LoadedBidFile result = fetcher.fetch(baseUrl + "/large", TASK_2, "投标文件.pdf", "application/pdf");

        assertThat(result).isInstanceOf(LoadedBidFile.OnDisk.class);
        LoadedBidFile.OnDisk onDisk = (LoadedBidFile.OnDisk) result;
        assertThat(onDisk.tempFile()).isEqualTo(tempDir.resolve(TASK_2 + ".part"));
        assertThat(Files.readAllBytes(onDisk.tempFile())).isEqualTo(body);
        assertThat(onDisk.fileName()).isEqualTo("投标文件.pdf");
    }

    @Test
    void exceedsMaxFileBytes_throwsQuotaExceptionAndCleansUpTempFile() throws Exception {
        byte[] body = new byte[20 * 1024];
        serveBytes("/too-big", body);

        assertThatThrownBy(() -> fetcher.fetch(baseUrl + "/too-big", TASK_3, "投标文件.pdf", "application/pdf"))
                .isInstanceOf(BidFileQuotaException.class)
                .hasMessageContaining("配额");

        assertThat(Files.exists(tempDir.resolve(TASK_3 + ".part"))).isFalse();
    }

    @Test
    void declaredContentLengthOverLimit_rejectsWithoutReadingBody() {
        // 服务端只发 header（声明 1MB > 16KB 上限）并挂 latch 不写 body：
        // fetcher 若等 body 会阻塞超时；header 预检拒绝才能秒回
        CountDownLatch bodyGate = new CountDownLatch(1);
        server.createContext("/declared-huge", exchange -> {
            exchange.sendResponseHeaders(200, 1024 * 1024);
            try (OutputStream out = exchange.getResponseBody()) {
                bodyGate.await();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });

        assertTimeoutPreemptively(Duration.ofSeconds(3), () ->
                assertThatThrownBy(() ->
                        fetcher.fetch(baseUrl + "/declared-huge", "task-4", "投标文件.pdf", "application/pdf"))
                        .isInstanceOf(BidFileQuotaException.class));
        bodyGate.countDown();
    }
}
