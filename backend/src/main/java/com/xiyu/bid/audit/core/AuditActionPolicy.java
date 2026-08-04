package com.xiyu.bid.audit.core;

import java.util.Locale;
import java.util.Set;

/**
 * 纯核心：决定哪些操作需要进入关键操作记录。
 */
public final class AuditActionPolicy {

    private static final Set<String> QUERY_ACTIONS = Set.of(
            "READ",
            "QUERY",
            "VIEW",
            "SEARCH",
            "LIST",
            "GET"
    );

    private static final Set<String> KEY_ACTIONS = Set.of(
            "CREATE",
            "UPDATE",
            "DELETE",
            "PAY",
            "RETURN",
            "CANCEL",
            "BORROW",
            "RESOLVE",
            "TOGGLE",
            "VERIFY",
            "CLAIM",
            "ASSIGN",
            "APPROVE",
            "REJECT",
            "ARCHIVE",
            "SUBMIT",
            "WITHDRAW",
            "REGENERATE",
            "ASSEMBLE",
            "LOGIN",
            "LOGOUT",
            // PRD §5.3 投标全生命周期事件 (TenderLifecycleAuditAction)
            "SUBMITTED",
            "TRANSITIONED",
            "CHANGED",
            "REGISTERED",
            "REVIEWED",
            "CLOSED",
            "VIEW_PASSWORD",
            "ATTACHMENT_CHANGE",
            // CO-324: 项目生命周期过去分词与缺失动词——与 @Auditable action 实际命名对齐
            // 原集合仅含动词原形，PROJECT_CLOSURE_APPROVED / REGISTER_PROJECT_RESULT /
            // TRANSITION_EVALUATION_SUB_STAGE / ATTACH_EVALUATION_EVIDENCE / ABANDON_BID /
            // GATE_ADVANCE_TO_EVALUATION / PROJECT_REBID_CREATED 等均无法匹配，被错误丢弃。
            "APPROVED",      // PROJECT_CLOSURE_APPROVED 等 endsWith(_APPROVED)
            "REJECTED",      // PROJECT_CLOSURE_REJECTED 等 endsWith(_REJECTED)
            "CREATED",       // PROJECT_REBID_CREATED 等 endsWith(_CREATED)
            "REGISTER",      // REGISTER_PROJECT_RESULT 等 startsWith(REGISTER_)
            "ABANDON",       // ABANDON_BID 等 startsWith(ABANDON_)
            "GATE",          // GATE_ADVANCE_TO_EVALUATION 等 startsWith(GATE_)
            "ADVANCE",       // ADVANCE_* 推进类操作
            "TRANSITION",    // TRANSITION_EVALUATION_SUB_STAGE 等 startsWith(TRANSITION_)
            "ATTACH",        // ATTACH_EVALUATION_EVIDENCE 等 startsWith(ATTACH_)
            // CO-602: 敏感数据批量下载需留痕（业绩合订本/仓库附件导出下载）。
            // PR !2256 曾用 PERFORMANCE_BUNDLE_EXPORT_DOWNLOAD 作 action，因不命中任何
            // KEY 词被本策略静默丢弃——action 命名必须对齐 KEY_ACTIONS，见上方 CO-324 注释。
            "DOWNLOAD"
    );

    public boolean shouldRecord(String action) {
        if (action == null || action.isBlank()) {
            return false;
        }
        String normalized = normalize(action);
        if (QUERY_ACTIONS.stream().anyMatch(queryAction ->
                normalized.equals(queryAction) || normalized.startsWith(queryAction + "_"))) {
            return false;
        }
        return KEY_ACTIONS.stream().anyMatch(keyAction ->
                normalized.equals(keyAction)
                        || normalized.startsWith(keyAction + "_")
                        || normalized.endsWith("_" + keyAction));
    }

    public String normalize(String action) {
        return action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
    }
}
