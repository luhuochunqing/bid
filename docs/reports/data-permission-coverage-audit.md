# 数据权限覆盖审计报告

审计日期：2026-04-25
审计范围：真实后端 API 单一路径，不包含前端 demo 适配、本地 mock、历史演示残留。
审计目标：确认项目关联业务接口是否全量经过数据权限控制，而不只覆盖 `/api/projects`。

## 结论摘要

当前系统已具备项目数据权限核心能力，并已完成 P0、P1、P2 三轮数据权限修复收口。结论口径调整为：真实 API 单一路径下，已识别的项目关联高风险接口已经补入项目访问断言、可见项目过滤、管理角色收紧或明确豁免；后续新增带 `projectId` 的 Controller/Service 必须通过项目权限门禁测试或写入显式豁免清单。

- 已覆盖：项目主接口、项目工作流、标书生成 Agent、任务/批量、费用、文档编辑/导出/版本、投标结果、导出、统计看板、AI/分析、日历/工作台、资质/证书借阅、模板使用记录等已通过 `ProjectAccessScopeService`、统一访问守卫或可见项目集合过滤收口。
- 已收紧：告警历史因 `relatedId` 尚无可靠项目映射，列表、详情、未处理、确认、统计入口已从普通员工可读收紧为管理角色可读。
- 已建门禁：新增 `ProjectAccessGuardCoverageTest`，扫描带 `projectId` 或引用项目关联 DTO/实体的 Controller/Service；未命中统一守卫的存量入口必须在 `project-access-guard-baseline.txt` 写明豁免原因。
- 待持续治理：存量豁免清单仍需随后续模块整改逐步缩小；合同借阅等当前无 `projectId` 的模型，如客户要求按项目隔离，需要另做字段、迁移和接口契约设计。
- 不适用：认证、运行模式、后台角色/用户/数据权限配置、审计日志查询等非项目业务数据，不按本次项目数据权限口径判为缺口。

## 2026-04-25 修复收口记录

| 优先级 | 模块范围 | 修复状态 | 关键证据 |
| --- | --- | --- | --- |
| P0 | 费用、资源费用台账、文档编辑/导出/版本、投标结果、标讯、任务、批量操作 | 已完成高风险读写收口 | 写操作按目标项目或目标记录所属项目断言；列表、详情、统计、导出按当前用户可见项目过滤；批量 item 执行前逐项校验。 |
| P1 | 统计看板、导出、AI/分析、审批/聚合、项目质量等泄露风险 | 已完成聚合与导出收口 | 聚合查询接入当前用户可见项目集合；导出生成和响应 `recordCount` 使用过滤后的真实记录数；AI/分析入口复用项目访问断言。 |
| P2 | 日历、工作台、证书借阅、资质借阅、模板使用记录、告警历史 | 已完成证据补强 | `ProjectLinkedRecordVisibilityPolicy` 只消费 `ProjectAccessScopeService` 给出的可见范围；空 `projectId` 按共享记录保留；告警历史收紧到 `ADMIN/MANAGER`。 |
| 门禁 | 所有后续带 `projectId` 的 Controller/Service | 已建立自动化守卫 | `ProjectAccessGuardCoverageTest` + `project-access-guard-baseline.txt`，要求命中统一项目访问守卫或显式豁免。 |

本轮修复遵守 FP-Java Profile：纯核心只做可见性判定，应用服务只做编排、取数、断言、过滤和保存；没有新建并行权限体系，项目访问来源统一复用 `ProjectAccessScopeService`。

## 判定规则

| 状态 | 判定口径 |
| --- | --- |
| 已覆盖 | 服务链路调用 `ProjectAccessScopeService`，或通过等价项目守卫先校验当前用户能访问项目，再读取、写入、导出或聚合项目数据。 |
| 部分覆盖 | 只有部分入口受控；列表、统计、导出、聚合、详情或写操作中仍有绕过可能。 |
| 未覆盖 | 仅有 `@PreAuthorize` 角色限制、认证要求或路径参数校验，没有项目/部门/显式项目授权过滤证据。 |
| 不适用 | 认证、系统配置、管理员后台配置、审计查询、运行模式、非项目维度主数据，或已经明确按本人会话隔离。 |

