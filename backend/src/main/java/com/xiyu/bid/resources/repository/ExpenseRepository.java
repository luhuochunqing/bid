package com.xiyu.bid.resources.repository;

import com.xiyu.bid.resources.entity.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    Page<Expense> findByProjectId(Long projectId, Pageable pageable);

    Page<Expense> findByProjectIdIn(List<Long> projectIds, Pageable pageable);

    List<Expense> findByProjectIdIn(List<Long> projectIds, org.springframework.data.domain.Sort sort);

    Page<Expense> findByCategory(Expense.ExpenseCategory category, Pageable pageable);

    Page<Expense> findByProjectIdInAndCategory(List<Long> projectIds, Expense.ExpenseCategory category, Pageable pageable);

    Page<Expense> findByDateBetween(LocalDate startDate, LocalDate endDate, Pageable pageable);

    Page<Expense> findByProjectIdInAndDateBetween(List<Long> projectIds, LocalDate startDate, LocalDate endDate, Pageable pageable);

    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.projectId = :projectId")
    BigDecimal sumAmountByProjectId(@org.springframework.data.repository.query.Param("projectId") Long projectId);

    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.projectId = :projectId AND e.category = :category")
    BigDecimal sumAmountByProjectIdAndCategory(
            @org.springframework.data.repository.query.Param("projectId") Long projectId,
            @org.springframework.data.repository.query.Param("category") Expense.ExpenseCategory category
    );

    Page<Expense> findByProjectIdAndCategory(Long projectId, Expense.ExpenseCategory category, Pageable pageable);

    Page<Expense> findByProjectIdAndDateBetween(Long projectId, LocalDate startDate, LocalDate endDate, Pageable pageable);

    List<Expense> findByProjectIdOrderByCreatedAtDesc(Long projectId);

    List<Expense> findByProjectIdInOrderByCreatedAtDesc(List<Long> projectIds);

    List<Expense> findByExpenseTypeAndExpectedReturnDateIsNotNullAndStatusNotOrderByExpectedReturnDateAsc(
            String expenseType,
            Expense.ExpenseStatus status);
}
