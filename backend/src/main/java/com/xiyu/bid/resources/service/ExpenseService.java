// Input: resources expense application services
// Output: Expense business facade for controller coordination
// Pos: Service/业务层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.resources.service;

import com.xiyu.bid.resources.dto.ExpenseApproveRequest;
import com.xiyu.bid.resources.dto.ExpenseApprovalRecordDTO;
import com.xiyu.bid.resources.dto.ExpenseCreateRequest;
import com.xiyu.bid.resources.dto.ExpenseDTO;
import com.xiyu.bid.resources.dto.ExpensePaymentCreateRequest;
import com.xiyu.bid.resources.dto.ExpensePaymentRecordDTO;
import com.xiyu.bid.resources.dto.ExpenseReturnActionRequest;
import com.xiyu.bid.resources.dto.ExpenseUpdateRequest;
import com.xiyu.bid.resources.service.expense.ExpenseCommandService;
import com.xiyu.bid.resources.service.expense.ExpensePaymentService;
import com.xiyu.bid.resources.service.expense.ExpenseQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExpenseService {

    private final ExpenseCommandService expenseCommandService;
    private final ExpenseQueryService expenseQueryService;
    private final ExpensePaymentService expensePaymentService;

    public ExpenseDTO createExpense(ExpenseCreateRequest request) {
        return expenseCommandService.createExpense(request);
    }

    public ExpenseDTO getExpenseById(Long id) {
        return expenseQueryService.getExpenseById(id);
    }

    public Page<ExpenseDTO> getAllExpenses(Pageable pageable) {
        return expenseQueryService.getAllExpenses(pageable);
    }

    public Page<ExpenseDTO> getExpensesByProjectId(Long projectId, Pageable pageable) {
        return expenseQueryService.getExpensesByProjectId(projectId, pageable);
    }

    public Page<ExpenseDTO> getExpensesByCategory(String category, Pageable pageable) {
        return expenseQueryService.getExpensesByCategory(category, pageable);
    }

    public Page<ExpenseDTO> getExpensesByDateRange(LocalDate startDate, LocalDate endDate, Pageable pageable) {
        return expenseQueryService.getExpensesByDateRange(startDate, endDate, pageable);
    }

    public BigDecimal getTotalExpenseByProject(Long projectId) {
        return expenseQueryService.getTotalExpenseByProject(projectId);
    }

    public Map<String, Object> getExpenseStatisticsByProject(Long projectId) {
        return expenseQueryService.getExpenseStatisticsByProject(projectId);
    }

    public List<ExpenseApprovalRecordDTO> getApprovalRecords(Long projectId) {
        return expenseQueryService.getApprovalRecords(projectId);
    }

    public ExpenseDTO updateExpense(Long id, ExpenseUpdateRequest request) {
        return expenseCommandService.updateExpense(id, request);
    }

    public void deleteExpense(Long id) {
        expenseCommandService.deleteExpense(id);
    }

    public ExpenseDTO approveExpense(Long id, ExpenseApproveRequest request) {
        return expenseCommandService.approveExpense(id, request);
    }

    public ExpenseDTO requestReturn(Long id, ExpenseReturnActionRequest request) {
        return expenseCommandService.requestReturn(id, request);
    }

    public ExpenseDTO confirmReturn(Long id, ExpenseReturnActionRequest request) {
        return expenseCommandService.confirmReturn(id, request);
    }

    public ExpenseDTO registerPayment(Long id, ExpensePaymentCreateRequest request) {
        return expensePaymentService.registerPayment(id, request);
    }

    public List<ExpensePaymentRecordDTO> getPaymentRecords(Long id) {
        return expensePaymentService.getPaymentRecords(id);
    }
}