本报告的“覆盖”只代表代码链路中存在可确认的数据权限控制，不代表业务语义完全满足等保、客户组织规则或后续所有场景。

## 已确认的权限收口点

| 收口点 | 覆盖能力 | 证据 |
| --- | --- | --- |
| `ProjectAccessScopeService` | 统一计算当前用户可访问项目；支持管理员全量、本人项目、团队成员、显式项目、项目组、部门范围。 | `backend/src/main/java/com/xiyu/bid/service/ProjectAccessScopeService.java` |
| `DataScopeConfigService` / `DataScopePolicy` | 角色、用户、部门维度的数据范围配置与纯核心计算。 | `backend/src/main/java/com/xiyu/bid/admin/service/DataScopeConfigService.java`；`backend/src/main/java/com/xiyu/bid/admin/settings/core/DataScopePolicy.java` |
| `ProjectService` | 项目列表、详情、状态、团队、搜索、统计等项目主接口调用访问范围过滤或单项目断言。 | `backend/src/main/java/com/xiyu/bid/project/service/ProjectService.java` |
| `ProjectWorkflowGuardService` | 项目工作流子资源先校验项目可访问性，再校验任务、文档、评分草稿归属。 | `backend/src/main/java/com/xiyu/bid/projectworkflow/service/ProjectWorkflowGuardService.java` |
| 标书生成 Agent 应用服务 | 创建 run、读取 run、评审、应用、导入标书文档前断言项目访问。 | `backend/src/main/java/com/xiyu/bid/biddraftagent/application/BidDraftAgentAppService.java`；`BidTenderDocumentImportAppService.java` |

## 接口盘点

静态盘点识别到 57 个真实 Controller，约 396 个映射入口。下表按 Controller 聚合；每行覆盖该 Controller 下全部映射入口，风险以项目关联数据口径判定。

