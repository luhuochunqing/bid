package com.xiyu.bid.audit.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TenderLifecycleAuditActionTest {

    private final AuditActionPolicy policy = new AuditActionPolicy();

    @Test
    void allTenderLifecycleEventsShouldBeRecorded() {
        for (String action : TenderLifecycleAuditAction.all()) {
            assertThat(policy.shouldRecord(action))
                    .as("audit policy must record %s", action)
                    .isTrue();
        }
    }

    @Test
    void allEventCodesAreUniqueAndPresent() {
        assertThat(TenderLifecycleAuditAction.all()).hasSize(8).doesNotContainNull();
    }
}
