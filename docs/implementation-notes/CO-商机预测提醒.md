# Implementation Notes: CO-商机预测提醒

> 功能：商机预测提醒（054）| 状态：开发完成，待合入

## 需求要点

需求给出 4 条预测规则 + 固定文案 + 三类通知角色 + 三个消息渠道。
经排查，4 条预测规则已在既有 `OpportunityPredictionPolicy.predict()` 完整实现
（`WorkbenchOpportunityController` 注释明确"提醒通知暂未接入，仅提供列表数据"），
本次实际交付的是**提醒通知链路**（每日定时扫描 → 判定 → 多渠道通知）。

## 规范之外的关键决策

### 1. 触发语义 = "预测招标时间"即"该去创建商机的时点"

需求没有明确"什么时候提醒"，只给了预测规则。设计约定：

- `predict()` 返回的"预测招标时间"就是触发提醒的时点；
- 提醒条件：`今天 >= 预测招标时间` 且该项目从未提醒过；
- 该语义下，规则①（央企提前 6 个月 / 其他提前 3 个月）自然成立——到点时恰好是服务周期截止前 6/3 个月。

实现为纯核心 `OpportunityPredictionReminderPolicy.shouldNotify(predicted, today, lastNotifiedAt)`。

### 2. 项目经理来源：取"自动分配"已落库的 Project.managerId，而非 CRM 实时反查

用户指出已有"自动分配标讯给项目经理"功能，项目经理来自西域 CRM 接口，要求"去查 CRM 里这个招标主体属于哪个项目经理"。

实现约束：定时任务**无登录态**，而 `CrmAuthService` 明确**禁止系统服务号换 CRM JWT**（必须基于真实用户 OSS token），因此无法在无登录态的每日任务里实时调 CRM 反查。

**落地方案**：自动分配功能经 CRM 反查后，把项目经理的 User id 落库到 `Project.managerId`
（见 `TenderCommandService.applyAssignmentResult`）。本项目直接读 `Project.managerId`
即等价于"用 CRM 查得的项目经理"。对 managerId 缺失项目，接收人只剩两个角色，不额外兜底。

### 3. 去重：站内提醒与事件库受理状态分离

用户确认"每项目只提醒一次"。为避免依赖可能被清理的 `notification` 表，
新增独立表 `opportunity_prediction_reminder_log`（`project_id` 唯一），站内通知创建成功后落表，
从数据库层兜底防并发双发。2026-09-10 补充 `event_published_at`：NULL 表示事件待重试，
非空表示 SDK 已受理。后续扫描只重推待处理事件，不再创建站内信或企微任务。
为避免多 Pod 同时补发，`event_publish_claim_token/event_publish_claimed_at` 通过条件 UPDATE 原子领取；领取超过 10 分钟可恢复。

### 4. 文案兜底：无服务周期截止日期时省略"服务周期将于…截止"句

需求文案固定为"服务周期将于 {到期日期} 截止，预测下次招标时间为 {预测招标时间}…"，
但无截止日期项目（规则 2/3/4）没有到期日期。用户确认：此时省略截止句，
文案变为"预测下次招标时间为 {预测招标时间}，请到CRM创建商机。"

### 5. CRM 事件库 = 西域统一事件库，复用标讯事件发布模式

用户确认"CRM 事件库"指西域统一事件库。`OpportunityPredictionEventPublisher` 与
`TenderEventSdkProducer` 分别直接调用客户 `SystemInteractiveService.sendEvent`，由 SDK 负责
`/eventbus/publishEvent` 的请求协议与 trace Header。
事件类型 `OPPORTUNITY_PREDICTION` 加入 `TenderEventCode`。

为避免 Spring 对 SDK 内部 `client.register` 配置二次绑定污染，装配层分别构造隔离的
`SystemInteractiveService` 实例并通过构造器传入两个发布器，不把 SDK 服务注册为共享 Bean。
已删除历史共享 `TenderEventHttpSender` / `TenderEventSdkSender` 协议层。

### 6. 定时任务无登录态 → repository 全量直查

不能复用带权限过滤的 `ProjectQueryService.getAllProjects()`（依赖登录态），
改用 `ProjectRepository.findAll()` 全量直查，再批量关联 `ProjectResult`（按 projectId）与 `Tender`（按 tenderId）。

### 7. 结果类型解析回退链

流标判定输入 `resultType`：`ProjectResult.resultType` 优先，缺失回退 `Project.status.name()`。

### 8. 容错与隔离

- 扫描本身无事务；通知及日志、事件领取、确认与释放均为 `REQUIRES_NEW` 短事务，单项目失败不回滚其他项目；
- 无接收人（角色空且经理未启用）：跳过该项目的通知与落日志；
- system actor 暂不可用时只跳过新通知，已有待重试事件仍继续领取和补发；
- `createNotification()` 返回无效结果：不写提醒日志、不发事件；
- SDK 在事务外调用；发布失败保留 `event_published_at=NULL` 并释放领取，后续每日扫描只重推事件；
- 历史提醒日志迁移时按 `event_published_at=notified_at` 回填，避免一次性重放。

### 9. ProjectAccessGuardCoverageTest 门禁基线

全量回归中 `ProjectAccessGuardCoverageTest` 会扫描所有触碰 projectId 的 Controller/Service，
要求走统一项目访问守卫或进 `project-access-guard-baseline.txt` 声明理由。
本服务是**定时任务、无登录态**，无法调用 `assertCurrentUserCanAccessProject`（依赖 SecurityContext），
故按既有调度服务模式（`TaskDueReminderService` / `CaExpiryScanService` / `PlatformAccountBorrowExpiryScanService`）
加入基线并写明理由：全量扫描预测到期并推送通知，非任何用户请求入口，接收人按角色码 + 落库 managerId 解析，无项目级数据访问决策。