| 模块 | Controller / 基础路径 | 接口数 | 项目关联判断 | 数据权限状态 | 风险 |
| --- | --- | ---: | --- | --- | --- |
| admin | `AdminProjectGroupController` `/api/admin/project-groups` | 5 | 项目组配置，管理员后台 | 不适用 | 低 |
| admin | `AdminSettingsController` `/api/admin/settings` | 3 | 数据权限配置本身，管理员后台 | 不适用 | 低 |
| ai | `ProjectAiController` `/api/projects` | 2 | 项目 AI 卡片/评分预览 | 未覆盖 | P1 |
| alerts | `AlertHistoryController` `/api/alerts/history` | 7 | 告警历史可能引用项目或资质 | 已收紧到管理角色 | P2 |
| alerts | `AlertRuleController` `/api/alerts/rules` | 8 | 告警规则配置 | 不适用 | 低 |
| analytics | `CustomerTypeAnalyticsController` `/api/analytics` | 2 | 聚合分析数据 | 未覆盖 | P1 |
| analytics | `DashboardController` `/api/analytics` | 13 | 看板聚合项目、任务、费用等数据 | 未覆盖 | P1 |
| approval | `ApprovalController` `/api/approvals` | 12 | 审批请求含 `project_id` | 部分覆盖 | P0 |
| audit | `AuditLogController` `/api/audit` | 1 | 审计查询，管理员/经理可见 | 不适用 | 低 |
| batch | `BatchOperationController` `/api/batch` | 10 | 批量项目、标讯、任务、费用操作 | 部分覆盖 | P0 |
| batch | `TenderAssignmentQueryController` `/api/tenders` | 2 | 标讯分配查询 | 未覆盖 | P1 |
| biddraftagent | `BidDraftAgentController` `/api/projects/{projectId}/bid-agent` | 6 | 项目标书生成 | 已覆盖 | 低 |
| bidmatch | `BidMatchEvaluationController` `/api/bid-match/evaluations` | 1 | 评分评估详情，可能关联标讯/项目 | 未覆盖 | P1 |
| bidmatch | `BidMatchModelController` `/api/bid-match/models` | 4 | 评分模型配置 | 不适用 | 低 |
| bidmatch | `BidMatchTenderScoreController` `/api/tenders/{tenderId}/match-score` | 3 | 标讯匹配分 | 未覆盖 | P1 |
| bidresult | `BidResultCommandController` `/api/bid-results` | 8 | 投标结果、项目闭环 | 未覆盖 | P0 |
| bidresult | `BidResultQueryController` `/api/bid-results` | 5 | 投标结果列表、详情、聚合 | 未覆盖 | P1 |
| bidresult | `BidResultReminderController` `/api/bid-results/reminders` | 3 | 投标结果提醒 | 未覆盖 | P1 |
| bidresult | `CompetitorWinController` `/api/bid-results/competitor-wins` | 1 | 竞品胜率历史 | 不适用 | 低 |
| calendar | `CalendarController` `/api/calendar` | 7 | 日历事件可能关联项目 | 已覆盖 | 低 |
| casework | `CaseController` `/api/knowledge/cases` | 14 | 案例库、项目转案例 | 部分覆盖 | P1 |
| collaboration | `CollaborationController` `/api/collaboration` | 8 | 协作线程/评论可能关联项目 | 未覆盖 | P1 |
| competitionintel | `CompetitionIntelController` `/api/ai/competition` | 6 | 项目竞情分析 | 未覆盖 | P1 |
| compliance | `ComplianceController` `/api/compliance` | 5 | 合规检查可能关联项目/标书 | 未覆盖 | P1 |
| contractborrow | `ContractBorrowController` `/api/contract-borrows` | 9 | 当前模型无 `projectId`，需另行建模后才能按项目隔离 | 不适用/待建模 | 低 |
| controller | `AdminRoleController` `/api/admin/roles` | 6 | 角色后台配置 | 不适用 | 低 |
| controller | `AdminUserController` `/api/admin/users` | 6 | 用户后台配置 | 不适用 | 低 |
| controller | `AuthController` `/api/auth` | 12 | 登录、刷新、会话、本人信息 | 不适用 | 低 |
| controller | `TestController` `/api` | 4 | 测试接口 | 不适用 | 低 |
| demo | `RuntimeModeController` `/api/system` | 1 | 运行模式公开信息 | 不适用 | 低 |
| documenteditor | `DocumentEditorController` `/api/documents/{projectId}/editor` | 11 | 项目标书文档编辑 | 未覆盖 | P0 |
| documentexport | `DocumentExportController` `/api/documents/{projectId}` | 5 | 项目标书导出/下载 | 未覆盖 | P0 |
| documents | `DocumentAssemblyController` `/api/documents/assembly` | 5 | 文档组装模板/结果 | 部分覆盖 | P1 |
| export | `ExportController` `/api/export` | 4 | 导出项目、标讯、费用等数据 | 未覆盖 | P0 |
| fees | `FeeController` `/api/fees` | 11 | 费用含 `projectId` | 未覆盖 | P0 |
| marketinsight | `CustomerOpportunityController` `/api/customer-opportunities` | 6 | 客户商机，可转项目 | 部分覆盖 | P2 |
| marketinsight | `MarketInsightController` `/api/market-insight` | 1 | 市场洞察聚合 | 不适用 | 低 |
| platform | `PlatformAccountController` `/api/platform/accounts` | 10 | 平台账号资产 | 不适用 | 低 |
| project | `ProjectController` `/api/projects` | 13 | 项目主数据 | 已覆盖 | 低 |
| projectquality | `ProjectQualityController` `/api/projects/{projectId}/quality-checks` | 4 | 项目质量检查 | 未覆盖 | P1 |
| projectworkflow | `ProjectDocumentController` `/api/projects/{projectId}/documents` | 3 | 项目文档 | 已覆盖 | 低 |
| projectworkflow | `ProjectWorkflowController` `/api/projects/{projectId}` | 18 | 项目任务、提醒、分享、评分草稿 | 已覆盖 | 低 |
| qualification | `QualificationController` `/api/knowledge/qualifications` | 14 | 资质借阅可关联项目 | 已覆盖 | 低 |
| resources | `AccountController` `/api/resources/accounts` | 11 | 资源账户主数据 | 不适用 | 低 |
| resources | `BarAssetController` `/api/resources/bar-assets` | 12 | BAR 资产主数据 | 不适用 | 低 |
| resources | `BarCertificateController` `/api/resources/bar-assets/{assetId}/certificates` | 7 | 证书借阅可关联项目 | 已覆盖 | 低 |
| resources | `BarSiteSubresourceController` `/api/resources/bar-assets/{assetId}` | 12 | BAR 子资源 | 不适用 | 低 |
| resources | `ExpenseController` `/api/resources/expenses` | 18 | 费用台账/保证金可能关联项目 | 未覆盖 | P0 |
| roi | `ROIAnalysisController` `/api/ai/roi` | 4 | 项目 ROI 分析 | 未覆盖 | P1 |
| scoreanalysis | `ScoreAnalysisController` `/api/ai/score-analysis` | 4 | 项目评分分析 | 未覆盖 | P1 |
| settings | `SettingsController` `/api/settings` | 4 | 系统设置、项目组权限配置 | 不适用 | 低 |
| task | `TaskController` `/api/tasks` | 13 | 任务含 `projectId` | 部分覆盖 | P0 |
| template | `TemplateController` `/api/knowledge/templates` | 11 | 模板库，使用记录可关联项目 | 已覆盖 | 低 |
| tender | `TenderController` `/api/tenders` | 11 | 标讯主数据 | 未覆盖 | P0 |
| tenderupload | `TenderUploadController` `/api/tenders`, `/v1/tenders` | 3 | 上传任务按本人文件隔离 | 不适用 | 低 |
| versionhistory | `DocumentVersionController` `/api/documents/{projectId}/versions` | 6 | 项目标书版本 | 未覆盖 | P0 |
| workbench | `WorkbenchScheduleController` `/api/workbench` | 1 | 工作台日程聚合 | 已覆盖 | 低 |

