// Input: 签名 URL / taskId / 文件名 / contentType
// Output: LoadedBidFile 二态（≤ram-buffer-bytes 内存快路径 / 超限 spill 落盘）
// Pos: scoreparse/application — 流式自适应 spill（spec 045 FR-001/FR-011，research R1）
package com.xiyu.bid.scoreparse.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

/**
 * 投标文件流式下载器：内存 buffer 累计 ≤ ram-buffer-bytes(50MB) 全程内存（快路径，
 * 零元数据依赖）；超过即 flush {taskId}.part 继续流式写盘（spill）；单文件累计
 * > max-file-bytes(512MB) 中止抛 BidFileQuotaException。
 * <p>骨架复用 BoundedHttpDownloader（ofInputStream + 8KB buf + Content-Length 预检）。
 */
@Component
public class StreamingBidFileFetcher {

    private final HttpClient httpClient;
    private final TempBidFileStore tempFileStore;
    private final long ramBufferBytes;
    private final long maxFileBytes;
    private final long downloadTimeoutSeconds;

    public StreamingBidFileFetcher(
            TempBidFileStore tempFileStore,
            @Value("${app.score-parse.bid-file.ram-buffer-bytes:52428800}") long ramBufferBytes,
            @Value("${app.score-parse.bid-file.max-file-bytes:536870912}") long maxFileBytes,
            @Value("${app.score-parse.bid-file.download-timeout-seconds:600}") long downloadTimeoutSeconds) {
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        this.tempFileStore = tempFileStore;
        this.ramBufferBytes = ramBufferBytes;
        this.maxFileBytes = maxFileBytes;
        this.downloadTimeoutSeconds = downloadTimeoutSeconds;
    }

    public LoadedBidFile fetch(String url, String taskId, String fileName, String contentType)
            throws IOException, InterruptedException {
        return fetch(url, taskId, fileName, contentType, this.ramBufferBytes, this.maxFileBytes);
    }

    public LoadedBidFile fetch(String url, String taskId, String fileName, String contentType,
                               long ramBufferBytes, long maxFileBytes)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(downloadTimeoutSeconds))
                .GET()
                .build();
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("下载投标文件失败 HTTP " + response.statusCode());
        }
        rejectIfDeclaredOverQuota(response.headers().firstValue("Content-Length"), maxFileBytes);
        try (InputStream body = response.body()) {
            return readWithAdaptiveSpill(body, taskId, fileName, contentType, ramBufferBytes, maxFileBytes);
        }
    }

    private void rejectIfDeclaredOverQuota(Optional<String> contentLength, long maxBytes) {
        if (contentLength.isEmpty()) {
            return;
        }
        long length;
        try {
            length = Long.parseLong(contentLength.get().trim());
        } catch (NumberFormatException ignored) {
            return;
        }
        if (length > maxBytes) {
            throw new BidFileQuotaException();
        }
    }

    private LoadedBidFile readWithAdaptiveSpill(InputStream body, String taskId,
                                                String fileName, String contentType,
                                                long ramBufferLimit, long maxFileLimit) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Path spillPath = null;
        OutputStream spillOut = null;
        byte[] buf = new byte[8192];
        long total = 0;
        try {
            int n;
            while ((n = body.read(buf)) != -1) {
                total += n;
                if (total > maxFileLimit) {
                    throw new BidFileQuotaException();
                }
                if (spillOut != null) {
                    tempFileStore.recordSpillChunk(taskId, n);
                    spillOut.write(buf, 0, n);
                } else if (total <= ramBufferLimit) {
                    buffer.write(buf, 0, n);
                } else {
                    // spill：内存 buffer flush 到临时文件，继续流式写盘
                    spillPath = tempFileStore.reserve(taskId);
                    spillOut = Files.newOutputStream(spillPath);
                    byte[] initial = buffer.toByteArray();
                    tempFileStore.recordSpillChunk(taskId, (long) initial.length + n);
                    spillOut.write(initial);
                    buffer.reset();
                    spillOut.write(buf, 0, n);
                }
            }
            if (spillOut == null) {
                return new LoadedBidFile.InMemory(buffer.toByteArray(), fileName, contentType);
            }
            spillOut.flush();
            spillOut.close();
            return new LoadedBidFile.OnDisk(spillPath, fileName, contentType);
        } catch (IOException | RuntimeException ex) {
            closeQuietly(spillOut);
            if (spillPath != null) {
                tempFileStore.release(taskId);
            }
            throw ex;
        }
    }

    private static void closeQuietly(OutputStream out) {
        if (out == null) {
            return;
        }
        try {
            out.close();
        } catch (IOException ignored) {
            // 中止路径上的关闭失败无需处理，临时文件随即删除
        }
    }
}
