package com.xiyu.bid.contractborrow.domain.service;

import com.xiyu.bid.contractborrow.domain.model.ContractBorrowApplication;
import com.xiyu.bid.contractborrow.domain.valueobject.ContractBorrowStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ContractBorrowLifecyclePolicyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 4, 21, 10, 30);

    @Test
    void submit_ShouldCreatePendingApplication() {
        ContractBorrowApplication application = sample(ContractBorrowStatus.PENDING_APPROVAL);

        assertThat(application.status()).isEqualTo(ContractBorrowStatus.PENDING_APPROVAL);
        assertThat(application.displayStatus(LocalDate.of(2026, 4, 21)))
                .isEqualTo(ContractBorrowStatus.PENDING_APPROVAL.name());
    }

    @Test
    void approve_PendingApplication_ShouldMoveToApproved() {
        var result = ContractBorrowLifecyclePolicy.approve(
                sample(ContractBorrowStatus.PENDING_APPROVAL),
                NOW,
                "张经理");

        assertThat(result.allowed()).isTrue();
        assertThat(result.application().status()).isEqualTo(ContractBorrowStatus.APPROVED);
        assertThat(result.application().approverName()).isEqualTo("张经理");
    }

    @Test
    void reject_ApprovedApplication_ShouldDeny() {
        var result = ContractBorrowLifecyclePolicy.reject(
                sample(ContractBorrowStatus.APPROVED),
                NOW,
                "合同信息不完整");

        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).contains("待审批");
    }

    @Test
    void return_ApprovedApplication_ShouldCloseAsReturned() {
        var result = ContractBorrowLifecyclePolicy.returnBack(
                sample(ContractBorrowStatus.APPROVED),
                NOW,
                "已归档");

        assertThat(result.allowed()).isTrue();
        assertThat(result.application().status()).isEqualTo(ContractBorrowStatus.RETURNED);
        assertThat(result.application().returnRemark()).isEqualTo("已归档");
    }

    @Test
    void overdue_ShouldBeDerivedOnlyForActiveBorrowingStatuses() {
        ContractBorrowApplication active = sample(ContractBorrowStatus.APPROVED)
                .withExpectedReturnDate(LocalDate.of(2026, 4, 20));
        ContractBorrowApplication returned = sample(ContractBorrowStatus.RETURNED)
                .withExpectedReturnDate(LocalDate.of(2026, 4, 20));

        assertThat(active.isOverdue(LocalDate.of(2026, 4, 21))).isTrue();
        assertThat(active.status()).isEqualTo(ContractBorrowStatus.APPROVED);
        assertThat(active.displayStatus(LocalDate.of(2026, 4, 21))).isEqualTo("OVERDUE");
        assertThat(returned.isOverdue(LocalDate.of(2026, 4, 21))).isFalse();
    }

    private ContractBorrowApplication sample(ContractBorrowStatus status) {
        return new ContractBorrowApplication(
                1L,
                "HT-2026-0421",
                "西域智算中心年度框架合同",
                "法务归档室",
                "小王",
                "销售一部",
                "西域智算中心",
                "投标文件复核",
                "原件借阅",
                LocalDate.of(2026, 4, 30),
                NOW,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                status
        );
    }
}
