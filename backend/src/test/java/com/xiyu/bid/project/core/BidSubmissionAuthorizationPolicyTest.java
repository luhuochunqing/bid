package com.xiyu.bid.project.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.xiyu.bid.project.entity.ProjectLeadAssignment;
import org.junit.jupiter.api.Test;

/**
 * BidSubmissionAuthorizationPolicy 独立单元测试（纯核心，无 Spring/Mockito）。
 * 覆盖所有 Decision 路径：角色白名单、直接放行、项目级负责人匹配、边界条件。
 */
class BidSubmissionAuthorizationPolicyTest {

    private static ProjectLeadAssignment lead(Long primary, Long secondary) {
        return ProjectLeadAssignment.builder()
                .id(1L).projectId(100L)
                .primaryLeadUserId(primary)
                .secondaryLeadUserId(secondary)
                .build();
    }

    // ── 角色白名单 ────────────────────────────────────────────────────────────

    @Test
    void canSubmitBid_nullRole_denied_identity() {
        var d = BidSubmissionAuthorizationPolicy.canSubmitBid(null, 1L, lead(1L, 2L));
        assertThat(d.allowed()).isFalse();
        assertThat(d.cause()).isEqualTo(BidSubmissionAuthorizationPolicy.Decision.Cause.IDENTITY);
        assertThat(d.reason()).contains("当前角色无权限");
    }

    @Test
    void canSubmitBid_emptyRole_denied_identity() {
        var d = BidSubmissionAuthorizationPolicy.canSubmitBid("", 1L, lead(1L, 2L));
        assertThat(d.allowed()).isFalse();
    }

    @Test
    void canSubmitBid_adminStaff_notInWhitelist_denied_identity() {
        var d = BidSubmissionAuthorizationPolicy.canSubmitBid("bid-administration", 1L, lead(1L, 2L));
        assertThat(d.allowed()).isFalse();
        assertThat(d.reason()).contains("当前角色无权限");
    }

    @Test
    void canSubmitBid_bidOtherDept_notInWhitelist_denied_identity() {
        var d = BidSubmissionAuthorizationPolicy.canSubmitBid("bid-otherDept", 1L, lead(1L, 2L));
        assertThat(d.allowed()).isFalse();
    }

    // ── 直接放行角色：admin / bid_admin / bid_lead ───────────────────────────

    @Test
    void canSubmitBid_admin_directPermit_noLeadNeeded() {
        var d = BidSubmissionAuthorizationPolicy.canSubmitBid("admin", 1L, null);
        assertThat(d.allowed()).isTrue();
        assertThat(d.cause()).isNull();
        assertThat(d.reason()).isNull();
    }

    @Test
    void canSubmitBid_bidAdmin_directPermit_noLeadNeeded() {
        var d = BidSubmissionAuthorizationPolicy.canSubmitBid("/bidAdmin", 1L, null);
        assertThat(d.allowed()).isTrue();
    }

    @Test
    void canSubmitBid_bidLead_directPermit_noLeadNeeded() {
        var d = BidSubmissionAuthorizationPolicy.canSubmitBid("bid-TeamLeader", 1L, null);
        assertThat(d.allowed()).isTrue();
    }

    @Test
    void canSubmitBid_admin_directPermit_evenIfLeadDoesNotMatch() {
        // admin 路径不查 lead，即使 lead 分配给别人也放行
        var d = BidSubmissionAuthorizationPolicy.canSubmitBid("admin", 1L, lead(99L, 88L));
        assertThat(d.allowed()).isTrue();
    }

    // ── sales：仅匹配 primaryLeadUserId ──────────────────────────────────────

    @Test
    void canSubmitBid_sales_asPrimaryLead_allowed() {
        var d = BidSubmissionAuthorizationPolicy.canSubmitBid("bid-projectLeader", 1L, lead(1L, 2L));
        assertThat(d.allowed()).isTrue();
    }

    @Test
    void canSubmitBid_sales_asSecondaryLead_denied() {
        // sales 只能匹配 primaryLead，不能匹配 secondaryLead
        var d = BidSubmissionAuthorizationPolicy.canSubmitBid("bid-projectLeader", 1L, lead(2L, 1L));
        assertThat(d.allowed()).isFalse();
        assertThat(d.reason()).contains("不是该项目的投标负责人");
    }

