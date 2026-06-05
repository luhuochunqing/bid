# BidResult 模块 (投标结果闭环模块)

> 一旦我所属的文件夹有所变化，请更新我。

## 职责
投标结果闭环模块负责：
1. 手工登记中标/落标 + 合同期限（含 SKU/金额/备注）
2. 内部 ERP/CRM 同步 + 公开信息同步 + 人工确认
3. 销售上传中标通知书/分析报告提醒
4. 竞争对手中标记录（SKU/品类/折扣/账期）及聚合报表

## Split-First 服务拆分
原 `BidResultService`（365 行上帝类）已拆为 5 个单一职责服务：

| 服务 | 职责 | 依赖数 |
|------|------|--------|
| `BidResultQueryService` | 只读 overview/详情/列表 | 5 |
| `BidResultRegistrationService` | 手工登记 / 补录 / 确认 / 忽略 | 3 |
| `BidResultReminderService` | 提醒创建 / 发送 / 批量发送 | 4 |
| `BidResultSyncService` | 内部同步 / 公开同步 + 同步日志 | 4 |
| `CompetitorReportService` | 竞争对手中标登记 + 聚合报表 | 3 |

## 纯核心 (FP-Java Profile)
`core/` 下为 Record/无状态静态方法，无 Spring / JPA / IO：

| 文件 | 职责 |
|------|------|
| `AwardRegistration` | 登记表单纯值对象 |
| `AwardRegistrationValidation` | 登记校验规则（返回 ValidationResult） |
| `CompetitorWinRow` | 报表输入单行 |
| `CompetitorReportRow` | 报表输出单行 |
| `CompetitorReportComputation` | 聚合算法（SKU sum / 品类众数 / 折扣均值 / 趋势） |
| `BidResultReminderLogic` | 提醒状态流转纯函数 |

## 架构模式：函数式内核，命令式外壳 (Functional Core, Imperative Shell)
本模块的服务类（如 `BidResultReminderService`）遵循此模式进行重构，以提升可测试性和性能：

1.  **函数式内核 (Functional Core)**：
    - 位于 `core/` 包。
    - **职责**：纯业务逻辑计算。接收状态参数，返回新状态或实体。
    - **约束**：禁止注入 Spring Bean，禁止调用数据库/外部接口，禁止获取系统时间（需作为入参）。
    - **收益**：100% 确定性，毫秒级单元测试，无需 Mock。

2.  **命令式外壳 (Imperative Shell)**：
    - 位于 `service/` 包（即原 `@Service` 类）。
    - **职责**：Side-Effects 编排。负责 I/O（读写数据库）、获取系统时间、调用内核函数。
    - **优化**：在内存中完成计算后，将多次数据库写入合并为一次，减少 SQL 开销。

示例：`BidResultReminderService` 将所有状态判定移至 `BidResultReminderLogic`，Service 仅负责 Repository 的存取。

## 边界清单
| 文件 | 地位 | 功能 |
|------|------|------|
| `controller/BidResultController.java` | Controller | HTTP 派发，不做业务决策 |
| `service/BidResultQueryService.java` | Service | 只读查询聚合 |
| `service/BidResultRegistrationService.java` | Service | 手工登记 + 补录 + 确认/忽略 |
| `service/BidResultReminderService.java` | Service | 提醒写入 + 发送 |
| `service/BidResultSyncService.java` | Service | 同步/外部同步 + 日志 |
| `service/CompetitorReportService.java` | Service | 竞争对手中标登记 + 报表 |
| `core/*` | Pure Core | 校验 + 聚合纯函数（无框架依赖） |
| `entity/BidResultFetchResult.java` | Entity | 外部同步/同步/手工登记结果实体 |
| `entity/BidResultReminder.java` | Entity | 上传提醒记录实体 |
| `entity/BidResultSyncLog.java` | Entity | 同步/外部同步操作日志实体 |
| `entity/CompetitorWinRecord.java` | Entity | 竞争对手中标记录实体 |
| `repository/BidResultFetchResultRepository.java` | Repository | 结果记录数据访问 |
| `repository/BidResultReminderRepository.java` | Repository | 提醒记录数据访问 |
| `repository/BidResultSyncLogRepository.java` | Repository | 同步日志数据访问 |
| `repository/CompetitorWinRecordRepository.java` | Repository | 竞争对手中标记录访问 |
| `dto/BidResultAssembler.java` | Assembler | Entity→DTO 单一装配 |
| `dto/CompetitorReportAssembler.java` | Assembler | 竞争对手 Entity/Core→DTO 装配 |
| `dto/BidResultOverviewDTO.java` | DTO | 概览卡片视图对象 |
| `dto/BidResultFetchResultDTO.java` | DTO | 待确认结果视图对象（含合同期限/备注/SKU） |
| `dto/BidResultReminderDTO.java` | DTO | 提醒记录视图对象 |
| `dto/BidResultCompetitorReportRowDTO.java` | DTO | 竞争对手报表行对象 |
| `dto/BidResultDetailDTO.java` | DTO | 结果详情视图对象 |
| `dto/BidResultActionRequest.java` | DTO | 闭环动作请求对象 |
| `dto/BidResultSyncResponseDTO.java` | DTO | 同步/外部同步响应对象 |
| `dto/BidResultRegisterRequest.java` | DTO | 手工登记请求 |
| `dto/BidResultUpdateRequest.java` | DTO | 登记补录请求 |
| `dto/CompetitorWinRequest.java` | DTO | 竞争对手中标登记请求 |
| `dto/CompetitorWinDTO.java` | DTO | 竞争对手中标记录视图对象 |

## 数据迁移
- `V50__create_bid_result_tables.sql` — 原始三表
- `V61__bid_result_closure_enhancement.sql` — 补齐合同期限/备注/SKU/附件链接字段 + 竞争对手中标记录表
