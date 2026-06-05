# Resources 模块 (平台资源域)

> 一旦我所属的文件夹有所变化，请更新我。

## 职责
资源模块统一管理平台账户、费用、BAR 证书和站点子资源，覆盖借用、审批、支付登记、归还和校验流程。这里是资源域的总入口，负责把多个资源子能力维持在同一套边界内。
BAR 证书借阅记录属于项目关联记录：借阅/归还会复用 `ProjectAccessScopeService` 做项目访问断言，记录查询复用 `ProjectLinkedRecordVisibilityPolicy` 保证非管理员仅看到可见项目或未关联项目的记录。
费用子域新增“保证金退还跟踪”链路：纯核心只负责根据已确认开标结果、预计退还日期和已提醒时间做提醒判定；应用服务负责扫描候选费用、写入提醒历史和手工发送提醒。

## 边界清单
| 文件 | 地位 | 功能 |
|------|------|------|
| `controller/AccountController.java` | Controller | 平台账户接口 |
| `controller/ExpenseController.java` | Controller | 费用申请、审批、支付登记与退还接口 |
| `controller/BarAssetController.java` | Controller | BAR 证书资产接口 |
| `controller/BarCertificateController.java` | Controller | BAR 证书借还接口 |
| `controller/BarSiteSubresourceController.java` | Controller | BAR 站点子资源接口 |
| `service/AccountService.java` | Service | 账户业务逻辑 |
| `service/ExpenseService.java` | Service | 费用对外门面服务 |
| `service/expense/ExpenseCommandService.java` | Service | 费用命令编排 |
| `service/expense/ExpenseQueryService.java` | Service | 费用查询编排 |
| `service/expense/ExpensePaymentService.java` | Service | 费用支付登记与记录查询 |
| `application/service/ScanDepositReturnTrackingAppService.java` | App Service | 自动扫描保证金退还跟踪并生成提醒 |
| `application/service/SendExpenseReturnReminderAppService.java` | App Service | 手工发送保证金退还提醒 |
| `service/BarAssetService.java` | Service | BAR 资产业务逻辑 |
| `service/BarCertificateService.java` | Service | BAR 证书借阅、归还和项目关联记录权限编排 |
| `service/BarSiteSubresourceService.java` | Service | 站点子资源业务逻辑 |
| `domain/service/DepositReturnReminderPolicy.java` | Domain Service | 纯核心提醒判定 |
| `domain/model/DepositReturnTrackingSnapshot.java` | Domain Model | 提醒判定快照 |
| `domain/model/DepositReturnReminderDecision.java` | Domain Model | 提醒判定结果 |
| `entity/` | Entity | Account、BarAsset、Expense、ExpensePaymentRecord、BarCertificate、BarSite* 实体 |
| `repository/` | Repository | 资源数据访问（含支付记录仓储） |
| `dto/` | DTO | 资源请求/响应模型（含支付记录 DTO） |
