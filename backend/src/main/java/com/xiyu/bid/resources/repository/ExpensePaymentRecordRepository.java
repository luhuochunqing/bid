package com.xiyu.bid.resources.repository;

import com.xiyu.bid.resources.entity.ExpensePaymentRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExpensePaymentRecordRepository extends JpaRepository<ExpensePaymentRecord, Long> {

    List<ExpensePaymentRecord> findByExpenseIdOrderByPaidAtDescIdDesc(Long expenseId);

    List<ExpensePaymentRecord> findByExpenseIdInOrderByExpenseIdAscPaidAtDescIdDesc(List<Long> expenseIds);
}
