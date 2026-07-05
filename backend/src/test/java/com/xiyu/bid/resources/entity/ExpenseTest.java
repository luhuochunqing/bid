package com.xiyu.bid.resources.entity;

import com.xiyu.bid.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExpenseTest {

    private Expense expense;

    @BeforeEach
    void setUp() {
        expense = Expense.builder()
                .id(1L)
                .projectId(100L)
                .category(Expense.ExpenseCategory.MATERIAL)
                .expenseType("差旅费")
                .amount(new BigDecimal("1000.00"))
                .date(LocalDate.now().minusDays(1))
                .createdBy("张三")
                .status(Expense.ExpenseStatus.PENDING_APPROVAL)
                .build();
    }

    @Test
    @DisplayName("markApproved - 状态为 PENDING_APPROVAL 时应正常通过")
    void markApproved_whenPendingApproval_shouldSucceed() {
        expense.markApproved("李四", "审批通过", Expense.ExpenseStatus.APPROVED);

        assertThat(expense.getStatus()).isEqualTo(Expense.ExpenseStatus.APPROVED);
        assertThat(expense.getApprovedBy()).isEqualTo("李四");
        assertThat(expense.getApprovalComment()).isEqualTo("审批通过");
    }

    @Test
    @DisplayName("markApproved - 状态为 REJECTED 时应正常通过")
    void markApproved_whenRejected_shouldSucceed() {
        expense.setStatus(Expense.ExpenseStatus.REJECTED);

        expense.markApproved("李四", "重新审批通过", Expense.ExpenseStatus.APPROVED);

        assertThat(expense.getStatus()).isEqualTo(Expense.ExpenseStatus.APPROVED);
    }

    @Test
    @DisplayName("markApproved - 状态为 APPROVED 时应抛出 BusinessException")
    void markApproved_whenAlreadyApproved_shouldThrowBusinessException() {
        expense.setStatus(Expense.ExpenseStatus.APPROVED);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> expense.markApproved("李四", "重复审批", Expense.ExpenseStatus.APPROVED));

        assertThat(ex.getCode()).isEqualTo(409);
        assertThat(ex.getMessage()).contains("not in an approvable state");
    }

    @Test
    @DisplayName("markApproved - 状态为 PAID 时应抛出 BusinessException")
    void markApproved_whenPaid_shouldThrowBusinessException() {
        expense.setStatus(Expense.ExpenseStatus.PAID);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> expense.markApproved("李四", "审批", Expense.ExpenseStatus.APPROVED));

        assertThat(ex.getCode()).isEqualTo(409);
    }

    @Test
    @DisplayName("requestReturn - 保证金类型且状态为 APPROVED 时应正常通过")
    void requestReturn_whenDepositAndApproved_shouldSucceed() {
        expense.setExpenseType("保证金");
        expense.setStatus(Expense.ExpenseStatus.APPROVED);

        expense.requestReturn("王五", "申请退款");

        assertThat(expense.getStatus()).isEqualTo(Expense.ExpenseStatus.RETURN_REQUESTED);
        assertThat(expense.getReturnComment()).isEqualTo("申请退款");
    }

    @Test
    @DisplayName("requestReturn - 非保证金类型时应抛出 BusinessException")
    void requestReturn_whenNotDeposit_shouldThrowBusinessException() {
        expense.setExpenseType("差旅费");
        expense.setStatus(Expense.ExpenseStatus.APPROVED);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> expense.requestReturn("王五", "申请退款"));

        assertThat(ex.getCode()).isEqualTo(409);
        assertThat(ex.getMessage()).contains("Only deposit-like expenses");
    }

    @Test
    @DisplayName("requestReturn - 已退回状态时应抛出 BusinessException")
    void requestReturn_whenAlreadyReturned_shouldThrowBusinessException() {
        expense.setExpenseType("保证金");
        expense.setStatus(Expense.ExpenseStatus.RETURNED);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> expense.requestReturn("王五", "重复申请退款"));

        assertThat(ex.getCode()).isEqualTo(409);
        assertThat(ex.getMessage()).contains("already been returned");
    }

    @Test
    @DisplayName("confirmReturn - 保证金类型且状态为 RETURN_REQUESTED 时应正常通过")
    void confirmReturn_whenDepositAndReturnRequested_shouldSucceed() {
        expense.setExpenseType("保证金");
        expense.setStatus(Expense.ExpenseStatus.RETURN_REQUESTED);

        expense.confirmReturn("赵六", "确认退款");

        assertThat(expense.getStatus()).isEqualTo(Expense.ExpenseStatus.RETURNED);
        assertThat(expense.getReturnComment()).isEqualTo("确认退款");
    }

    @Test
    @DisplayName("confirmReturn - 保证金类型且状态为 PAID 时应正常通过")
    void confirmReturn_whenDepositAndPaid_shouldSucceed() {
        expense.setExpenseType("保证金");
        expense.setStatus(Expense.ExpenseStatus.PAID);

        expense.confirmReturn("赵六", "确认退款");

        assertThat(expense.getStatus()).isEqualTo(Expense.ExpenseStatus.RETURNED);
    }

    @Test
    @DisplayName("confirmReturn - 非保证金类型时应抛出 BusinessException")
    void confirmReturn_whenNotDeposit_shouldThrowBusinessException() {
        expense.setExpenseType("差旅费");
        expense.setStatus(Expense.ExpenseStatus.APPROVED);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> expense.confirmReturn("赵六", "确认退款"));

        assertThat(ex.getCode()).isEqualTo(409);
        assertThat(ex.getMessage()).contains("Only deposit-like expenses");
    }

    @Test
    @DisplayName("confirmReturn - 状态为 PENDING_APPROVAL 时应抛出 BusinessException")
    void confirmReturn_whenPendingApproval_shouldThrowBusinessException() {
        expense.setExpenseType("保证金");
        expense.setStatus(Expense.ExpenseStatus.PENDING_APPROVAL);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> expense.confirmReturn("赵六", "确认退款"));

        assertThat(ex.getCode()).isEqualTo(409);
        assertThat(ex.getMessage()).contains("not awaiting return confirmation");
    }

    @Test
    @DisplayName("markPaid - 状态为 APPROVED 时应正常通过")
    void markPaid_whenApproved_shouldSucceed() {
        expense.setStatus(Expense.ExpenseStatus.APPROVED);

        expense.markPaid();

        assertThat(expense.getStatus()).isEqualTo(Expense.ExpenseStatus.PAID);
    }

    @Test
    @DisplayName("markPaid - 状态为 PAID 时应正常通过（幂等）")
    void markPaid_whenAlreadyPaid_shouldSucceed() {
        expense.setStatus(Expense.ExpenseStatus.PAID);

        expense.markPaid();

        assertThat(expense.getStatus()).isEqualTo(Expense.ExpenseStatus.PAID);
    }

    @Test
    @DisplayName("markPaid - 状态为 PENDING_APPROVAL 时应抛出 BusinessException")
    void markPaid_whenPendingApproval_shouldThrowBusinessException() {
        expense.setStatus(Expense.ExpenseStatus.PENDING_APPROVAL);

        BusinessException ex = assertThrows(BusinessException.class, expense::markPaid);

        assertThat(ex.getCode()).isEqualTo(409);
        assertThat(ex.getMessage()).contains("Only approved or already-paid expenses");
    }

    @Test
    @DisplayName("markPaid - 状态为 REJECTED 时应抛出 BusinessException")
    void markPaid_whenRejected_shouldThrowBusinessException() {
        expense.setStatus(Expense.ExpenseStatus.REJECTED);

        BusinessException ex = assertThrows(BusinessException.class, expense::markPaid);

        assertThat(ex.getCode()).isEqualTo(409);
    }

    @Test
    @DisplayName("updateDetails - 金额为负数时应抛出 IllegalArgumentException")
    void updateDetails_whenNegativeAmount_shouldThrowIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> expense.updateDetails(
                        null,
                        new BigDecimal("-100.00"),
                        null,
                        null,
                        null
                ));
        assertThat(ex.getMessage()).contains("must be positive");
    }

    @Test
    @DisplayName("updateDetails - 日期为未来时应抛出 IllegalArgumentException")
    void updateDetails_whenFutureDate_shouldThrowIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> expense.updateDetails(
                        null,
                        null,
                        LocalDate.now().plusDays(1),
                        null,
                        null
                ));
        assertThat(ex.getMessage()).contains("cannot be in the future");
    }

    @Test
    @DisplayName("isReturnable - 保证金类型应返回 true")
    void isReturnable_whenDeposit_shouldReturnTrue() {
        expense.setExpenseType("保证金");
        assertThat(expense.isReturnable()).isTrue();
    }

    @Test
    @DisplayName("isReturnable - 非保证金类型应返回 false")
    void isReturnable_whenNotDeposit_shouldReturnFalse() {
        expense.setExpenseType("差旅费");
        assertThat(expense.isReturnable()).isFalse();
    }
}