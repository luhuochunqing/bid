package com.xiyu.bid.resources.service.expense;

import com.xiyu.bid.resources.dto.ExpenseDTO;
import com.xiyu.bid.resources.dto.ExpensePaymentCreateRequest;
import com.xiyu.bid.resources.dto.ExpensePaymentRecordDTO;
import com.xiyu.bid.resources.dto.ResourceResponseMapper;
import com.xiyu.bid.resources.entity.Expense;
import com.xiyu.bid.resources.entity.ExpensePaymentRecord;
import com.xiyu.bid.resources.repository.ExpensePaymentRecordRepository;
import com.xiyu.bid.resources.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExpensePaymentService {

    private final ExpenseRepository expenseRepository;
    private final ExpensePaymentRecordRepository expensePaymentRecordRepository;
    private final ExpenseCommandService expenseCommandService;

    @Transactional
    public ExpenseDTO registerPayment(Long expenseId, ExpensePaymentCreateRequest request) {
        validatePaymentRequest(request);

        Expense expense = expenseCommandService.getExpenseEntityById(expenseId);
        expense.markPaid();

        ExpensePaymentRecord paymentRecord = expensePaymentRecordRepository.save(ExpensePaymentRecord.builder()
                .expenseId(expense.getId())
                .amount(request.getAmount())
                .paidAt(request.getPaidAt())
                .paidBy(request.getPaidBy())
                .paymentReference(request.getPaymentReference())
                .paymentMethod(request.getPaymentMethod())
                .remark(request.getRemark())
                .build());

        return ResourceResponseMapper.toDto(expenseRepository.save(expense), paymentRecord);
    }

    public List<ExpensePaymentRecordDTO> getPaymentRecords(Long expenseId) {
        expenseCommandService.getExpenseEntityById(expenseId);
        return expensePaymentRecordRepository.findByExpenseIdOrderByPaidAtDescIdDesc(expenseId).stream()
                .map(ResourceResponseMapper::toDto)
                .toList();
    }

    private void validatePaymentRequest(ExpensePaymentCreateRequest request) {
        if (request.getPaidAt() != null && request.getPaidAt().isAfter(LocalDateTime.now().plusMinutes(1))) {
            throw new IllegalArgumentException("Paid at cannot be in the future");
        }
    }
}
