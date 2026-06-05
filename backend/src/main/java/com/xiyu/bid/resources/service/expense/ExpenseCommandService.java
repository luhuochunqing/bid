package com.xiyu.bid.resources.service.expense;

import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.resources.dto.ExpenseApproveRequest;
import com.xiyu.bid.resources.dto.ExpenseCreateRequest;
import com.xiyu.bid.resources.dto.ExpenseDTO;
import com.xiyu.bid.resources.dto.ExpenseReturnActionRequest;
import com.xiyu.bid.resources.dto.ExpenseUpdateRequest;
import com.xiyu.bid.resources.dto.ResourceResponseMapper;
import com.xiyu.bid.resources.entity.Expense;
import com.xiyu.bid.resources.entity.ExpenseApprovalRecord;
import com.xiyu.bid.resources.repository.ExpenseApprovalRecordRepository;
import com.xiyu.bid.resources.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ExpenseCommandService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseApprovalRecordRepository expenseApprovalRecordRepository;
    private final ExpenseAccessGuard accessGuard;

    @Transactional
    public ExpenseDTO createExpense(ExpenseCreateRequest request) {
        validateCreateRequest(request);
        accessGuard.assertCanAccessProject(request.getProjectId());

        Expense expense = Expense.builder()
                .projectId(request.getProjectId())
                .category(request.getCategory())
                .expenseType(request.getExpenseType())
                .amount(request.getAmount())
                .date(request.getDate())
                .expectedReturnDate(request.getExpectedReturnDate())
                .description(request.getDescription())
                .createdBy(request.getCreatedBy())
                .status(Expense.ExpenseStatus.PENDING_APPROVAL)
                .build();

        return ResourceResponseMapper.toDto(expenseRepository.save(expense));
    }

    @Transactional
    public ExpenseDTO updateExpense(Long id, ExpenseUpdateRequest request) {
        Expense expense = getExpenseEntityById(id);
        expense.updateDetails(request.getCategory(), request.getAmount(), request.getDate(),
                request.getExpenseType(), request.getDescription());
        if (request.getExpectedReturnDate() != null) {
            expense.updateExpectedReturnDate(request.getExpectedReturnDate());
        }
        return ResourceResponseMapper.toDto(expenseRepository.save(expense));
    }

    @Transactional
    public void deleteExpense(Long id) {
        Expense expense = getExpenseEntityById(id);
        expenseRepository.delete(expense);
    }

    @Transactional
    public ExpenseDTO approveExpense(Long id, ExpenseApproveRequest request) {
        Expense expense = getExpenseEntityById(id);
        Expense.ExpenseStatus nextStatus = request.getResult() == ExpenseApproveRequest.ApprovalResult.APPROVED
                ? Expense.ExpenseStatus.APPROVED
                : Expense.ExpenseStatus.REJECTED;
        expense.markApproved(request.getApprover(), request.getComment(), nextStatus);

        Expense saved = expenseRepository.save(expense);
        expenseApprovalRecordRepository.save(ExpenseApprovalRecord.builder()
                .expenseId(saved.getId())
                .result(request.getResult() == ExpenseApproveRequest.ApprovalResult.APPROVED
                        ? ExpenseApprovalRecord.ApprovalResult.APPROVED
                        : ExpenseApprovalRecord.ApprovalResult.REJECTED)
                .comment(request.getComment())
                .approver(request.getApprover())
                .actedAt(LocalDateTime.now())
                .build());

        return ResourceResponseMapper.toDto(saved);
    }

    @Transactional
    public ExpenseDTO requestReturn(Long id, ExpenseReturnActionRequest request) {
        Expense expense = getExpenseEntityById(id);
        expense.requestReturn(request.getActor(), request.getComment());
        return ResourceResponseMapper.toDto(expenseRepository.save(expense));
    }

    @Transactional
    public ExpenseDTO confirmReturn(Long id, ExpenseReturnActionRequest request) {
        Expense expense = getExpenseEntityById(id);
        expense.confirmReturn(request.getActor(), request.getComment());
        return ResourceResponseMapper.toDto(expenseRepository.save(expense));
    }

    public Expense getExpenseEntityById(Long id) {
        Expense expense = expenseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Expense", String.valueOf(id)));
        accessGuard.assertCanAccessProject(expense.getProjectId());
        return expense;
    }

    private void validateCreateRequest(ExpenseCreateRequest request) {
        if (request.getProjectId() == null) {
            throw new IllegalArgumentException("Project ID is required");
        }
        if (request.getCategory() == null) {
            throw new IllegalArgumentException("Category is required");
        }
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (request.getDate() == null) {
            throw new IllegalArgumentException("Date is required");
        }
        if (request.getDate().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Date cannot be in the future");
        }
        if (request.getCreatedBy() == null || request.getCreatedBy().trim().isEmpty()) {
            throw new IllegalArgumentException("Created by is required");
        }
    }
}
