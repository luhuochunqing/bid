// Input: LLM 通道调用结果（成功携带裁决结论 / 响应不可解析 / Noop / 失败明细）
// Output: 不可变通道结果与 LLM 裁决结论（status ∈ PASS/FAIL/MANUAL）
// Pos: compliance/domain/policy/ - LLM 通道结果值对象（spec 049 US5 / T047，FP-Java：异常在边界吸收为值）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import java.util.Optional;

/**
 * LLM 裁决结论（纯核心值对象）.
 *
 * <p>status ∈ PASS/FAIL/MANUAL；MANUAL 必附 reasonCode ∈ PRD §3.5 五值集合.
 * JSON 解析属于边界 IO 行为，由 service 层适配器完成；纯核心只消费本 record.
 */
public record LlmVerdict(String status, String reasonCode, String conclusion, String suggestion) {

    /** 通道调用结果：成功携带结论 / 响应不可解析 / Noop（AI 未启用）/ 失败明细. */
    public sealed interface ChatOutcome<T> permits Success, Invalid, Noop, Failure {
    }

    /** 调用成功且响应已解析为裁决结论. */
    public record Success<T>(T verdict) implements ChatOutcome<T> {
    }

    /** 调用成功但响应不可解析为裁决结构：单项 MANUAL + LOW_CONFIDENCE. */
    public record Invalid<T>() implements ChatOutcome<T> {
    }

    /** AI 未启用/未配置（Noop）：语义项 MANUAL + MANUAL_REVIEW_REQUIRED. */
    public record Noop<T>() implements ChatOutcome<T> {
    }

    /** 调用失败（异常/超时），message 为失败明细：单项 MANUAL + LOW_CONFIDENCE 不阻断整体. */
    public record Failure<T>(String message) implements ChatOutcome<T> {
    }

    /** LLM 通道（副作用边界，由适配器实现；调用异常与 JSON 解析在边界吸收为本值类型）. */
    @FunctionalInterface
    public interface LlmChatPort<T> {
        ChatOutcome<T> chat(String prompt);
    }

    /** 成功携带结论. */
    public static <T> ChatOutcome<T> success(final T verdict) {
        return new Success<>(verdict);
    }

    /** 响应不可解析. */
    public static <T> ChatOutcome<T> invalid() {
        return new Invalid<>();
    }

    /** AI 未启用（Noop）. */
    public static <T> ChatOutcome<T> noop() {
        return new Noop<>();
    }

    /** 调用失败，携带明细. */
    public static <T> ChatOutcome<T> failure(final String message) {
        return new Failure<>(message);
    }

    /** 结构合法校验：status ∈ 三值集合；MANUAL 必附 conclusion. */
    public Optional<LlmVerdict> validated() {
        if (status == null || conclusion == null || suggestion == null) {
            return Optional.empty();
        }
        if (!status.equals("PASS") && !status.equals("FAIL") && !status.equals("MANUAL")) {
            return Optional.empty();
        }
        if (status.equals("MANUAL") && conclusion.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(this);
    }
}
