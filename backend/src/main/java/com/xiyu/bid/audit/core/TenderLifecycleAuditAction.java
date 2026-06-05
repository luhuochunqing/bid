// Input: tender-lifecycle audit event identifiers
// Output: stable audit action codes consumed by AuditActionPolicy
// Pos: audit/core - pure-core enumeration of WS-H audit events
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.audit.core;

import java.util.List;

/**
 * 纯核心：投标全生命周期阶段的关键审计事件常量。
 * 与 {@link AuditActionPolicy} 协作：所有事件都包含 KEY_ACTIONS 中的关键词
 * （SUBMIT / TRANSITIONED / CHANGED 等同义词），shouldRecord() 必须返回 true。
 */
public final class TenderLifecycleAuditAction {

    public static final String PROJECT_INITIATION_SUBMITTED = "PROJECT_INITIATION_SUBMITTED";
    public static final String PROJECT_STAGE_TRANSITIONED = "PROJECT_STAGE_TRANSITIONED";
    public static final String PROJECT_EVALUATION_SUBSTAGE_CHANGED = "PROJECT_EVALUATION_SUBSTAGE_CHANGED";
    public static final String PROJECT_RESULT_REGISTERED = "PROJECT_RESULT_REGISTERED";
    public static final String PROJECT_RETROSPECTIVE_SUBMITTED = "PROJECT_RETROSPECTIVE_SUBMITTED";
    public static final String PROJECT_RETROSPECTIVE_REVIEWED = "PROJECT_RETROSPECTIVE_REVIEWED";
    public static final String PROJECT_DEPOSIT_RETURN_REGISTERED = "PROJECT_DEPOSIT_RETURN_REGISTERED";
    public static final String PROJECT_CLOSED = "PROJECT_CLOSED";

    private TenderLifecycleAuditAction() {
    }

    public static List<String> all() {
        return List.of(
                PROJECT_INITIATION_SUBMITTED,
                PROJECT_STAGE_TRANSITIONED,
                PROJECT_EVALUATION_SUBSTAGE_CHANGED,
                PROJECT_RESULT_REGISTERED,
                PROJECT_RETROSPECTIVE_SUBMITTED,
                PROJECT_RETROSPECTIVE_REVIEWED,
                PROJECT_DEPOSIT_RETURN_REGISTERED,
                PROJECT_CLOSED
        );
    }
}
