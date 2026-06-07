package com.xiyu.bid.resources.expenseledger.domain;

import com.xiyu.bid.resources.dto.ExpenseDTO;
import com.xiyu.bid.resources.expenseledger.dto.ExpenseLedgerGroupSummaryDTO;
import com.xiyu.bid.resources.expenseledger.dto.ExpenseLedgerItemDTO;
import com.xiyu.bid.resources.expenseledger.dto.ExpenseLedgerSummaryDTO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ExpenseLedgerStatisticsCalculator {

    public ExpenseLedgerSummaryDTO summarize(List<ExpenseLedgerItemDTO> items) {
        return ExpenseLedgerSummaryDTO.builder()
                .recordCount(items.size())
                .totalAmount(sum(items, item -> true))
                .pendingApprovalAmount(sum(items, item -> item.getStatus() == ExpenseDTO.ExpenseStatus.PENDING_APPROVAL))
                .approvedAmount(sum(items, item -> item.getStatus() == ExpenseDTO.ExpenseStatus.APPROVED))
                .paidAmount(sum(items, item -> item.getStatus() == ExpenseDTO.ExpenseStatus.PAID))
                .returnRequestedAmount(sum(items, item -> item.getStatus() == ExpenseDTO.ExpenseStatus.RETURN_REQUESTED))
                .returnedAmount(sum(items, item -> item.getStatus() == ExpenseDTO.ExpenseStatus.RETURNED))
                .depositCount(items.stream().filter(this::isDeposit).count())
                .pendingReturnCount(items.stream().filter(this::isPendingReturnDeposit).count())
                .byDepartment(group(items,
                        item -> defaultKey(item.getDepartmentCode()),
                        item -> defaultLabel(item.getDepartmentName())))
                .byProject(group(items,
                        item -> defaultKey(item.getProjectId() == null ? null : String.valueOf(item.getProjectId())),
                        item -> defaultLabel(item.getProjectName())))
                .byExpenseType(group(items,
                        item -> defaultKey(item.getExpenseType()),
                        item -> defaultLabel(item.getExpenseType())))
                .byStatus(group(items,
                        item -> defaultKey(item.getStatus() == null ? null : item.getStatus().name()),
                        item -> defaultLabel(item.getStatus() == null ? null : item.getStatus().name())))
                .build();
    }

    private BigDecimal sum(List<ExpenseLedgerItemDTO> items, java.util.function.Predicate<ExpenseLedgerItemDTO> predicate) {
        return items.stream()
                .filter(predicate)
                .map(ExpenseLedgerItemDTO::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<ExpenseLedgerGroupSummaryDTO> group(
            List<ExpenseLedgerItemDTO> items,
            Function<ExpenseLedgerItemDTO, String> keySelector,
            Function<ExpenseLedgerItemDTO, String> labelSelector) {

        Map<String, List<ExpenseLedgerItemDTO>> grouped = items.stream()
                .collect(Collectors.groupingBy(keySelector));

        return grouped.entrySet().stream()
                .map(entry -> ExpenseLedgerGroupSummaryDTO.builder()
                        .key(entry.getKey())
                        .label(defaultLabel(labelSelector.apply(entry.getValue().get(0))))
                        .count(entry.getValue().size())
                        .totalAmount(entry.getValue().stream()
                                .map(ExpenseLedgerItemDTO::getAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add))
                        .build())
                .sorted(Comparator.comparing(ExpenseLedgerGroupSummaryDTO::getTotalAmount).reversed())
                .toList();
    }

    private boolean isDeposit(ExpenseLedgerItemDTO item) {
        return "保证金".equals(item.getExpenseType());
    }

    private boolean isPendingReturnDeposit(ExpenseLedgerItemDTO item) {
        return item.getStatus() == ExpenseDTO.ExpenseStatus.PAID
                || item.getStatus() == ExpenseDTO.ExpenseStatus.RETURN_REQUESTED;
    }

    private String defaultKey(String value) {
        return value == null || value.isBlank() ? "UNASSIGNED" : value;
    }

    private String defaultLabel(String value) {
        return value == null || value.isBlank() ? "未分配部门" : value;
    }
}
