// Input: Sidecar 协议版本守卫
// Output: 抽象 Sidecar 响应形态是否可供 v1 表格提取客户端消费
package com.xiyu.bid.scoreparse.application;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Optional;

final class SidecarSchemaContract {

    static final String SCHEMA_VERSION_HEADER = "X-Schema-Version";
    static final String EXPECTED_SCHEMA_VERSION = "1.0";

    private static final String MISMATCH_BASE = "Sidecar 表格提取协议版本不匹配：期望 "
            + SCHEMA_VERSION_HEADER + "=" + EXPECTED_SCHEMA_VERSION + "，实际";
    private static final String FALLBACK_HINT = "请升级调用方到 SidecarDocumentArtifactExtractor 或回退 Sidecar 版本。";

    private SidecarSchemaContract() {
    }

    static void requireV1Contract(JsonNode body, String responseSchemaVersion) {
        Optional<String> reason = versionMismatchReason(body, responseSchemaVersion);
        if (reason.isPresent()) {
            throw new IllegalStateException(reason.get());
        }
    }

    static Optional<String> versionMismatchReason(JsonNode body, String responseSchemaVersion) {
        if (responseSchemaVersion != null
                && !responseSchemaVersion.isBlank()
                && !responseSchemaVersion.startsWith("1.")) {
            return Optional.of(MISMATCH_BASE + "响应头 " + SCHEMA_VERSION_HEADER + "="
                    + responseSchemaVersion + "。"
                    + FALLBACK_HINT);
        }
        if (body != null && body.isObject()) {
            JsonNode schemaVersion = body.get("schemaVersion");
            if (schemaVersion != null && schemaVersion.isTextual()
                    && !schemaVersion.asText().startsWith("1.")) {
                return Optional.of(MISMATCH_BASE + "响应体 schemaVersion=" + schemaVersion.asText()
                        + "（Sidecar 回落到默认 v2 DocumentArtifact，可能是请求头被代理剥离或 Sidecar 已升级）。"
                        + FALLBACK_HINT);
            }
            if (body.has("artifact")) {
                return Optional.of(MISMATCH_BASE + "响应体为 v2 DocumentArtifact 形态（含 artifact 头且无 v1 schemaVersion）。"
                        + FALLBACK_HINT);
            }
        }
        return Optional.empty();
    }
}
