# 商机预测提醒 - 任务拆解 (054)

> 状态标记：`[x]` 完成 / `[ ]` 待做。每个任务完成需附测试证据。

## 阶段 1：纯核心（预测复用 + 判定 + 文案）

- [x] **T1 复用预测规则**：确认 `OpportunityPredictionPolicy.predict()` 四条规则已完整实现，本需求直接调用不重复实现。
  - 证据：`OpportunityPredictionPolicyTest` 13 例全绿（既有）。
- [x] **T2 提醒判定策略**：新增 `OpportunityPredictionReminderPolicy.shouldNotify(predicted, today, lastNotifiedAt)`
  （`today >= predicted` 且未提醒过）。
  - 证据：`OpportunityPredictionReminderPolicyTest` 6 例全绿。
- [x] **T3 文案模板**：新增 `OpportunityPredictionAlertMessage.from(...)`，有/无截止日期两分支 + 空值兜底。
  - 证据：`OpportunityPredictionAlertMessageTest` 4 例全绿。

## 阶段 2：编排与调度

- [x] **T4 通知类型扩展**：`NotificationType` 新增 `OPPORTUNITY_PREDICTION`；`WeComMessageFormatter.humanType()` 补"商机预测"。
- [x] **T5 事件库发布**：`TenderEventCode` 新增 `OPPORTUNITY_PREDICTION`；新增 `OpportunityPredictionEventPublisher`
  （复用 `TenderEventSdkProperties` / trace 头模式，开关控制，失败返回 false）。
- [x] **T6 提醒日志表**：Flyway `V1200__add_opportunity_prediction_reminder_log.sql`（`db/migration-mysql/` 活跃目录）
  + 回滚 `U1200`（`db/rollback/migration-mysql/`）+ JPA 实体 + Repository（`findByProjectId`）。
- [x] **T7 编排服务**：`OpportunityPredictionReminderService.runScan(today)` —— 全量加载项目/结果/标讯/用户 →
  预测 → 判定 → 解析接收人（角色 + 启用项目经理）→ 通知 → 落日志 → 发事件。
  - 证据：`OpportunityPredictionReminderServiceTest` 5 例全绿（命中通知 / 已提醒去重 / 未来跳过 / 无接收人跳过 / 空扫描）。
- [x] **T8 定时任务**：`OpportunityPredictionReminderScanTask`（`@Scheduled` 每日 09:00）。

## 阶段 3：前端

- [x] **T9 通知类型映射**：`notificationHelpers.js` 新增 `OPPORTUNITY_PREDICTION` 图标（`TrendCharts`）+ 标签"商机预测"；
  `resolveNotificationRoute` 已支持 `payload.targetUrl` 深链，无需改路由表。
  - 证据：`TrendCharts` 图标导出校验 OK。

## 阶段 4：验证与收尾

- [x] **T10 构建验证**：`mvn compile` + 定向测试（13 例迁移/契约回归）全绿；
  全量回归 8403 例 1 失败（`ProjectAccessGuardCoverageTest` 新服务未进基线）→ 补基线条目复跑通过；
  迁移目录修复（V1200 → `db/migration-mysql`）消除原 45 个 context 加载错误。
- [ ] **T11 门禁基线**：`OpportunityPredictionReminderService` 加入 `project-access-guard-baseline.txt`
  （定时任务无登录态，无法调项目访问守卫；见实现笔记决策 9）。—— 已并入 T10 验证。
- [x] **T12 文档**：本 spec + `docs/implementation-notes/CO-商机预测提醒.md` + wiki 回填（`opportunity-prediction-reminder` 页 + `_index.md` + `log.md`）。
- [ ] **T13 PR 与合入**：确认门禁（Flyway 版本递增、wiki health-check、lessons 编号）后提交 PR。

## 阶段 5：事件发布失败可恢复（2026-09-10）

- [x] **T14 规格与回归测试**：明确站内提醒去重状态与事件库受理状态分离；先补失败重试红灯测试。
- [x] **T15 持久化迁移**：新增 `event_published_at`，历史记录按 `notified_at` 回填，并提供回滚脚本。
- [x] **T16 编排与契约修复**：首次失败保留待重试；后续扫描只重推事件；通知校验失败不落日志、不发事件；商机事件空值固定输出 11 字段。
- [x] **T17 验证与知识沉淀**：专项与架构门禁、前端构建、wiki、行数门禁、自审均完成；全量后端回归的环境/既有失败已记录在实现笔记。
- [x] **T18 PR**：已原子提交并通过 Gitee API 创建 PR !2610，附测试与部署后项目 983 验收 SQL。
