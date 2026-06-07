package com.xiyu.bid.resources.expenseledger.domain;

import com.xiyu.bid.resources.dto.ExpenseDTO;
import com.xiyu.bid.resources.expenseledger.dto.ExpenseLedgerItemDTO;
import com.xiyu.bid.resources.expenseledger.dto.ExpenseLedgerSummaryDTO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExpenseLedgerStatisticsCalculatorTest {

    private final ExpenseLedgerStatisticsCalculator calculator = new ExpenseLedgerStatisticsCalculator();

    @Test
    void summarize_ShouldReturnAmountsAndGroupsForMultipleDimensions() {
        ExpenseLedgerSummaryDTO summary = calculator.summarize(List.of(
                item(1L, 101L, "华北电网项目", "D-NORTH", "华北事业部", "保证金",
                        new BigDecimal("120000.00"), ExpenseDTO.ExpenseStatus.PENDING_APPROVAL),
                item(2L, 101L, "华北电网项目", "D-NORTH", "华北事业部", "差旅费",
                        new BigDecimal("5600.00"), ExpenseDTO.ExpenseStatus.RETURNED),
                item(3L, 202L, "华南轨交项目", "D-SOUTH", "华南事业部", "人工费",
                        new BigDecimal("8800.00"), ExpenseDTO.ExpenseStatus.PAID),
                item(4L, 202L, "华南轨交项目", "D-SOUTH", "华南事业部", "材料费",
                        new BigDecimal("3000.00"), ExpenseDTO.ExpenseStatus.RETURN_REQUESTED)
        ));

        assertThat(summary.getRecordCount()).isEqualTo(4);
        assertThat(summary.getTotalAmount()).isEqualByComparingTo("137400.00");
        assertThat(summary.getPendingApprovalAmount()).isEqualByComparingTo("120000.00");
        assertThat(summary.getPaidAmount()).isEqualByComparingTo("8800.00");
        assertThat(summary.getReturnRequestedAmount()).isEqualByComparingTo("3000.00");
        assertThat(summary.getReturnedAmount()).isEqualByComparingTo("5600.00");
        assertThat(summary.getDepositCount()).isEqualTo(1);
        assertThat(summary.getPendingReturnCount()).isEqualTo(2);
        assertThat(summary.getByDepartment()).extracting("key")
                .containsExactly("D-NORTH", "D-SOUTH");
        assertThat(summary.getByProject()).extracting("key")
                .containsExactly("101", "202");
        assertThat(summary.getByExpenseType()).extracting("key")
                .containsExactly("保证金", "人工费", "差旅费", "材料费");
        assertThat(summary.getByStatus()).extracting("key")
                .containsExactly("PENDING_APPROVAL", "PAID", "RETURNED", "RETURN_REQUESTED");
    }

    private ExpenseLedgerItemDTO item(
            Long id,
            Long projectId,
            String projectName,
            String departmentCode,
            String departmentName,
            String expenseType,
            BigDecimal amount,
            ExpenseDTO.ExpenseStatus status) {
        return ExpenseLedgerItemDTO.builder()
                .id(id)
                .projectId(projectId)
                .projectName(projectName)
                .departmentCode(departmentCode)
                .departmentName(departmentName)
                .category(ExpenseDTO.ExpenseCategory.OTHER)
                .expenseType(expenseType)
                .amount(amount)
                .date(LocalDate.now())
                .description("desc")
                .createdBy("tester")
                .status(status)
                .build();
    }
}
