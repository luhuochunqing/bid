# Compliance 模块 (合规检查模块)

> 一旦我所属的文件夹有所变化，请更新我。

## 职责
合规检查模块负责项目和标讯的规则检查、检查结果记录以及风险评估输出。这里负责把合规规则、检查结果和对外 API 边界收拢到统一位置。

## 边界清单
| 文件 | 地位 | 功能 |
|------|------|------|
| `controller/ComplianceController.java` | Controller | 合规检查接口 |
| `service/ComplianceCheckService.java` | Service | 合规检查入口编排、结果持久化、DTO 组装 |
| `service/ComplianceCheckPolicy.java` | Policy | 总体状态、风险分和默认风险建议等纯决策 |
| `service/ComplianceIssueFactory.java` | Factory | 统一构造规则失败与执行失败问题 |
| `service/ComplianceRuleEvaluator.java` | Evaluator | 规则定义解析与项目/标书规则求值 |
| `entity/ComplianceRule.java` | Entity | 合规规则实体 |
| `entity/ComplianceCheckResult.java` | Entity | 合规检查结果实体 |
| `repository/ComplianceRuleRepository.java` | Repository | 合规规则数据访问 |
| `repository/ComplianceCheckResultRepository.java` | Repository | 检查结果数据访问 |
| `dto/ComplianceCheckResultDTO.java` | DTO | 检查结果视图对象 |
| `dto/ComplianceIssue.java` | DTO | 合规问题对象 |
| `dto/RiskAssessmentDTO.java` | DTO | 风险评估对象 |
