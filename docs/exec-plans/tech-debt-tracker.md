# 技术债追踪器 (tech-debt-tracker)

> 供后台"文档园丁" / 重构 agent 定期扫描的结构化技术债清单。新增技术债时追加到对应分类下，处理后标记 `status: resolved` 并保留记录。

## 命名约定

每条记录建议字段：
```
- area: <模块/包/文件>
  type: <god-class | dead-code | mock-leak | out-of-sync-doc | dependency-debt | test-gap>
  severity: <high | medium | low>
  status: <open | in-progress | resolved>
  source: <发现来源，如 implementation-notes 某行 / 某次 review>
  note: <一句话说明与建议处理方式>
```

## 当前已知技术债

来源：`SECURITY.md §Mock 政策` 与 `docs/reports/`、`backend/implementation-notes.md`。

### 安全权限类

- area: `backend/src/main/java/com/xiyu/bid/tender/controller/TenderTransferController.java`
  type: security
  severity: high
  status: resolved
  source: 权限矩阵审计报告（2026-06-17）§5.3 第1项
  note: 转派接口 Controller 层仅 `isAuthenticated()`，任何登录用户可调用。已修复：方法级注解 `@PreAuthorize("hasAnyRole('ADMIN', 'BID_LEAD', 'BID_SENIOR')")` 限制为投标管理员/组长。

- area: `backend/src/main/java/com/xiyu/bid/tender/controller/TenderController.java`
  type: security
  severity: high
  status: resolved
  source: 权限矩阵审计报告（2026-06-17）§5.3 第2项
  note: 编辑/删除接口白名单含 `ROLE_STAFF`，bid_specialist 可越权操作。已修复：`PUT/DELETE /api/tenders/{id}` 收窄为 `hasAnyRole('ADMIN', 'MANAGER')`。

- area: `backend/src/main/java/com/xiyu/bid/tender/controller/TenderController.java`
  type: security
  severity: medium
  status: resolved
  source: 权限矩阵审计报告（2026-06-17）§5.2 第4项 / §6 P1
  note: `participateBid`（投标决策）和 `abandonBid`（弃标决策）接口缺少 `@PreAuthorize`，仅依赖 Service 层校验，防护层过薄。已修复：添加 `@PreAuthorize("hasAnyRole('ADMIN', 'BID_LEAD', 'BID_SENIOR')")` 限制为投标管理员/组长。

### 遗留清理类

- area: `frontendDemo` 适配层 / `demoPersistence`
  type: mock-leak
  severity: medium
  status: open
  source: SECURITY.md §Mock 政策（遗留代码现状）
  note: 历史遗留适配层，仅作清理对象，不允许新增、不允许扩散。

- area: `src/mock` / `src/api/mock-adapters/` / `.env.mock`
  type: mock-leak
  severity: low
  status: resolved
  source: SECURITY.md §Mock 政策（前端 Mock）
  note: 已清空，`src/api/config.js` 硬编码 `mode: 'api'`。

### 文档同步类

- area: `docs/generated/db-schema.md`
  type: out-of-sync-doc
  severity: low
  status: resolved
  source: docs/generated/README.md
  note: 由 `scripts/generate-db-schema.mjs` 自动生成，跟随 Flyway 迁移刷新。

- area: `docs/lessons/README.md`
  type: out-of-sync-doc
  severity: medium
  status: resolved
  source: knowledge-capture (CO-279 / CO-281 session)
  note: 文件第 15 行残留未解决的 git 冲突标记 `<<<<<<< HEAD`，已在本 session 中修复，并补充 CO-279、spring-boot-actuator-gotchas 索引条目。建议后续提交前检查文档文件是否含冲突标记。

### 字段名双轨制

- area: `backend/src/main/java/com/xiyu/bid/tender/core/TenderEvaluationCustomerInfoPolicy.java`
  type: out-of-sync-doc
  severity: medium
  status: open
  source: docs/lessons/root-cause-analysis-co-266-co-267.md
  note: 客户信息 infoKey 存在双轨命名：EVALUATION_BASIS / INFO_TENDENCY_BASIS、CONTACT（旧 CRM 字段）/ CONTACT_INFO（新标准）。当前通过 TenderIntegrationService 兼容映射缓解，建议未来统一收敛为一套标准 key，并移除兼容代码。

### 流程不一致与死代码类

- area: `backend/src/main/java/com/xiyu/bid/tender/service/TenderSubmissionService.java`
  type: dead-code
  severity: low
  status: resolved
  source: CO-274 复盘（PR #842）
  note: `TenderSubmissionService.proceedToBid()` 没有任何 Controller 调用，疑似 V118/V119 快速投标遗留方法。已删除该方法及关联的 `copyEvaluationToProject` 方法，清理无用 import 和字段。