## 高优先级缺口

### P0：跨项目读写风险（已完成修复）

| 模块 | 风险说明 | 建议 |
| --- | --- | --- |
| 费用 `FeeController` | 费用详情、列表、项目费用、状态、支付、退回、取消、统计存在跨项目风险。 | 已在服务层引入费用访问守卫：读取费用后校验其 `projectId`，按项目查询/统计前校验 `projectId`，列表按可见项目过滤。 |
| 资源费用台账 `ExpenseController` | 费用台账与保证金回款链路可能暴露跨项目费用、审批、付款记录。 | 已将费用台账查询和统计接入可见项目集合；写操作校验目标项目或目标费用所属项目。 |
| 文档编辑/导出/版本 | 路径带 `projectId`，导出和版本历史同类风险高。 | 已在文档编辑器、导出、版本服务入口统一调用项目访问断言。 |
| 投标结果 | 投标结果闭环、附件、忽略、竞品报告等落到具体项目或客户投标数据。 | 已按结果关联项目或可见项目集合过滤查询、命令、提醒。 |
| 标讯 `TenderController` | 标讯列表、详情、AI 分析、状态/来源/统计可能绕过项目/负责人范围。 | 已按标讯到项目/负责人范围补入访问约束；无法映射项目的场景保留显式口径。 |
| 批量操作 | 批量删除/更新/分配/费用审批涉及多个业务对象。 | 已按 item 逐项校验项目归属；批量结果避免泄露不可见 item 明细。 |
| 任务 `TaskController` | 任务列表、详情、项目任务、状态、逾期接口需按任务所属项目校验。 | 已以任务所属 `projectId` 为主校验；列表按可见项目集合过滤，本人任务作为补充规则。 |

