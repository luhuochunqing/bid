package com.xiyu.bid.resources.expenseledger.application;

import com.xiyu.bid.resources.entity.Expense;
import com.xiyu.bid.resources.expenseledger.domain.ExpenseLedgerStatisticsCalculator;
import com.xiyu.bid.resources.expenseledger.dto.ExpenseLedgerQuery;
import com.xiyu.bid.resources.repository.ExpenseRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.resources.service.expense.ExpenseAccessGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpenseLedgerApplicationServiceAccessTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ExpenseAccessGuard accessGuard;

    @Test
    void queryLedger_ShouldRejectExplicitInvisibleProjectBeforeQueryingExpenses() {
        ExpenseLedgerApplicationService service = newService();
        org.mockito.Mockito.doThrow(new AccessDeniedException("权限不足"))
                .when(accessGuard).assertCanAccessProject(99L);

        assertThatThrownBy(() -> service.queryLedger(ExpenseLedgerQuery.builder().projectId(99L).build()))
                .isInstanceOf(AccessDeniedException.class);

        verify(expenseRepository, never()).findAll(org.mockito.ArgumentMatchers.any(Sort.class));
    }

    @Test
    void queryLedger_ShouldFilterExpensesByVisibleProjectsForNonAdmin() {
        ExpenseLedgerApplicationService service = newService();
        when(accessGuard.visibleProjectIdsForCurrentUser()).thenReturn(List.of(10L));
        when(expenseRepository.findByProjectIdIn(
                org.mockito.ArgumentMatchers.eq(List.of(10L)),
                org.mockito.ArgumentMatchers.any(Sort.class)))
                .thenReturn(List.of(expense(1L, 10L)));
        when(projectRepository.findAllById(org.mockito.ArgumentMatchers.<Iterable<Long>>any())).thenReturn(List.of());

        assertThat(service.queryLedger(ExpenseLedgerQuery.builder().build()).getItems())
                .extracting("projectId")
                .containsExactly(10L);
    }

    private ExpenseLedgerApplicationService newService() {
        return new ExpenseLedgerApplicationService(
                expenseRepository,
                projectRepository,
                userRepository,
                new ExpenseLedgerStatisticsCalculator(),
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
}