- area: `backend/src/main/java/com/xiyu/bid/tender/core/AssignmentPermissionRules.java`
  type: dead-code
  severity: low
  status: resolved
  source: 权限矩阵审计报告（2026-06-17）§5.5 第1项
  note: 审计报告误判为死代码。实际被 `TenderAssignmentPermissions.java` 第68行和第79行调用，用于 `canFill` 和 `canDecide` 权限判定。非死代码，无需清理。

- area: `src/views/Bidding/detail/useTenderActions.js` / `src/views/Bidding/list/useTenderListPage.js`
  type: out-of-sync-doc
  severity: medium
  status: open
  source: CO-274 复盘（PR #842）
  note: 标讯「投标」存在两个行为不一致的入口：详情页自动创建项目，列表页跳转 `/project/create` 手工创建。建议产品侧统一交互，或至少在两处入口补充一致的测试覆盖。

- area: `src/views/Bidding/detail/useTenderActions.js`
  type: test-gap
  severity: medium
  status: open
  source: CO-274 复盘（PR #842）
  note: `proceedToBid` 失败被空 catch 吞掉，导致后端 404 对用户不可见。需补充错误反馈与 E2E 回归测试。

- area: `backend/src/main/java/com/xiyu/bid/{warehouse,performance,...}/infrastructure/persistence/entity/*ExportTaskEntity.java` + `db/migration-mysql` 各模块任务表
  type: dependency-debt
  severity: medium
  status: open
  source: PR !2250 系统性设计评审（2026-08-04，CO-602）
  note: 异步导出/导入任务表已第 6 次按模块逐字段复制（warehouse_export_task V1032、personnel_batch_import_task V1022、platform_account_import_task V1084、ca_certificate_import_task V1085、tender_import_task V1153、performance_export_task V1184），字段完全同构（status/filter_snapshot/stored_file_path/expires_at/created_by 等）。PR !2250 引入的 `AbstractExportTaskStateService` 只抽象了 4 个状态转换方法，两个子类的 apply* 方法体仍逐字相同，executor 的三段式 catch 模板亦逐字雷同。下一次新增同类任务前，应合并为通用 `export_task` 表（加 `export_type` 列）+ 单实体 + executor 模板上移抽象类；涉及已有表迁移，需独立任务评估，不在功能 PR 内顺手做。

### 接口规范设计缺陷类

- area: 接口规范/CRM 对接（`docs/integration/integration-tender-api-v3.1.md` §3.2 推标讯接口 / `WebhookEventListener` / `TenderPushRequest`）
  type: out-of-sync-doc
  severity: medium
  status: open
  source: CO-277 深挖（CRM 实推商机主键 id，非编号 code）
  note: 推标讯接口仅定义 `crmOpportunityId` 字段，CRM 据此推送**主键 id**（如 20916）；但 `bidInfoSync` 回传契约要求商机**编号 code**（CC... 格式）。CO-277 的"识别纯数字 id → 反查 code"本质是补偿这个设计缺陷，而非弯路。演进路径：接口规范新增 `crmOpportunityCode` 字段让 CRM 显式推 code，代码优先用 code（`firstNonBlank(crmOpportunityCode, crmOpportunityId)`），保留 id 反查作为兜底；需 CRM 团队配合改推送代码。当前不改动——CO-277 已生效，改接口需外部协调，且向后兼容仍需保留 id 反查。

### 测试与生产环境对齐类

- area: 生产 MySQL sql_mode 配置
  type: out-of-sync-doc
  severity: medium
  status: open
  source: PR !1372 后续确认（2026-06-30，通过 SSH jetty@172.16.38.78 直连 RDS 查询）
  note: 生产 RDS（`winbid-01.test.rds.ehsy.com`，MySQL 8.0.43-251200）`@@sql_mode = ''`（空字符串，所有 strict mode 关闭）。测试侧 `AbstractMysqlIntegrationTest.TEST_SQL_MODE` 保留 `ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION`，比生产严格。已知影响：V1077 的 `'0000-00-00 00:00:00'` 字面量在生产合法（sql_mode 空不阻止零日期），在 MySQL 8.0 默认 strict mode 下会触发 Error 1292（测试侧已通过去掉 `NO_ZERO_DATE`/`NO_ZERO_IN_DATE` 对齐）。潜在风险：生产能跑过的某些"不严格"SQL（如截断字符串、零日期、非完全 GROUP BY）在测试会被拒绝，存在漏测可能。完整对齐需要先审计生产数据中是否存在零日期/截断字符串等问题，再决定是在生产开启严格模式还是在测试进一步放宽 sql_mode。属于运维侧独立任务，不在本 PR 范围内。确认结果与决策已记录在 `backend/src/test/java/com/xiyu/bid/support/FlywayMysqlContainerTest.java:47-62`。

