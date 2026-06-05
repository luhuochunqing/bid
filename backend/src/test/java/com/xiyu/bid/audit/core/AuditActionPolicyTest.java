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
}
