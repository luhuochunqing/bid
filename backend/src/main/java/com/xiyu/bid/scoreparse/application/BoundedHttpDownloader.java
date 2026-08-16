package com.xiyu.bid.scoreparse.application;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * 招标文件 HTTP 下载：复用 HttpClient，Content-Length 超限不读 body，流式累计超过 50MB 中止。
 */
final class BoundedHttpDownloader {

    static final int MAX_BYTES = 50 * 1024 * 1024;
    static final String TOO_LARGE_MESSAGE = "招标文件超过 50MB，无法解析";

    private final HttpClient httpClient;

    BoundedHttpDownloader() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build());
    }

    BoundedHttpDownloader(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    byte[] get(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("下载招标文件失败 HTTP " + response.statusCode());
        }
        rejectIfDeclaredTooLarge(response.headers().firstValue("Content-Length"));
        try (InputStream body = response.body()) {
            return readAtMost(body);
        }
    }

    static void rejectIfDeclaredTooLarge(Optional<String> contentLength) {
        if (contentLength.isEmpty()) {
            return;
        }
        long length;
        try {
            length = Long.parseLong(contentLength.get().trim());
        } catch (NumberFormatException ignored) {
            return;
        }
        if (length > MAX_BYTES) {
            throw new IllegalStateException(TOO_LARGE_MESSAGE);
        }
    }

    static byte[] readAtMost(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int total = 0;
        int n;
        while ((n = in.read(buf)) != -1) {
            if (total + n > MAX_BYTES) {
                throw new IllegalStateException(TOO_LARGE_MESSAGE);
            }
            out.write(buf, 0, n);
            total += n;
        }
        return out.toByteArray();
    }
}