    @Test
    void canSubmitBid_sales_notLead_denied() {
        var d = BidSubmissionAuthorizationPolicy.canSubmitBid("bid-projectLeader", 1L, lead(2L, 3L));
        assertThat(d.allowed()).isFalse();
    }

    @Test
    void canSubmitBid_sales_nullCurrentUserId_denied() {
        var d = BidSubmissionAuthorizationPolicy.canSubmitBid("bid-projectLeader", null, lead(1L, 2L));
        assertThat(d.allowed()).isFalse();
    }

    // ── bid_specialist：匹配 primaryLeadUserId 或 secondaryLeadUserId ──────────

    @Test
    void canSubmitBid_bidSpecialist_asSecondaryLead_allowed() {
        var d = BidSubmissionAuthorizationPolicy.canSubmitBid("bid-Team", 1L, lead(2L, 1L));
        assertThat(d.allowed()).isTrue();
    }

    @Test
    void canSubmitBid_bidSpecialist_asPrimaryLead_allowed() {
        var d = BidSubmissionAuthorizationPolicy.canSubmitBid("bid-Team", 1L, lead(1L, 2L));
        assertThat(d.allowed()).isTrue();
    }

    @Test
    void canSubmitBid_bidSpecialist_notLead_denied() {
        var d = BidSubmissionAuthorizationPolicy.canSubmitBid("bid-Team", 1L, lead(2L, 3L));
        assertThat(d.allowed()).isFalse();
    }

    // ── lead 为 null / 字段为 null ───────────────────────────────────────────

    @Test
    void canSubmitBid_sales_leadNull_denied_withContactAdminMessage() {
        var d = BidSubmissionAuthorizationPolicy.canSubmitBid("bid-projectLeader", 1L, null);
        assertThat(d.allowed()).isFalse();
        assertThat(d.reason()).contains("尚未分配投标负责人");
        assertThat(d.reason()).contains("联系管理员");
    }

    @Test
    void canSubmitBid_bidSpecialist_leadNull_denied() {
        var d = BidSubmissionAuthorizationPolicy.canSubmitBid("bid-Team", 1L, null);
        assertThat(d.allowed()).isFalse();
        assertThat(d.reason()).contains("尚未分配投标负责人");
    }

    @Test
    void canSubmitBid_sales_primaryLeadNull_denied() {
        // lead 存在但 primaryLeadUserId 为 null
        var d = BidSubmissionAuthorizationPolicy.canSubmitBid("bid-projectLeader", 1L, lead(null, 2L));
        assertThat(d.allowed()).isFalse();
    }

    @Test
    void canSubmitBid_bidSpecialist_secondaryLeadNull_denied() {
        var d = BidSubmissionAuthorizationPolicy.canSubmitBid("bid-Team", 1L, lead(2L, null));
        assertThat(d.allowed()).isFalse();
    }

    // ── Decision 值对象语义 ──────────────────────────────────────────────────

    @Test
    void decision_permit_hasNullCauseAndReason() {
        var d = BidSubmissionAuthorizationPolicy.canSubmitBid("admin", 1L, null);
        assertThat(d.allowed()).isTrue();
        assertThat(d.cause()).isNull();
        assertThat(d.reason()).isNull();
    }

    @Test
    void decision_deny_hasCauseAndReason() {
        var d = BidSubmissionAuthorizationPolicy.canSubmitBid("bid-administration", 1L, null);
        assertThat(d.allowed()).isFalse();
        assertThat(d.cause()).isEqualTo(BidSubmissionAuthorizationPolicy.Decision.Cause.IDENTITY);
        assertThat(d.reason()).isNotBlank();
    }

    @Test
    void decision_causeEnum_hasIdentityAndState() {
        // 确保 Cause enum 有 IDENTITY 和 STATE 两个值（供编排层映射 HTTP 状态码）
        var causes = BidSubmissionAuthorizationPolicy.Decision.Cause.values();
        assertThat(causes).contains(
                BidSubmissionAuthorizationPolicy.Decision.Cause.IDENTITY,
                BidSubmissionAuthorizationPolicy.Decision.Cause.STATE);
    }
}