- area: workbench-characterization.spec.js 本地环境失败
  type: flaky-test
  severity: low
  status: open
  source: CO-592 提交时发现（2026-07-19，agent/trae2），PR !2142 审查确认
  note: `workbench-characterization.spec.js` 在本地环境因 vue-router mock 加载失败而无法运行（CI 环境通过），导致 CO-592 提交时使用 `SKIP_TESTING_GATE=1` 逃生阀绕过 testing-gate。逃生阀使用应在 PR 描述中显式声明。根治方向：排查该 spec 的 vue-router mock 在本地与 CI 的环境差异（node 版本 / 依赖解析顺序），消除"本地必失败"状态；在根治前，任何因此跳过门禁的提交都应在 PR 描述中注明。

### 待登记

> 后续发现的技术债请追加到对应分类下，不要新建文件。

### 标讯去重：数据层清理 + 推送层策略加固（从 lessons §109 follow-up 提升为正式任务）

> **来源**：PR 日历/截止重复显示事故（2026-08-07）+ 思维链 Review H3。
> **背景**：展示层防御性去重（后端 `WorkbenchScheduleQueryService` / `WorkbenchDeadlineQueryService` + 前端）已收敛——前端薄防御层已移除（H2），去重单一事实源在后端。但**数据源真实重复未消除**，展示层去重仅是"症状管理"。以下两项需作为正式任务根治重复产生路径。

- area: `Tender` 表历史脏数据（生产库）
  type: dependency-debt
  severity: high
  status: open
  source: lessons-learned §109（2026-08-07）Follow-up 任务 1
  note: 生产环境 Tender 表存在真实重复标讯（日历与截止列表同时重复证实）。需开发清理脚本按业务键（`purchaserName + registrationDeadline + bidOpeningTime`）扫描，保留 `id` 最小的一条，合并/清理历史脏数据。清理前先备份，确认不误删有效记录。依赖持续观察（日历/截止去重 warn 日志）确认清理后是否仍有新重复进入。

- area: `backend/src/main/java/com/xiyu/bid/tender/core/TenderDeduplicationPolicy.java` + `backend/src/main/java/com/xiyu/bid/integration/external/TenderIntegrationCommandService.java`
  type: out-of-sync-doc
  severity: high
  status: open
  source: lessons-learned §109（2026-08-07）Follow-up 任务 2
  note: `TenderDeduplicationPolicy.isDuplicate()` 在任一关键字段（`purchaserName`/`registrationDeadline`/`bidOpeningTime`）为 null 时直接返回 false 不判重；`TenderIntegrationCommandService.rejectDuplicateBusinessTender()`（外部推送路径）同样对空时间字段跳过判重。漏洞：先插入字段不全记录，后续 update 补全时间字段时不再触发去重检查，导致重复进入数据库。需改为：字段不全时按已有字段做宽松匹配，或在 Tender 更新补全时间字段时重新触发去重检查（外部推送与 update 路径都要覆盖）。治理模型参照 `TenderDeduplicationService` Javadoc 的调用方覆盖情况。

- area: 持续观察（日历聚合 + 截止时间去重 warn 日志）
  type: test-gap
  severity: medium
  status: open
  source: lessons-learned §109（2026-08-07）Follow-up 任务 3
  note: 依赖后端去重命中时打印的 warn 日志（`WorkbenchScheduleQueryService` / `WorkbenchDeadlineQueryService`）持续监控，确认去重是否仍在触发、是否还有新重复进入，作为数据清理与策略加固完成的验收依据。

### 通知派发接收人按资源可见性过滤审视清单（spec 030）

> **来源**：spec 030 / 06131 案例（2026-07-06）
> **背景**：`TaskReviewNotificationService` 用 `findEnabledByRoleProfileCodes` 广播给所有投标专员/负责人，未过滤接收人对项目的访问权，导致无权用户（如 06131）收到通知后跳转 403。已通过 spec 030 修复（Phase 3，commit `8527766c0`）。
> **审视范围**：全仓 11 处调用 `findEnabledByRoleProfileCodes` 的通知派发点，判定是否需要同类修复。
> **结论**：**0 处需要同类修复**，10 处豁免（接收人全是 `dataScope=all` 全局角色 / 资源当事人 / targetUrl 跳全局可访问页）。

