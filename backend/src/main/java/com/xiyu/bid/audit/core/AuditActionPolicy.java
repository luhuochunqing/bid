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
            "ATTACHMENT_CHANGE"
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
