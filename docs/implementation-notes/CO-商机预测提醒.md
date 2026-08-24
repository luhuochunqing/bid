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

### 3. 去重：每项目只提醒一次，独立表 + project_id 唯一键

用户确认"每项目只提醒一次"。为避免依赖可能被清理的 `notification` 表，
新增独立表 `opportunity_prediction_reminder_log`（`project_id` 唯一），发送成功后落表，
从数据库层兜底防并发双发。

### 4. 文案兜底：无服务周期截止日期时省略"服务周期将于…截止"句

需求文案固定为"服务周期将于 {到期日期} 截止，预测下次招标时间为 {预测招标时间}…"，
但无截止日期项目（规则 2/3/4）没有到期日期。用户确认：此时省略截止句，
文案变为"预测下次招标时间为 {预测招标时间}，请到CRM创建商机。"

### 5. CRM 事件库 = 西域统一事件库，复用标讯事件发布模式

用户确认"CRM 事件库"指西域统一事件库。复用 `TenderEventSdkProducer` 的模式
（`POST /eventbus/publishEvent`、`serviceName/eventTopic/eventSource/data` 消息体、trace 头写 HTTP Header），
但**新增独立轻量 `OpportunityPredictionEventPublisher`**，不改动既有标讯事件管线（避免回归）。
事件类型 `OPPORTUNITY_PREDICTION` 加入 `TenderEventCode`。

### 6. 定时任务无登录态 → repository 全量直查

不能复用带权限过滤的 `ProjectQueryService.getAllProjects()`（依赖登录态），
改用 `ProjectRepository.findAll()` 全量直查，再批量关联 `ProjectResult`（按 projectId）与 `Tender`（按 tenderId）。

### 7. 结果类型解析回退链

流标判定输入 `resultType`：`ProjectResult.resultType` 优先，缺失回退 `Project.status.name()`。

### 8. 容错与隔离

- 单项目通知失败：try-catch 隔离，不拖垮整个扫描；
- 无接收人（角色空且经理未启用）：跳过该项目的通知与落日志；
- 事件库发布失败：只记日志，不阻断站内信/企微主链路。

### 9. ProjectAccessGuardCoverageTest 门禁基线

全量回归中 `ProjectAccessGuardCoverageTest` 会扫描所有触碰 projectId 的 Controller/Service，
要求走统一项目访问守卫或进 `project-access-guard-baseline.txt` 声明理由。
本服务是**定时任务、无登录态**，无法调用 `assertCurrentUserCanAccessProject`（依赖 SecurityContext），
故按既有调度服务模式（`TaskDueReminderService` / `CaExpiryScanService` / `PlatformAccountBorrowExpiryScanService`）
加入基线并写明理由：全量扫描预测到期并推送通知，非任何用户请求入口，接收人按角色码 + 落库 managerId 解析，无项目级数据访问决策。

## 文件变更

**新增（后端）**
- `workbench/domain/OpportunityPredictionReminderPolicy.java` — 提醒判定纯核心
- `workbench/domain/OpportunityPredictionAlertMessage.java` — 文案模板纯核心
- `workbench/service/OpportunityPredictionReminderService.java` — 编排（扫描→判定→通知→落日志→发事件）
- `workbench/job/OpportunityPredictionReminderScanTask.java` — 每日 09:00 定时任务
- `workbench/entity/OpportunityPredictionReminderLog.java` + `workbench/repository/OpportunityPredictionReminderLogRepository.java`
- `integration/tenderevent/domain/OpportunityPredictionEventPayload.java` + `.../sdk/OpportunityPredictionEventPublisher.java`
- `resources/db/migration-mysql/V1200__add_opportunity_prediction_reminder_log.sql` + 回滚 `db/rollback/migration-mysql/U1200__add_opportunity_prediction_reminder_log.sql`
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
- `OpportunityPredictionReminderLog` 落库与通知在同一事务（`@Transactional`），事件发布在事务内且失败不阻断。

## 测试证据

- `OpportunityPredictionPolicyTest` 13 例（既有，回归通过）
- `OpportunityPredictionReminderPolicyTest` 6 例（判定四分支）
- `OpportunityPredictionAlertMessageTest` 4 例（有/无截止句 + 空值兜底）
- `OpportunityPredictionReminderServiceTest` 5 例（命中通知 / 已提醒去重 / 未来跳过 / 无接收人跳过 / 空扫描）
- 全量后端回归：8403 例，1 失败（`ProjectAccessGuardCoverageTest` 新服务未进基线）→ 加基线条目后复跑通过；Flyway 迁移目录修复后 45 个 context 加载错误全部消除
