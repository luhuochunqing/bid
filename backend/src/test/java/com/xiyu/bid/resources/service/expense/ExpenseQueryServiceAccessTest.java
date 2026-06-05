package com.xiyu.bid.resources.service.expense;

import com.xiyu.bid.resources.entity.Expense;
import com.xiyu.bid.resources.entity.ExpenseApprovalRecord;
import com.xiyu.bid.resources.repository.ExpenseApprovalRecordRepository;
import com.xiyu.bid.resources.repository.ExpensePaymentRecordRepository;
import com.xiyu.bid.resources.repository.ExpenseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpenseQueryServiceAccessTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private ExpenseApprovalRecordRepository approvalRecordRepository;

    @Mock
    private ExpensePaymentRecordRepository paymentRecordRepository;

    @Mock
    private ExpenseAccessGuard accessGuard;

    @Test
    void getExpenseById_ShouldRejectInvisibleProjectExpenseBeforeLoadingPayments() {
        ExpenseQueryService service = newService();
        Expense expense = expense(1L, 99L);
        when(expenseRepository.findById(1L)).thenReturn(Optional.of(expense));
        org.mockito.Mockito.doThrow(new AccessDeniedException("权限不足"))
                .when(accessGuard).assertCanAccessProject(99L);

        assertThatThrownBy(() -> service.getExpenseById(1L))
                .isInstanceOf(AccessDeniedException.class);

        verify(paymentRecordRepository, never()).findByExpenseIdOrderByPaidAtDescIdDesc(1L);
    }

    @Test
    void getAllExpenses_ShouldFilterByVisibleProjectsForNonAdmin() {
        ExpenseQueryService service = newService();
        PageRequest pageable = PageRequest.of(0, 10);
        when(accessGuard.visibleProjectIdsForCurrentUser()).thenReturn(List.of(10L));
        when(expenseRepository.findByProjectIdIn(List.of(10L), pageable))
                .thenReturn(new PageImpl<>(List.of(expense(2L, 10L)), pageable, 1));

        assertThat(service.getAllExpenses(pageable).getContent())
                .extracting("projectId")
                .containsExactly(10L);
    }

    @Test
    void getExpensesByCategory_ShouldFilterByVisibleProjectsForNonAdmin() {
        ExpenseQueryService service = newService();
        PageRequest pageable = PageRequest.of(0, 10);
        when(accessGuard.visibleProjectIdsForCurrentUser()).thenReturn(List.of(10L));
        when(expenseRepository.findByProjectIdInAndCategory(List.of(10L), Expense.ExpenseCategory.OTHER, pageable))
                .thenReturn(new PageImpl<>(List.of(expense(2L, 10L)), pageable, 1));

        assertThat(service.getExpensesByCategory("OTHER", pageable).getContent())
                .extracting("projectId")
                .containsExactly(10L);

        verify(expenseRepository, never()).findByCategory(Expense.ExpenseCategory.OTHER, pageable);
    }

    @Test
    void getExpensesByDateRange_ShouldFilterByVisibleProjectsForNonAdmin() {
        ExpenseQueryService service = newService();
        PageRequest pageable = PageRequest.of(0, 10);
        LocalDate startDate = LocalDate.now().minusDays(7);
        LocalDate endDate = LocalDate.now();
        when(accessGuard.visibleProjectIdsForCurrentUser()).thenReturn(List.of(10L));
        when(expenseRepository.findByProjectIdInAndDateBetween(List.of(10L), startDate, endDate, pageable))
                .thenReturn(new PageImpl<>(List.of(expense(2L, 10L)), pageable, 1));

        assertThat(service.getExpensesByDateRange(startDate, endDate, pageable).getContent())
                .extracting("projectId")
                .containsExactly(10L);

        verify(expenseRepository, never()).findByDateBetween(startDate, endDate, pageable);
    }

    @Test
    void getApprovalRecords_ShouldFilterAllHistoryByVisibleProjectsForNonAdmin() {
        ExpenseQueryService service = newService();
        Expense visibleExpense = expense(3L, 10L);
        when(accessGuard.visibleProjectIdsForCurrentUser()).thenReturn(List.of(10L));
        when(expenseRepository.findByProjectIdInOrderByCreatedAtDesc(List.of(10L)))
                .thenReturn(List.of(visibleExpense));
        when(approvalRecordRepository.findByExpenseIdOrderByActedAtDesc(3L))
                .thenReturn(List.of(approvalRecord(3L)));

        assertThat(service.getApprovalRecords(null))
                .extracting("expenseId")
                .containsExactly(3L);
    }

    @Test
    void getApprovalRecords_ShouldRejectInvisibleProjectBeforeLoadingHistory() {
        ExpenseQueryService service = newService();
        org.mockito.Mockito.doThrow(new AccessDeniedException("权限不足"))
                .when(accessGuard).assertCanAccessProject(99L);

        assertThatThrownBy(() -> service.getApprovalRecords(99L))
                .isInstanceOf(AccessDeniedException.class);

        verify(expenseRepository, never()).findByProjectIdOrderByCreatedAtDesc(99L);
    }

    @Test
    void getApprovalRecords_ShouldReturnEmptyWhenUserHasNoVisibleProjects() {
        ExpenseQueryService service = newService();
        when(accessGuard.visibleProjectIdsForCurrentUser()).thenReturn(List.of());

        assertThat(service.getApprovalRecords(null)).isEmpty();

        verify(approvalRecordRepository, never()).findAll();
    }

    @Test
    void getExpenseStatisticsByProject_ShouldRejectInvisibleProject() {
        ExpenseQueryService service = newService();
        org.mockito.Mockito.doThrow(new AccessDeniedException("权限不足"))
                .when(accessGuard).assertCanAccessProject(99L);

        assertThatThrownBy(() -> service.getExpenseStatisticsByProject(99L))
                .isInstanceOf(AccessDeniedException.class);

        verify(expenseRepository, never()).sumAmountByProjectId(99L);
    }

    private ExpenseQueryService newService() {
        return new ExpenseQueryService(
                expenseRepository,
                approvalRecordRepository,
                paymentRecordRepository,
                accessGuard
        );
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

    private ExpenseApprovalRecord approvalRecord(Long expenseId) {
        return ExpenseApprovalRecord.builder()
                .expenseId(expenseId)
                .result(ExpenseApprovalRecord.ApprovalResult.APPROVED)
                .approver("manager")
                .comment("通过")
                .actedAt(LocalDateTime.now())
                .build();
    }
}