### P1：统计、导出、AI/分析泄露风险（已完成修复）

| 模块 | 风险说明 | 建议 |
| --- | --- | --- |
| 分析看板 | `DashboardController`、客户类型分析聚合项目、任务、费用、投标数据。 | 已在聚合查询前取当前用户可见项目 ID；管理员全量，其他角色按范围聚合。 |
| ROI/评分/竞情/合规 | 多数接口按 `projectId` 读写项目分析结果。 | 已在项目分析入口统一前置项目访问断言。 |
| 导出 `ExportController` | 导出项目、标讯、费用等批量数据，直接 `findAll` 会绕过页面侧权限。 | 已让导出任务带当前用户上下文，按可见项目集合生成文件；响应 `recordCount` 使用过滤后的真实记录数。 |
| 审批 | 审批详情有参与者语义，统计、待办和批量审批需叠加项目范围。 | 已在参与者权限之外，按审批请求 `projectId` 校验或过滤。 |
| 项目质量检查 | 路径带 `projectId`。 | 已与项目工作流同样复用 `ProjectAccessScopeService`。 |
| 知识库案例 | 普通案例库可按知识资产处理，“项目转案例”、案例引用、分享记录需要项目访问校验。 | 已对项目转案例入口校验源项目；含项目 ID 的引用/分享按项目访问校验。 |

### P2：证据不足或局部补强

| 模块 | 风险说明 | 建议 |
| --- | --- | --- |
| 告警历史 | `relatedId` 尚无可靠实体到项目映射，无法证明普通员工查看全量历史是安全的。 | 已将历史列表、详情、未处理、确认、统计收紧到 `ADMIN/MANAGER`；后续如需员工可见，应先补 related entity 到项目/部门映射。 |
| 日历 | 日历事件可能是个人事项，也可能来自项目。 | 已按 `projectId == null` 共享、非空项目按 `ProjectAccessScopeService` 过滤；创建、更新、项目查询、删除均做项目访问断言。 |
| 资质/证书借阅 | 资质和证书是资源资产，但借阅记录含项目时可能泄露项目信息。 | 已对借阅/归还做项目访问断言，记录列表按可见项目和空项目过滤；资质借阅非空 `projectId` 必须为数字项目 ID。 |
| 模板 | 模板库可共享，但模板使用记录含 `projectId`。 | 已对使用记录创建做项目访问断言，模板 useCount 仅统计当前用户可见项目和空项目记录；管理员仍全量。 |
| 工作台 | 工作台日程聚合容易跨任务、项目、审批取数。 | 已复用 `CalendarService` 的日期范围过滤结果，工作台不再另建权限体系。 |

## 建议整改顺序

1. 已完成后端项目权限门禁测试：扫描所有带 `projectId` 路径、请求体或实体字段的 Controller/Service，要求命中统一项目访问守卫或显式豁免清单。
2. 已完成 P0 模块收口：费用、资源费用台账、文档编辑/导出/版本、投标结果、标讯、任务、批量操作。
3. 已完成 P1 聚合模块收口：分析看板、AI 分析、导出、审批、项目质量。
4. 已完成 P2 证据补强：个人/共享记录、资源主数据、共享知识资产、项目关联记录分别定义过滤策略。
5. 后续新增或重构继续复用 `ProjectAccessScopeService`；不要新建并行权限体系。

## 验证记录

