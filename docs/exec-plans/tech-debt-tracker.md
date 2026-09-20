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

- area: `backend scoreparse /score-parse/scoring` scope 枚举（UNSATISFIED/ITEMS）
  type: dead-code
  severity: low
  status: open
  source: PR !2329 review FYI#5（2026-08-18，客户验收反馈 UI 精简）
  note: 前端打分范围单选已删，打分固定 scope=ALL；后端 UNSATISFIED/ITEMS 分支与前端 `scoringBody` 的 scope/itemIds 透传参数暂无生产调用方（仅 `defineExpose` 公开契约保留）。待产品确认不再恢复「仅不满足打分」后，评估下线后端枚举分支并简化 `runScoring`/`scoringBody` 签名。

- area: `frontendDemo` 适配层 / `demoPersistence`
  type: mock-leak
  severity: medium
  status: resolved
  source: SECURITY.md §Mock 政策（遗留代码现状）
  note: `src/utils/demoPersistence.js` 已删除；`check-front-data-boundaries` 仍拦截 import。

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
  note: 异步导出/导入任务表已第 6 次按模块逐字段复制（warehouse_export_task V1032、personnel_import_task V1022、platform_account_import_task V1084、ca_certificate_import_task V1085、tender_import_task V1153、performance_export_task V1184）。根因不是「没有通用表」——基线 export_tasks 一直在，但 /api/export 不写它，后来者只能抄 warehouse。存量专表不迁（独立任务）。防复发：scripts/check-async-task-table.sh 拦截新建 *_export_task/*_import_task（lessons-learned §139）。

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

### TenderAttachmentUrlResolver 静态字段注入反模式（integration.external）

> **来源**：PR !2331 生产风险审计（2026-08-18）F2 项
> **背景**：`TenderAttachmentUrlResolver` 用 `@Value`/`@Autowired` setter 注入**静态字段**（`publicBaseUrl`、`obsShareUrlSigner`），所有方法为静态方法。属 CO-280 既有债：静态 API 被 `TenderIntegrationMapper`、`TenderMapper`、`ProjectResultPayloadAssembler` 等多处直接调用，改实例化注入会级联波及所有调用点。
> **风险**：① 依赖 Spring 容器初始化顺序，Bean 创建前调用得到 null；② 单测需 ReflectionTestUtils 注入静态字段，易漏；③ 多 Spring 上下文测试（如 @SpringBootTest 并行）相互污染静态状态。
> **建议**：重构为实例方法 + 构造器注入，同时把 legacy doc-insight 兼容逻辑（`extractOwnLegacyDocInsightParams`/`ownAbsolutePathPrefix`/`isOwnIntegrationDownloadUrl`）拆出，解决文件顶格 300 行硬上限问题。
> **实测影响面**（2026-08-19 评估，PR !2331 审查期间）：主代码 4 文件（Resolver 本体 + `TenderIntegrationMapper`/`ProjectResultPayloadAssembler` 各 +1 final 字段，`TenderMapper` 需新增构造器）；测试 12+ 构造点（`new TenderIntegrationMapper(...)` ×8、`new ProjectResultPayloadAssembler(...)` ×2、`new TenderMapper()` ×3）+ 2 个反射设静态字段的测试文件（`TenderIntegrationMapperToDownloadUrlTest` 31 用例 / `CallerContextUrlResolverTest` 12 用例）需改实例构造。纯 DI 重构行为中性，编译器保证不遗漏调用点。
> **状态**：resolved（2026-08-19）。决策记录：不并入热修复 PR !2331，待其合入后从 main 开独立分支修复，避免跨模块重构拖慢生产热修复上线。
> **解决**：分支 `agent/qoder/resolver-di-refactor` 完成——Resolver 改实例方法 + 构造器注入，新建 `IntegrationEndpointUrls` 承载 publicBaseUrl 配置与端点路径常量（Resolver 300 → 252 行）；删除 `TenderIntegrationMapper.toDownloadUrl` 静态委托，调用方（`TenderMapper`/`TenderQueryService`/`ProjectResultPayloadAssembler`）改直接注入 Resolver；测试 12+ 构造点与 2 个反射测试文件全部改实例构造，消除静态状态污染。211 个相关用例 + ArchitectureTest 全绿。见 PR !2334。
> **复杂度**：中（半天），风险：低（行为中性 + 79 个 Resolver 相关测试兜底）。

### G1-走查补验：标书质量核查实质化端到端人工走查（spec 049）

> **来源**：spec 049 收尾决策（2026-08-20，产品确认）
> **背景**：spec 049「AI 标书质量核查实质化」代码实现与全部机器门禁（G2-G6 + FP/单测/前端 build）已完成并通过，但 G1 唯一主线（spec §2 压轴条款：真实投标 PDF + 真实招标 PDF 端到端跑一次核查，抽 3 个 FAIL/MANUAL 项逐项对照真实页码/原文摘录/招标要求/知识库记录）**尚未人工走查**。产品决策：知情风险下先合入 PR #2365，走查转本遗留项。
> **风险**：链路未经真实数据端到端验证，可能存在解析/定位/裁决在真实文件下的未暴露问题（如超大文件、扫描版 PDF、LLM 真实响应格式漂移）。
> **补验动作**：主工作区（trae）启动开发环境，按 `specs/049-bid-quality-check-realization/quickstart.md §3` 执行走查；发现问题按 lessons §140（047 验收失真复盘）标准追根因，不接受"形态通过"。
> **状态**：open（优先级 high，建议下次部署测试环境后第一时间补验）。

### 049-后续跟进：标书质量核查 PR !2365 审查遗留项（2026-08-21 评审）

> **来源**：PR !2365 事后评审（google-code-review），三处代码问题已在本轮顺手修复（见 PR 分支 `agent/zcode/fix-2365-followups`），以下为需另行跟进的项。
> **① LLM JSON 围栏剥离待验**：`LlmVerdictParser`/`TenderRequirementsParser` 直接 `ObjectMapper.readTree`，未处理 LLM 网关常见的 ```json 围栏（`CaseAiMatcher.java:268` 已有同款先例）。一旦模型返回带围栏 JSON，裁决全部降级 MANUAL、招标要求全 missing，功能静默失效。已排除在"顺手修复"外（需服务器端真实响应验证），建议 G1 走查/部署验证时同步补防御性剥离（纯解析层逻辑，可单测覆盖，不依赖端到端）。
> **② trigger 同步全量解析 + 双重解析**：`BidQualityCheckTaskAppService` 在 HTTP 请求线程做完整下载（OBS 超时上限 120s）+ Sidecar 解析校验"可解析性"，缓存未命中时 async executor 再把下载+解析重做一遍。大文件下 trigger 请求阻塞数分钟。建议：前置校验只查元数据（文件存在），解析校验移异步侧置 FAILED 态；或解析结果透传复用。注意勿削弱 G3 门禁（"无文件不核查 4xx"）。
> **③ `LlmAdjudicator.adjudicate()` 批量方法仅测试使用**：生产走引擎逐项 `adjudicateItem`，批量方法进度记账与引擎循环重复。保留作 API（契约测试覆盖），若后续简化可删并迁移测试走引擎。
> **④ tasks.md T051/T057/T058 未完成却勾选 [X]**：本 PR 修的正是"验收失真"，自身验收勾选有形态通过之嫌，已合入不便回改；后续 spec 收尾任务应保持 `[ ]` + ⚠️ 注释，完成后再勾。
> **状态**：open（①②建议随 G1 走查一并处理；③④可选）。

### knowledge_case 缺金额/业绩品类字段，业绩金额门槛只能文本正则匹配（casework）

> **来源**：spec 064 业绩评分项案例推荐准入（2026-09-16）
> **背景**：需求要求“办公业绩不低于 2000 万、工业品业绩不低于 4500 万”的候选准入。当前 `knowledge_case` 仅有 `product_line`（来源模块默认“综合产品线”，非品类），无合同/发票金额、无业绩品类字段；`PerformanceCaseAdmissionPolicy` 只能从应答文本正则提取金额（支持元/千/万/亿与千分位），无法读取权威结构化金额。
> **风险**：①文本未提金额的合规案例只能降级到 tier2（品类命中）；②文本含无关金额时可能误判；③金额门槛无法作为强一致门禁。
> **补验动作**：为 `knowledge_case` 增加 `performance_category` / `contract_amount`（万元）字段与 Flyway 迁移（注意并行迁移锁）；沉淀链路从项目合同/发票数据回填，导入脚本补齐历史切片；准入策略优先采用结构化字段、文本作为兜底。
> **状态**：open（优先级 medium，依赖业务确认金额数据来源与回填口径）。

### AI 标书质量核查解析链路残余债（spec 066，2026-09-20）

> **来源**：spec 066 修复（生产 22/35/46/580 核查失败）与 S6 三轮独立评审。
> **背景**：解析失败误报已修复（五分类 + 按原始大小分档 + 分块 100），以下为记录不修的残余项。

- area: `document-converter-sidecar/app.py`（ProcessPoolExecutor max_workers=1）
  type: dependency-debt
  severity: medium
  status: open
  source: spec 066 R0 证据（项目 30 连续 6 次 ~183s 重试期间小文件解析也超时）
  note: 单 Worker 串行导致解析饥饿；建议引入有界并发（按内存标定）或队列+截止时间，并对上游重试加单飞/退避。

- area: `backend/src/main/java/com/xiyu/bid/scoreparse`（SidecarDocumentArtifactExtractor / ScoreScoringAppService）
  type: test-gap
  severity: medium
  status: open
  source: spec 066 R0 证据（441MB 评分链路 600s/1800s 超时同样按固定档）
  note: 评分链路与质量核查共用 Sidecar，但超时预算未按文档规模分档；建议复用 `SidecarTimeoutBudget` 口径并做规模标定。

- area: `backend/src/main/java/com/xiyu/bid/compliance`
  type: dependency-debt
  severity: medium
  status: open
  source: spec 066 D7（记录不修）
  note: 解析产物无按内容哈希缓存；重试/多特性（质量核查+评分）会重复解析 19 分钟级文档。建议解析产物按 SHA-256 缓存复用（本地磁盘或 DB + TTL）。

- area: `backend/src/main/java/com/xiyu/bid/compliance/service/SidecarStreamDocumentAdapter.java`
  type: test-gap
  severity: low
  status: open
  source: spec 066 S6 评审（Round 2/3）
  note: ①连接建立超时（HttpConnectTimeoutException）分类由异常类型优先级保障，无自动化测试（10s 连接超时无法快速触发）；②失败路径 WARN 日志无断言。建议后续以可注入超时的 HTTP 客户端补测。

- area: `backend/src/main/java/com/xiyu/bid/compliance/application/BidQualityCheckAsyncExecutor.java`
  type: test-gap
  severity: low
  status: open
  source: spec 066 S6 生产风险评审（预存在）
  note: `catch (IOException | RuntimeException)` 不含 Error；若解析线程抛 Error，任务将永留 CHECKING。建议兜底 `catch (Throwable)` 并 markFailed（需与 FP 纪律确认）。

- area: `document-converter-sidecar`（无 OCR）
  type: dependency-debt
  severity: medium
  status: open
  source: spec 066 用户决策（本方案不引入 OCR）
  note: 纯图片扫描件（无文本层）不可核查，只能给能力边界提示；是否接入 OCR 由产品决策（另立项）。
