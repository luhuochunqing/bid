// Input: AI Provider 调用元数据（provider、model、耗时、异常及阶段调用）
// Output: 纯领域可观测性记录载体与 run_summary Map 投影（无框架依赖、内建脱敏）
// Pos: scoreparse/domain — AI 提供方诊断载体
package com.xiyu.bid.scoreparse.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 评分解析 AI Provider 调用诊断载体（FP-Java 纯领域值对象）。
 */
public record AiProviderDiagnostics(
        String provider,
        String model,
        String status,
        Long latencyMs,
        String errorClass,
        String errorMessage,
        List<AiCallDetail> calls
) {

    private static final Pattern SENSITIVE_KEY_PATTERN = Pattern.compile(
            "(?i)(sk-[a-zA-Z0-9_\\-]{8,}|Bearer\\s+[a-zA-Z0-9_\\-\\.]+)"
    );

    public record AiCallDetail(
            String phase,
            String provider,
            String model,
            String status,
            Long latencyMs,
            String errorClass,
            String errorMessage
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            if (phase != null) map.put("phase", phase);
            if (provider != null) map.put("provider", provider);
            if (model != null) map.put("model", model);
            if (status != null) map.put("status", status);
            if (latencyMs != null) map.put("latencyMs", latencyMs);
            if (errorClass != null) map.put("errorClass", errorClass);
            if (errorMessage != null) map.put("errorMessage", errorMessage);
            return map;
        }
    }

    public static AiProviderDiagnostics empty() {
        return new AiProviderDiagnostics("", "", "", null, null, null, Collections.emptyList());
    }

    public static AiProviderDiagnostics success(String provider, String model, Long latencyMs, List<AiCallDetail> calls) {
        return new AiProviderDiagnostics(
                provider == null ? "" : provider,
                model == null ? "" : model,
                "SUCCESS",
                latencyMs,
                null,
                null,
                calls == null ? Collections.emptyList() : List.copyOf(calls)
        );
    }

    public static AiProviderDiagnostics failure(String provider, String model, Long latencyMs, Throwable throwable) {
        String errClass = throwable != null ? throwable.getClass().getSimpleName() : null;
        String errMsg = throwable != null ? sanitize(throwable.getMessage()) : null;
        return new AiProviderDiagnostics(
                provider == null ? "" : provider,
                model == null ? "" : model,
                "FAILED",
                latencyMs,
                errClass,
                errMsg,
                Collections.emptyList()
        );
    }

    public boolean isEmpty() {
        return (provider == null || provider.isBlank())
                && (model == null || model.isBlank())
                && (status == null || status.isBlank())
                && latencyMs == null
                && errorClass == null
                && (errorMessage == null || errorMessage.isBlank())
                && (calls == null || calls.isEmpty());
    }

    public Map<String, Object> toMap() {
        if (isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> map = new LinkedHashMap<>();
        if (provider != null && !provider.isBlank()) {
            map.put("provider", provider);
        }
        if (model != null && !model.isBlank()) {
            map.put("model", model);
        }
        if (status != null && !status.isBlank()) {
            map.put("status", status);
        }
        if (latencyMs != null) {
            map.put("latencyMs", latencyMs);
        }
        if (errorClass != null && !errorClass.isBlank()) {
            map.put("errorClass", errorClass);
        }
        if (errorMessage != null && !errorMessage.isBlank()) {
            map.put("errorMessage", errorMessage);
        }
        if (calls != null && !calls.isEmpty()) {
            List<Map<String, Object>> callList = new ArrayList<>();
            for (AiCallDetail call : calls) {
                if (call != null) {
                    callList.add(call.toMap());
                }
            }
            if (!callList.isEmpty()) {
                map.put("calls", callList);
            }
        }
        return map;
    }

    /**
     * 对异常信息或日志做脱敏处理，杜绝 API Key / Token 等凭据泄漏。
     */
    public static String sanitize(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        return SENSITIVE_KEY_PATTERN.matcher(raw).replaceAll("sk-***");
    }
}
