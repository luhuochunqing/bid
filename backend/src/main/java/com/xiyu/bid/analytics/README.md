# Analytics 模块（合规检查与看板分析能力）

> 一旦我所属的文件夹有所变化，请更新我。

## 职责说明
Analytics 模块负责项目和标讯的合规性校验、风险评估、看板聚合、指标下钻与客户类型维度分析。
该目录承载的是分析和规则检查能力，不负责业务主流程状态写入。
对外暴露统一的检查、查询、风险评估、看板和客户类型分析接口。
会被 Redis cache 序列化的看板 DTO 必须保持可序列化。

## 边界清单

| 文件 | 地位 | 功能 |
|------|------|------|
| `entity/` | 子目录 | 合规规则与检查结果实体边界 |
| `entity/ComplianceRule.java` | Entity | 合规规则实体 |
| `entity/ComplianceCheckResult.java` | Entity | 合规检查结果实体 |
| `repository/` | 子目录 | 合规数据访问边界 |
| `repository/ComplianceRuleRepository.java` | Repository | 合规规则数据访问 |
| `repository/ComplianceCheckResultRepository.java` | Repository | 检查结果数据访问 |
| `service/` | 子目录 | 合规检查服务边界 |
| `service/ComplianceCheckService.java` | Service | 合规检查业务逻辑 |
| `controller/` | 子目录 | 合规检查 API 边界 |
| `controller/ComplianceController.java` | Controller | 合规检查 REST API |
| `dto/` | 子目录 | 合规结果与风险评估边界 |
| `dto/ComplianceCheckResultDTO.java` | DTO | 检查结果传输对象 |
| `dto/ComplianceIssue.java` | DTO | 合规问题项 |
| `dto/RiskAssessmentDTO.java` | DTO | 风险评估结果 |
| `controller/CustomerTypeAnalyticsController.java` | Controller | 客户类型分析与下钻 REST API |
| `dto/CustomerTypeAnalyticsResponse.java` | DTO | 客户类型分析响应 |
| `dto/CustomerTypeDimensionDTO.java` | DTO | 客户类型维度聚合项 |
| `dto/CustomerTypeDrillDownRowDTO.java` | DTO | 客户类型下钻行 |
| `model/CustomerTypeAggregate.java` | Model | 客户类型纯聚合结果 |
| `model/CustomerTypeProjectRow.java` | Model | 客户类型查询行快照 |
| `service/CustomerTypeAnalyticsQueryService.java` | Service | 客户类型只读查询 |
| `service/CustomerTypeAnalyticsComputationService.java` | Service | 客户类型纯计算 |
| `service/CustomerTypeAnalyticsAssemblerService.java` | Service | 客户类型 DTO 组装 |
| `service/DashboardAnalyticsService.java` | Service | 看板分析门面 |
| `service/DashboardAnalyticsMetricDrillDownService.java` | Service | 看板指标下钻编排 |
