// Input: resources facade service, ledger app service, reminder app service, and request DTOs
// Output: Expense CRUD, approval/return flow, payment tracking, and ledger statistics REST API endpoints
// Pos: Controller/控制器层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.resources.controller;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.config.PaginationConstants;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.resources.application.service.SendExpenseReturnReminderAppService;
import com.xiyu.bid.resources.dto.ExpenseApproveRequest;
import com.xiyu.bid.resources.dto.ExpenseApprovalRecordDTO;
import com.xiyu.bid.resources.dto.ExpenseCreateRequest;
import com.xiyu.bid.resources.dto.ExpenseDTO;
import com.xiyu.bid.resources.dto.ExpensePaymentCreateRequest;
import com.xiyu.bid.resources.dto.ExpensePaymentRecordDTO;
import com.xiyu.bid.resources.dto.ExpenseReturnActionRequest;
import com.xiyu.bid.resources.dto.ExpenseUpdateRequest;
import com.xiyu.bid.resources.expenseledger.application.ExpenseLedgerApplicationService;
import com.xiyu.bid.resources.expenseledger.dto.ExpenseLedgerQuery;
import com.xiyu.bid.resources.expenseledger.dto.ExpenseLedgerResponse;
import com.xiyu.bid.resources.service.ExpenseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/resources/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;
    private final ExpenseLedgerApplicationService expenseLedgerApplicationService;
    private final SendExpenseReturnReminderAppService sendExpenseReturnReminderAppService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Auditable(action = "CREATE", entityType = "Expense", description = "Create expense record")
    public ResponseEntity<ApiResponse<ExpenseDTO>> createExpense(@Valid @RequestBody ExpenseCreateRequest request) {
        ExpenseDTO expense = expenseService.createExpense(request);
        return ResponseEntity.ok(ApiResponse.success("Expense created successfully", expense));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<ExpenseDTO>> getExpenseById(@PathVariable Long id) {
        ExpenseDTO expense = expenseService.getExpenseById(id);
        return ResponseEntity.ok(ApiResponse.success(expense));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<Page<ExpenseDTO>>> getAllExpenses(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        if (size > PaginationConstants.MAX_PAGE_SIZE) {
            size = PaginationConstants.MAX_PAGE_SIZE;
        }

        Sort sort = sortDir.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<ExpenseDTO> expenses = expenseService.getAllExpenses(pageable);
        return ResponseEntity.ok(ApiResponse.success(expenses));
    }

    @GetMapping("/ledger")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<ExpenseLedgerResponse>> getExpenseLedger(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String projectKeyword,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String expenseType,
            @RequestParam(required = false) ExpenseDTO.ExpenseStatus status) {

        ExpenseLedgerQuery query = ExpenseLedgerQuery.builder()
                .projectId(projectId)
                .projectKeyword(projectKeyword)
                .startDate(startDate)
                .endDate(endDate)
                .department(department)
                .expenseType(expenseType)
                .status(status)
                .build();

        ExpenseLedgerResponse response = expenseLedgerApplicationService.queryLedger(query);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/project/{projectId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<Page<ExpenseDTO>>> getExpensesByProjectId(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        if (size > PaginationConstants.MAX_PAGE_SIZE) {
            size = PaginationConstants.MAX_PAGE_SIZE;
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("date").descending());
        Page<ExpenseDTO> expenses = expenseService.getExpensesByProjectId(projectId, pageable);
        return ResponseEntity.ok(ApiResponse.success(expenses));
    }

    @GetMapping("/category/{category}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<Page<ExpenseDTO>>> getExpensesByCategory(
            @PathVariable String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        if (size > PaginationConstants.MAX_PAGE_SIZE) {
            size = PaginationConstants.MAX_PAGE_SIZE;
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("date").descending());
        Page<ExpenseDTO> expenses = expenseService.getExpensesByCategory(category, pageable);
        return ResponseEntity.ok(ApiResponse.success(expenses));
    }

    @GetMapping("/date-range")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<Page<ExpenseDTO>>> getExpensesByDateRange(
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        if (size > PaginationConstants.MAX_PAGE_SIZE) {
            size = PaginationConstants.MAX_PAGE_SIZE;
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("date").descending());
        Page<ExpenseDTO> expenses = expenseService.getExpensesByDateRange(startDate, endDate, pageable);
        return ResponseEntity.ok(ApiResponse.success(expenses));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Auditable(action = "UPDATE", entityType = "Expense", description = "Update expense record")
    public ResponseEntity<ApiResponse<ExpenseDTO>> updateExpense(
            @PathVariable Long id,
            @Valid @RequestBody ExpenseUpdateRequest request) {

        ExpenseDTO expense = expenseService.updateExpense(id, request);
        return ResponseEntity.ok(ApiResponse.success("Expense updated successfully", expense));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Auditable(action = "DELETE", entityType = "Expense", description = "Delete expense record")
    public ResponseEntity<ApiResponse<Void>> deleteExpense(@PathVariable Long id) {
        expenseService.deleteExpense(id);
        return ResponseEntity.ok(ApiResponse.success("Expense deleted successfully", null));
    }

    @GetMapping("/project/{projectId}/total")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<BigDecimal>> getTotalExpenseByProject(@PathVariable Long projectId) {
        BigDecimal total = expenseService.getTotalExpenseByProject(projectId);
        return ResponseEntity.ok(ApiResponse.success(total));
    }

    @GetMapping("/project/{projectId}/statistics")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getExpenseStatistics(@PathVariable Long projectId) {
        Map<String, Object> statistics = expenseService.getExpenseStatisticsByProject(projectId);
        return ResponseEntity.ok(ApiResponse.success(statistics));
    }

    @GetMapping("/approval-records")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<ExpenseApprovalRecordDTO>>> getApprovalRecords(
            @RequestParam(required = false) Long projectId) {
        List<ExpenseApprovalRecordDTO> records = expenseService.getApprovalRecords(projectId);
        return ResponseEntity.ok(ApiResponse.success(records));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Auditable(action = "APPROVE", entityType = "Expense", description = "Approve or reject expense")
    public ResponseEntity<ApiResponse<ExpenseDTO>> approveExpense(
            @PathVariable Long id,
            @Valid @RequestBody ExpenseApproveRequest request) {
        ExpenseDTO expense = expenseService.approveExpense(id, request);
        return ResponseEntity.ok(ApiResponse.success("Expense approval action completed", expense));
    }

    @PostMapping("/{id}/return-request")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Auditable(action = "RETURN_REQUEST", entityType = "Expense", description = "Request expense return")
    public ResponseEntity<ApiResponse<ExpenseDTO>> requestReturn(
            @PathVariable Long id,
            @Valid @RequestBody ExpenseReturnActionRequest request) {
        ExpenseDTO expense = expenseService.requestReturn(id, request);
        return ResponseEntity.ok(ApiResponse.success("Expense return requested", expense));
    }

    @PostMapping("/{id}/confirm-return")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Auditable(action = "CONFIRM_RETURN", entityType = "Expense", description = "Confirm expense return")
    public ResponseEntity<ApiResponse<ExpenseDTO>> confirmReturn(
            @PathVariable Long id,
            @Valid @RequestBody ExpenseReturnActionRequest request) {
        ExpenseDTO expense = expenseService.confirmReturn(id, request);
        return ResponseEntity.ok(ApiResponse.success("Expense return confirmed", expense));
    }

    @PostMapping("/{id}/payments")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Auditable(action = "PAY", entityType = "Expense", description = "Register expense payment")
    public ResponseEntity<ApiResponse<ExpenseDTO>> registerPayment(
            @PathVariable Long id,
            @Valid @RequestBody ExpensePaymentCreateRequest request) {
        ExpenseDTO expense = expenseService.registerPayment(id, request);
        return ResponseEntity.ok(ApiResponse.success("Expense payment registered", expense));
    }

    @GetMapping("/{id}/payments")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<ExpensePaymentRecordDTO>>> getPaymentRecords(@PathVariable Long id) {
        List<ExpensePaymentRecordDTO> records = expenseService.getPaymentRecords(id);
        return ResponseEntity.ok(ApiResponse.success(records));
    }

    @PostMapping("/{id}/return-reminder")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Auditable(action = "SEND_RETURN_REMINDER", entityType = "Expense", description = "Send expense return reminder")
    public ResponseEntity<ApiResponse<ExpenseDTO>> sendReturnReminder(
            @PathVariable Long id,
            @Valid @RequestBody ExpenseReturnActionRequest request) {
        ExpenseDTO expense = sendExpenseReturnReminderAppService.send(id, request.getActor(), request.getComment());
        return ResponseEntity.ok(ApiResponse.success("Expense return reminder sent", expense));
    }
}
