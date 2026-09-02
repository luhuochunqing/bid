// Input: multipart boundary + filename + error-stream
// Output: multipart framing byte[] and error-snippet string
// Pos: scoreparse/application — shared by all Sidecar HTTP clients
package com.xiyu.bid.scoreparse.application;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * multipart/form-data framing helpers shared by Sidecar HTTP clients.
 */
final class SidecarMultipart {

    static final String SIDECAR_KEY_HEADER = "X-Sidecar-Key";
    static final int ERROR_SNIPPET_MAX_CHARS = 512;

    private SidecarMultipart() {
    }

    static byte[] prefix(String boundary, String fileName) {
        return ("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    static byte[] suffix(String boundary) {
        return ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
    }

    static String readErrorSnippet(InputStream body) {
        try (body) {
            byte[] buf = new byte[ERROR_SNIPPET_MAX_CHARS];
            int n = body.read(buf);
            return n <= 0 ? "" : new String(buf, 0, n, StandardCharsets.UTF_8).trim();
        } catch (IOException ex) {
            return "";
        }
    }
}
