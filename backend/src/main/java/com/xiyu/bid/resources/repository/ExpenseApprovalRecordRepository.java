package com.xiyu.bid.resources.repository;

import com.xiyu.bid.resources.entity.ExpenseApprovalRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExpenseApprovalRecordRepository extends JpaRepository<ExpenseApprovalRecord, Long> {

    List<ExpenseApprovalRecord> findByExpenseIdOrderByActedAtDesc(Long expenseId);

    List<ExpenseApprovalRecord> findByApproverOrderByActedAtDesc(String approver);
}
