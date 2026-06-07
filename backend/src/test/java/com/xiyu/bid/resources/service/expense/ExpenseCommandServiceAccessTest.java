package com.xiyu.bid.resources.service.expense;

import com.xiyu.bid.resources.dto.ExpenseApproveRequest;
import com.xiyu.bid.resources.dto.ExpenseUpdateRequest;
import com.xiyu.bid.resources.entity.Expense;
import com.xiyu.bid.resources.repository.ExpenseApprovalRecordRepository;
import com.xiyu.bid.resources.repository.ExpenseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpenseCommandServiceAccessTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private ExpenseApprovalRecordRepository approvalRecordRepository;

    @Mock
    private ExpenseAccessGuard accessGuard;

    @Test
    void updateExpense_ShouldRejectInvisibleProjectBeforeSaving() {
        ExpenseCommandService service = newService();
        when(expenseRepository.findById(1L)).thenReturn(Optional.of(expense(1L, 99L)));
        org.mockito.Mockito.doThrow(new AccessDeniedException("权限不足"))
                .when(accessGuard).assertCanAccessProject(99L);

        ExpenseUpdateRequest request = new ExpenseUpdateRequest();
        request.setCategory(Expense.ExpenseCategory.OTHER);
        request.setAmount(new BigDecimal("120.00"));
        request.setDate(LocalDate.now().minusDays(1));
        request.setExpenseType("保证金");
        request.setDescription("更新");

        assertThatThrownBy(() -> service.updateExpense(1L, request))
                .isInstanceOf(AccessDeniedException.class);

        verify(expenseRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void approveExpense_ShouldRejectInvisibleProjectBeforeWritingApprovalRecord() {
        ExpenseCommandService service = newService();
        when(expenseRepository.findById(1L)).thenReturn(Optional.of(expense(1L, 99L)));
        org.mockito.Mockito.doThrow(new AccessDeniedException("权限不足"))
                .when(accessGuard).assertCanAccessProject(99L);

        ExpenseApproveRequest request = new ExpenseApproveRequest();
        request.setResult(ExpenseApproveRequest.ApprovalResult.APPROVED);
        request.setApprover("manager");
        request.setComment("通过");

        assertThatThrownBy(() -> service.approveExpense(1L, request))
                .isInstanceOf(AccessDeniedException.class);

        verify(approvalRecordRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private ExpenseCommandService newService() {
        return new ExpenseCommandService(expenseRepository, approvalRecordRepository, accessGuard);
    }

    private Expense expense(Long id, Long projectId) {
        return Expense.builder()
                .id(id)
                .projectId(projectId)
                .category(Expense.ExpenseCategory.OTHER)
                .expenseType("保证金")
                .amount(new BigDecimal("100.00"))
                .date(LocalDate.now().minusDays(1))
                .createdBy("creator")
                .status(Expense.ExpenseStatus.PENDING_APPROVAL)
                .build();
    }
}
