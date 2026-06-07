# Expense Ledger 子域

> 一旦我所属的文件夹有所变化，请更新我。

## 职责
费用台账子域负责把 `expenses` 中的费用记录编排成“可筛选的台账明细 + 可直接展示的聚合统计”。首版部门口径按项目经理所属部门落地，并保持审批与退还流程仍由 `resources.service.ExpenseService` 主导。

## 边界清单
| 文件 | 地位 | 功能 |
|------|------|------|
| `application/ExpenseLedgerApplicationService.java` | Application | 查询编排、项目/项目经理映射、筛选校验 |
| `domain/ExpenseLedgerStatisticsCalculator.java` | Domain | 纯统计聚合与分组计算 |
| `dto/ExpenseLedgerQuery.java` | DTO | 台账查询条件 |
| `dto/ExpenseLedgerItemDTO.java` | DTO | 台账明细行 |
| `dto/ExpenseLedgerSummaryDTO.java` | DTO | 汇总统计 |
| `dto/ExpenseLedgerGroupSummaryDTO.java` | DTO | 维度分组统计 |
| `dto/ExpenseLedgerResponse.java` | DTO | 明细 + 汇总响应包装 |