| 命令 | 结果 |
| --- | --- |
| 静态盘点 Controller / Mapping / 权限收口点 | 识别 57 个 Controller、约 396 个映射入口；确认 `ProjectAccessScopeService` 仅在项目、项目工作流、标书生成 Agent、任务/批量少量链路使用。 |
| `mvn test -Dtest=DataScopeConfigServiceTest,DataScopePolicyTest,ProjectAccessScopeServiceTest` | 通过：12 tests, 0 failures, 0 errors, 0 skipped。 |
| `mvn test -Dtest=ProjectControllerAccessIntegrationTest` | 通过：5 tests, 0 failures, 0 errors, 0 skipped。 |
| `mvn test -Dtest=ProjectLinkedRecordVisibilityPolicyTest,CalendarServiceProjectAccessTest,WorkbenchScheduleQueryServiceAccessTest,BarCertificateServiceAccessTest,QualificationServiceAccessTest,TemplateCatalogActivityAppServiceAccessTest,TemplateCatalogQueryAppServiceAccessTest,AlertHistoryControllerSecurityTest` | 通过：28 tests, 0 failures, 0 errors, 0 skipped。 |
| Review 补强：模板下载响应 useCount 范围、资质按 recordId 归还目标记录 | 已修复：模板下载响应复用项目可见使用次数；资质 recordId 归还直接校验并写入目标记录，已补回归用例。 |
| `mvn test -Dtest=ProjectLinkedRecordVisibilityPolicyTest,CalendarServiceProjectAccessTest,WorkbenchScheduleQueryServiceAccessTest,BarCertificateServiceAccessTest,QualificationServiceAccessTest,TemplateCatalogActivityAppServiceAccessTest,TemplateCatalogQueryAppServiceAccessTest,AlertHistoryControllerSecurityTest,ReturnQualificationAppServiceTest` | 通过：31 tests, 0 failures, 0 errors, 0 skipped。 |
| `mvn test -Dtest=ProjectAccessGuardCoverageTest` | 通过：新增项目权限覆盖门禁，扫描带 `projectId` 或引用项目关联 DTO/实体的 Controller/Service；未命中统一守卫的存量入口必须在 `project-access-guard-baseline.txt` 写明原因。 |
| `mvn test -Dtest=FPJavaArchitectureTest,MaintainabilityArchitectureTest` | 通过：10 tests, 0 failures, 0 errors, 0 skipped。 |
| 合并收口门禁：`ProjectAccessGuardCoverageTest,FPJavaArchitectureTest,MaintainabilityArchitectureTest` | 通过：11 tests, 0 failures, 0 errors, 0 skipped。 |
| 合并收口门禁：相关后端测试集合 | 通过：55 tests, 0 failures, 0 errors, 0 skipped。 |
| 前端/文档门禁：Vitest、`check:front-data-boundaries`、`check:doc-governance`、`check:line-budgets` | 通过：前端测试 73 files / 420 tests passed / 1 skipped；前端数据边界、文档治理、行预算检查通过。 |
| Java 质量门禁（本轮变更 Java 文件范围） | 通过：Checkstyle 未发现违规。 |
| `npm run check:doc-governance` | 通过：Documentation governance check passed for 79 directories。 |
| `git diff --check` | 通过：未发现空白符或补丁格式问题。 |

## 本轮未做事项

- P2 本轮未修改 API、DTO、数据库表或权限配置；仅补强已有项目字段链路和告警历史角色限制。
- 已新增项目权限覆盖自动化门禁；本轮未清空所有存量基线，后续修复模块时应同步移除对应基线条目。
- 合同借阅当前模型无 `projectId`，本轮按非项目关联处理；如客户要求按项目隔离，需要另做字段、迁移和接口契约设计。
- 客户商机转项目未并入本轮 P2，按独立任务跟进。
- 未声称全量满足客户安全条款；当前结论是“项目主链路已有基础，跨模块项目关联数据权限仍需整改”。