### 10. 已知失败项目 983 的部署后验收

迁移会把所有历史记录视为事件已完成，因此不能把测试项目 ID 硬编码进通用迁移。测试环境部署后，
先执行 `UPDATE opportunity_prediction_reminder_log SET event_published_at=NULL, event_publish_claim_token=NULL, event_publish_claimed_at=NULL WHERE project_id=983;`，
再触发扫描。验收条件：站内信/企微不新增，SDK 日志显示 11 字段事件受理成功，
该行 `event_published_at` 被补写，扫描结果 `eventRetried=1`。

### 11. 商机事件固定 11 字段

`OpportunityPredictionEventPublisher.buildData` 固定按客户最新确认顺序输出 11 个 key。除必填数字
`projectId` 外，字符串、数值和日期无值时均输出空字符串，不能省略字段。该规则由
`publish_emptyOptionalFields_keepsElevenFieldContract` 使用项目 983 空值样本锁定。新增 `projectType` 位于 `customerType` 后；`ProjectInitiationDetails.projectType` 优先，缺失时从关联 Tender 归一化回退，publisher 再转中文标签。

## 文件变更

**新增（后端）**
- `workbench/domain/OpportunityPredictionReminderPolicy.java` — 提醒判定纯核心
- `workbench/domain/OpportunityPredictionAlertMessage.java` — 文案模板纯核心
- `workbench/service/OpportunityPredictionReminderService.java` — 无事务扫描与 SDK 编排
- `workbench/service/OpportunityPredictionReminderTransactionService.java` — 通知日志、领取、确认、释放的独立短事务
- `workbench/job/OpportunityPredictionReminderScanTask.java` — 每日 09:00 定时任务
- `workbench/entity/OpportunityPredictionReminderLog.java` + `workbench/repository/OpportunityPredictionReminderLogRepository.java`
- `integration/tenderevent/domain/OpportunityPredictionEventPayload.java` + `.../sdk/OpportunityPredictionEventPublisher.java`
- `resources/db/migration-mysql/V1200__add_opportunity_prediction_reminder_log.sql` + 回滚 `db/rollback/migration-mysql/U1200__add_opportunity_prediction_reminder_log.sql`
- `resources/db/migration-mysql/V1214__track_opportunity_event_publish.sql` + 回滚 `db/rollback/migration-mysql/U1214__track_opportunity_event_publish.sql`
- 测试：`workbench/domain/*ReminderPolicyTest`、`*AlertMessageTest`、`workbench/service/OpportunityPredictionReminderServiceTest`
- `src/test/resources/project-access-guard-baseline.txt` — 新增本服务门禁基线条目（见决策 9）

**编辑（后端）**
- `NotificationType` 新增 `OPPORTUNITY_PREDICTION`
- `TenderEventCode` 新增事件类型
- `WeComMessageFormatter.humanType()` 补"商机预测"

**编辑（前端）**
- `src/utils/notificationHelpers.js` — 新增图标（`TrendCharts`）+ 标签"商机预测"；
  `resolveNotificationRoute` 已支持 `payload.targetUrl` 深链（`/bidding/customer-opportunities`），无需改路由表

## 权衡与已知限制

- 企微按工号推送，无工号用户只收站内信（既有机制）；
- 事件库开关 `xiyu.integrations.tender-event.sdk.enabled` 默认 false，需配置为 true 才生效；
- 定时任务 `@Scheduled` 需应用启用调度（项目既有调度已启用，参考 `WarehouseExpiryScanTask`）；
- `OpportunityPredictionReminderLog` 与通知在同一短事务提交，SDK 不持有数据库事务；
- 投递语义为至少一次：SDK 已受理但成功状态提交前进程退出时，10 分钟租约到期后可能再次推送，消费端仍需按 `projectId` 业务键幂等。

## 测试证据

- `OpportunityPredictionPolicyTest` 13 例（既有，回归通过）
- `OpportunityPredictionReminderPolicyTest` 6 例（判定四分支）
- `OpportunityPredictionAlertMessageTest` 4 例（有/无截止句 + 空值兜底）
- `OpportunityPredictionReminderServiceTest` 7 例（命中通知 / 字段来源优先级与回退 / 已提醒去重 / 未来跳过 / 无接收人跳过 / 空扫描）
- `OpportunityPredictionEventRetryTest` 5 例（首次失败释放 / 待重试成功 / 连续失败不重复通知 / 多实例领取互斥 / system actor 缺失时仍重试）
- `OpportunityPredictionReminderTransactionBoundaryTest` 5 例（真实 Spring/JPA 提交、通知失败回滚、领取互斥、失败/超时恢复、后续项目失败不回滚前项）
- `OpportunityPredictionEventPublisherTest` 增加空值样本，断言固定 11 个字段且无值传空字符串
- 本次专项与架构门禁：`mvn clean -Dtest=ProjectAccessGuardCoverageTest,OpportunityPredictionEventPublisherTest,OpportunityPredictionEventRetryTest,OpportunityPredictionReminderServiceTest,OpportunityPredictionReminderTransactionBoundaryTest,ArchitectureTest,FlywayRollbackScriptCoverageTest,EntityTableMigrationCoverageTest,ResponsibilityArchitectureTest test` 通过（退出码 0）
- 全量后端回归执行 9577 例：发现并修复本次新增事务类缺少后台任务权限基线；其余失败为本地 MySQL 容器在套件中途断开（60 errors）以及既有无关用例 `CompetitionIntelControllerIntegrationTest`、`GoldenCasesReplayTest`，不作为本次通过证据