| # | 文件:行号 | 通知场景 | 接收人 | targetUrl | 判定 | 豁免依据 |
|---|---|---|---|---|---|---|
| 1 | `tender/service/TenderPendingAssignmentNotifier.java:66` | 标讯待分配通知 | `GLOBAL_ACCESS_ROLES` (admin+/bidAdmin+bid-TeamLeader，全 dataScope=all) | sourceEntity=TENDER（标讯总览，非项目详情） | ℹ️ 无需修 | ① 全局角色 + ③ targetUrl 非项目详情 |
| 2 | `tender/service/TenderEvaluationNotificationService.java:100` | 标讯评估通知 | `GLOBAL_ACCESS_ROLES` (全 dataScope=all) | sourceEntity=TENDER | ℹ️ 无需修 | ① 全局角色 |
| 3 | `resources/notification/CaNotificationDispatcher.java:166` | CA 证书到期通知 | `BID_ADMIN_CODES` (全 dataScope=all) + cert.custodianId 当事人 | sourceEntity=CA_CERTIFICATE（CA 资源） | ℹ️ 无需修 | ①②③ 全局角色 + 当事人 + 非 /project |
| 4 | `platform/service/PlatformAccountBorrowExpiryScanService.java:164` | 平台账号借用逾期通知 | `/bidAdmin` (dataScope=all) + applicantId/custodianId 当事人 | sourceEntity=ACCOUNT_BORROW_OVERDUE | ℹ️ 无需修 | ①②③ |
| 5 | `alerts/service/QualificationExpiryNotificationService.java:194` | 资质到期通知 | bid-administration + /bidAdmin + bid-TeamLeader | `/knowledge/qualification?id={id}` | ℹ️ 无需修 | ①③④ 全局角色 + bid-administration 本就是资质模块管理角色（含 QUALIFICATION_VIEW/MANAGE）+ 跳资质详情（非 /project）+ 全公司通用提醒 |
| 6 | `project/notification/ProjectNotificationService.java:262` | 立项/复盘/结项审核通知 | admin + /bidAdmin + bid-TeamLeader (全 dataScope=all) | `/project/{id}/{initiation,retrospective,closure}` | ℹ️ 无需修 | ① 全局角色全部能访问项目 |
| 7 | `project/notification/TaskReviewNotificationService.java:119` | **任务审核通知** | `TASK_MUTATION_ALLOWED_ROLES` (含 bid-Team) | `/project/{id}/drafting` | ✅ **spec 030 已修** | 唯一含 bid-Team（dataScope=self 受限角色）的调用点，06131 案例根因 |
| 8 | `project/service/ProjectClosureService.java:320` | 项目结项申请通知 | admin + /bidAdmin + bid-TeamLeader (全 dataScope=all) | `/project/{id}/closure` | ℹ️ 无需修 | ① 全局角色 |
| 9 | `project/service/ProjectRetrospectiveService.java:221` | 项目复盘通知 | admin + /bidAdmin + bid-TeamLeader (全 dataScope=all) | `/project/{id}/retrospective` | ℹ️ 无需修 | ① 全局角色 |
| 10 | `warehouse/service/WarehouseExpiryScanTask.java:50` | 仓库租约到期通知 | /bidAdmin + bid-TeamLeader (全 dataScope=all) | sourceEntity=WAREHOUSE_EXPIRY_WARNING（无 targetUrl） | ℹ️ 无需修 | ①③④ 全局角色 + 不跳项目 + 通用基建提醒 |
| 11 | `personnel/infrastructure/persistence/PersonnelNotificationAdapter.java:102` | 人员执业证书到期通知 | /bidAdmin + bid-TeamLeader (全 dataScope=all) | sourceEntity=PERSONNEL_CERT（无 targetUrl） | ℹ️ 无需修 | ①③④ 全局角色 + 不跳项目 + 通用人员提醒 |

**豁免条件参考**：① 接收人是 dataScope=all 全局角色（admin / /bidAdmin / bid-TeamLeader 等）；② 接收人是资源当事人（applicant/custodian/assignee 等）；③ targetUrl 跳转到接收人一定能访问的页面（通知中心/资源总览/非项目详情）；④ 通知场景是全公司广播（资质到期/仓库到期/人员到期等通用提醒）。

**关键洞察**：`TASK_MUTATION_ALLOWED_ROLES` 是全仓唯一把 `bid-Team`（普通受限角色）纳入广播范围的常量。未来新增通知派发器时，**接收人集合只要含 `bid-Team`/`bid-otherDept`/`bid-administration` 等 `dataScope=self` 角色，就必须对接收人做项目可见性过滤**（参考 `NotificationRecipientFilter` + `ProjectAccessScopeService.canAccessProject`）。这是 lessons §44 的核心检查清单项。
