package com.xiyu.bid.audit.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditActionPolicyTest {

    private final AuditActionPolicy policy = new AuditActionPolicy();

    @Test
    void shouldSkipQueryLikeActions() {
        assertFalse(policy.shouldRecord("READ"));
        assertFalse(policy.shouldRecord("QUERY_PROJECTS"));
        assertFalse(policy.shouldRecord("view_detail"));
        assertFalse(policy.shouldRecord(" SEARCH "));
        assertFalse(policy.shouldRecord("LIST_TENDERS"));
        assertFalse(policy.shouldRecord("GET_BY_ID"));
        assertFalse(policy.shouldRecord("EXPORT"));
        assertFalse(policy.shouldRecord("AI_ANALYZE"));
    }

    @Test
    void shouldRecordKeyChangeActions() {
        assertTrue(policy.shouldRecord("CREATE"));
        assertTrue(policy.shouldRecord("UPDATE"));
        assertTrue(policy.shouldRecord("DELETE"));
        assertTrue(policy.shouldRecord("UPDATE_STATUS"));
        assertTrue(policy.shouldRecord("BATCH_UPDATE"));
        assertTrue(policy.shouldRecord("BATCH_CLAIM"));
        assertTrue(policy.shouldRecord("APPROVE"));
    }

    /**
     * CO-324: 项目生命周期过去分词与组合形式 action 不应被 shouldRecord 丢弃。
     * 修复前这些 action 因 KEY_ACTIONS 仅含动词原形、匹配规则不命中而被错误过滤，
     * 导致 audit_log 不写入、项目动态只剩前端伪造的"创建"基线。
     */
    @Test
    void shouldRecordProjectLifecycleActions() {
        // 过去分词形式（endsWith 匹配）
        assertTrue(policy.shouldRecord("PROJECT_CLOSURE_APPROVED"));
        assertTrue(policy.shouldRecord("PROJECT_CLOSURE_REJECTED"));
        assertTrue(policy.shouldRecord("PROJECT_REBID_CREATED"));
        // 动词原形开头（startsWith 匹配）
        assertTrue(policy.shouldRecord("REGISTER_PROJECT_RESULT"));
        assertTrue(policy.shouldRecord("ABANDON_BID"));
        assertTrue(policy.shouldRecord("GATE_ADVANCE_TO_EVALUATION"));
        assertTrue(policy.shouldRecord("TRANSITION_EVALUATION_SUB_STAGE"));
        assertTrue(policy.shouldRecord("ATTACH_EVALUATION_EVIDENCE"));
        // createProject 新增注解
        assertTrue(policy.shouldRecord("CREATE_PROJECT"));
        // CO-324 事件驱动动态
        assertTrue(policy.shouldRecord("PROJECT_STAGE_TRANSITIONED"));
    }

    /**
     * CO-602: 敏感数据批量下载需留痕。PR !2256 的 PERFORMANCE_BUNDLE_EXPORT_DOWNLOAD
     * 因 DOWNLOAD 不在 KEY_ACTIONS 被静默丢弃；补齐后短形式与复合形式均应命中。
     */
    @Test
    void shouldRecordDownloadActions() {
        assertTrue(policy.shouldRecord("DOWNLOAD"));
        assertTrue(policy.shouldRecord("PERFORMANCE_BUNDLE_EXPORT_DOWNLOAD"));
        assertTrue(policy.shouldRecord("download"));
    }
}
