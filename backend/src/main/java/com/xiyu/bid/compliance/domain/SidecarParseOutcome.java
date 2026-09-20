// Input: 解析终态分类 + 内容 + 诊断细节 + Sidecar 警告 + 是否建议重试
// Output: 不可变的解析结果值对象（替代"空文档哨兵"，调用侧保持零异常流）
// Pos: compliance/domain/ - Sidecar 解析结果值对象（FP-Java，无框架依赖）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain;

import java.util.List;

/**
 * Sidecar 解析终态结果.
 *
 * <p>五分类语义（契约见 specs/066-fix-quality-check-parse/contracts/parse-outcome-contract.md）：
 * OK / TIMEOUT / SERVICE_ERROR / EMPTY_TEXT / UNPARSEABLE.
 * 非 OK 时 {@link #content()} 恒为空文档，调用方不得消费其内容.
 */
public record SidecarParseOutcome(
        Kind kind,
        DocumentContent content,
        String detail,
        List<String> warnings,
        boolean retrySuggested) {

    /** 解析终态分类. */
    public enum Kind {
        /** 解析成功且有可消费文本/页. */
        OK,
        /** 请求读超时（含排队），文档本身无问题. */
        TIMEOUT,
        /** 服务侧/协议错误：连接失败、5xx、401/413、响应格式异常等. */
        SERVICE_ERROR,
        /** 解析成功但无文本（图片扫描件/无文本层）. */
        EMPTY_TEXT,
        /** 文档级错误（400/415/422 等）：格式不支持或损坏. */
        UNPARSEABLE
    }

    public SidecarParseOutcome {
        kind = kind == null ? Kind.SERVICE_ERROR : kind;
        content = content == null ? DocumentContent.empty() : content;
        detail = detail == null ? "" : detail;
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public static SidecarParseOutcome ok(final DocumentContent content, final List<String> warnings) {
        return new SidecarParseOutcome(Kind.OK, content, "", warnings, false);
    }

    public static SidecarParseOutcome timeout(final String detail) {
        return new SidecarParseOutcome(Kind.TIMEOUT, DocumentContent.empty(), detail, List.of(), true);
    }

    public static SidecarParseOutcome serviceError(final String detail) {
        return new SidecarParseOutcome(Kind.SERVICE_ERROR, DocumentContent.empty(), detail, List.of(), true);
    }

    /** 413 超上限：服务错误但不建议"稍后重试"（重试不会成功）. */
    public static SidecarParseOutcome oversize(final String detail) {
        return new SidecarParseOutcome(Kind.SERVICE_ERROR, DocumentContent.empty(), detail, List.of(), false);
    }

    public static SidecarParseOutcome emptyText(final String detail, final List<String> warnings) {
        return new SidecarParseOutcome(Kind.EMPTY_TEXT, DocumentContent.empty(), detail, warnings, false);
    }

    public static SidecarParseOutcome unparseable(final String detail) {
        return new SidecarParseOutcome(Kind.UNPARSEABLE, DocumentContent.empty(), detail, List.of(), false);
    }

    public boolean isOk() {
        return kind == Kind.OK;
    }
}
