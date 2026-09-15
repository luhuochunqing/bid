---
title: 商机预测提醒
space: engineering
category: feature
tags: [商机预测, 提醒, 定时任务, 通知中心, 企微, CRM事件库, OSS, 组织架构, opportunity-prediction]
created: 2026-08-24
updated: 2026-09-16
health_checked: 2026-09-16
sources:
  - specs/054-opportunity-prediction-reminder/spec.md
  - specs/054-opportunity-prediction-reminder/data-model.md
  - specs/063-opportunity-reminder-live-recipients/spec.md
  - docs/implementation-notes/CO-商机预测提醒.md
  - backend/src/main/java/com/xiyu/bid/workbench/service/OpportunityPredictionReminderService.java
  - backend/src/main/java/com/xiyu/bid/workbench/service/OpportunityPredictionRecipientResolver.java
  - backend/src/main/java/com/xiyu/bid/workbench/domain/OpportunityPredictionRecipientPolicy.java
  - backend/src/main/java/com/xiyu/bid/integration/organization/infrastructure/client/OrganizationDirectoryStrictBatchLookup.java
  - backend/src/main/java/com/xiyu/bid/workbench/service/OpportunityPredictionReminderSupport.java
  - backend/src/main/java/com/xiyu/bid/integration/tenderevent/infrastructure/sdk/OpportunityPredictionEventPublisher.java
backlinks:
  - _index
  - integration-tender-event-push
---
# 商机预测提醒

## 核心结论

- **提醒时机 = 预测招标时间**：`OpportunityPredictionPolicy.predict()` 返回的"预测招标时间"即"该去创建商机的时点"；
  提醒条件 `today >= predicted` 且从未提醒过（`OpportunityPredictionReminderPolicy`）。
  有服务周期截止时间时，到点即自然满足"央企提前 6 个月 / 其他提前 3 个月"。
- **每项目只创建一次通知**：独立表 `opportunity_prediction_reminder_log`（`project_id` 唯一键），站内通知成功后落表，DB 层兜底防并发双发。
- **事件失败可恢复**：`event_published_at=NULL` 表示 CRM 事件待重试；每日扫描只重推事件，不重复站内信或企微任务，SDK 受理后补写时间。事件先用条件 UPDATE 原子领取，多 Pod 同时扫描时只有一个实例调用 SDK；10 分钟未完成的领取可恢复。
- **接收人**：OSS 实时确认在职的项目经理（`Project.managerId`）+ OSS 实时活动角色中的投标管理员（`/bidAdmin`）+ 投标组长（`bid-TeamLeader`），合并去重；本地用户表只负责“工号 → 站内用户 ID”映射，不再作为在职状态或角色事实源。
- **渠道**：站内信（`NotificationApplicationService.createNotification`，企微经 `NotificationCreatedEvent` 自动镜像，按工号推送无工号 SKIP）+ 西域事件库（`OpportunityPredictionEventPublisher` → `/eventbus/publishEvent`）。

## 关键决策（规范外）

1. **项目经理来源**：定时任务无登录态，`CrmAuthService` 禁止系统服务号换 CRM JWT，不能实时反查 CRM；
   负责人身份取"自动分配"已落库的 `Project.managerId`，是否仍在职则以本轮 OSS 批量实时回查为准。
2. **文案兜底**：无服务周期截止日期时省略"服务周期将于…截止"句，只保留"预测下次招标时间为…，请到CRM创建商机。"
3. **事件发布隔离**：事件库失败不阻断站内信/企微主链路；失败状态持久化，后续扫描只补发事件。
4. **结果类型回退**：`ProjectResult.resultType` 优先，缺失回退 `Project.status.name()`。
5. **事件推送字段口径对齐表格 enrich 链（2026-09-06）**：商机预测表格 `WorkbenchOpportunityQueryService.toDto` 消费 `ProjectQueryService.getAllProjects()` 的 enrich 后 `ProjectDTO`，故事件推送四字段须对齐同一 enrich 链——`customer`=`Project.customer` 优先→`Tender.purchaserName` 兜底（ownerUnit 列）；`projectLeaderName`=立项详情优先→`Tender.projectManagerName` 兜底（`ProjectListEnrichmentSupport`）；`customerType`=`Project.customerType` 优先（严格 null 判空）→`Tender.customerType` 兜底并经 `InitiationFieldPolicy.normalizeCustomerType` 归一化；`projectType`=`ProjectInitiationDetails.projectType` 优先→`Tender.projectType` 兜底并经 `normalizeProjectType` 归一化，逐条对齐 `ProjectListEnrichmentSupport.populateFromTender`（**注意：项目内 `ProjectTenderPopulator` 为死代码，勿再引用**），且该兜底值**同时驱动预测规则**（央企提前 6 月/其他 3 月），预测逻辑与表格完全一致。**注意 `customer` 与站内信 `resolvePurchaserName`（tender 优先）方向相反**——事件推送对齐表格展示口径，站内信对齐提醒文案口径，不可互换。
6. **项目负责人来源**：取 `project_initiation_details.projectLeaderName`（`Project` 实体无此字段），`runScan` 用 `findByProjectIdIn` 批量加载避免 N+1；立项详情缺失时回退 `Tender.projectManagerName`（见决策5）。
7. **事件全量字段**：应 CRM 要求，商机事件 `data` 从轻量标识扩展为 11 字段（客户最新增加 `projectType`）（见下节），推送触发/策略/开关/线程池/流水编码均不变。
8. **渠道状态分离（2026-09-10）**：提醒日志表示站内提醒已创建，`event_published_at` 表示 SDK 已受理。历史行按 `notified_at` 回填，防止部署后重放全部历史事件；已知失败项目必须按项目单独置空后验收。扫描不持有长事务，通知日志、领取、成功确认、失败释放均为独立短事务，SDK 调用发生在事务外。

## 实时收件人解析与 OSS 批量回查（2026-09-16）

### 事实源与执行顺序

1. `OpportunityPredictionRecipientResolver` 从本地 `users` 枚举非空工号，并建立规范化工号到站内用户 ID 的映射。
2. 每轮扫描只调用一次 `OrganizationDirectoryGateway#getUserJobAndRoleListByJobNumbersStrict`，OSS 返回值决定账号是否在职及当前活动角色。
3. `status` 必须明确为 `1/true/active/enabled/启用/在职` 才能接收；未返回或未知状态 fail-closed。
4. `sysRoleList` 对象先排除 `del=true`（兼容数字 `1`）或非活动 `status`，再优先读取 `roleCode`；字符串型旧响应继续兼容。
5. 核心角色在前，实时在职项目经理追加，按站内用户 ID 去重后进入既有站内信、企微镜像和事件链路。

### 批量接口恢复契约

| 响应 | 处理 |
|---|---|
| HTTP 200 且业务码 `0/200/OK/SUCCESS`，数据非空 | 映射返回人员 |
| HTTP 200 且成功业务码、`data={}` | **成功空结果**，立即结束该批，不重试、不拆分 |
| HTTP 5xx / 408 / 429 / 传输失败 | 短暂重试一次；仍失败则递归二分隔离组合型异常 |
| HTTP 4xx、非法 JSON、未知业务码 | 不可重试，整轮失败关闭 |
| 单工号重试后仍失败，或超过 64 次额外请求/55 秒恢复预算 | 整轮失败关闭；不创建新通知和项目提醒日志 |

测试环境真实边界：8,509 个工号按 50 人分 171 个基础批次；一个 50 人组合稳定返回 HTTP 500，重试后二分到 12 人的成功空结果和其余成功结果。严格回查共 177 次请求、6.627 秒，返回 1,163 名当前人员，识别 3 名核心角色（`/bidAdmin=3`、`bid-TeamLeader=1`，其中 1 人兼任）。

### 副作用（Cross-Module Impact）

- **普通组织同步不变**：仍调用允许返回部分结果的原查询；严格重试/二分仅供商机提醒使用。
- **提醒唯一日志不变**：实时回查失败时收件人快照为空，新提醒被跳过，因此不会用部分角色集写入一次性 `opportunity_prediction_reminder_log`。
- **历史补发不在本次修复内**：本次仅修正每日扫描的收件人事实源；历史项目补发需在测试验收后单独执行一次性、可审计流程。
- **09:00 验收指标**：应出现 scheduled start/done、严格回查完成、`roleRecipients=3`（组织未变化时）及总耗时小于 60 秒；`notified=0` 只能证明调度和解析链路，不能证明企微实际送达。

## 通知链路复用

- 通知：`CreateNotificationRequest(OPPORTUNITY_PREDICTION, "Project", projectId, title, body, payload, recipients)`；
  payload 含 `targetUrl=/bidding/customer-opportunities` 深链（前端 `resolveNotificationRoute` 已支持）。
- 事件：`TenderEventCode.OPPORTUNITY_PREDICTION("OpportunityPrediction", "bid", "OPPORTUNITY", "projectId")`；
  开关 `xiyu.integrations.tender-event.sdk.enabled`（默认 false，需配置 true）。

## 事件推送 data 全量字段（2026-09-06）

商机事件 `OpportunityPredictionEventPayload`（11 字段，经 `OpportunityPredictionEventPublisher` → SDK `sendEvent`）：`projectId` / `projectName` / `resultType`(中文) / `customer`(招标主体，口径见关键决策5) / `projectLeaderName`(立项详情→标讯兜底，见决策5/6) / `customerType`(中文，Project→标讯兜底，见决策5) / `projectType`(中文，立项详情→标讯兜底) / `bidOpenTime` / `servicePeriodYears` / `servicePeriodEndDate` / `predictedBidDate`。固定输出 11 个 key；除数字 `projectId` 外，无值字段传空字符串。

- **⚠️ traceId/spanId varchar(19)**：商机侧曾用 UUID(32 字符) 超事件库列宽，2026-09-06 改用 `TenderEventSnowflakeUtil` 雪花 ID（"t"/"s"+18 位）。约束与修复细节详见 [[integration-tender-event-push]] §11。
- **纯派生下沉 Support**：`resolveResultType`/`resolvePurchaserName`/`resolveCustomer`/`resolveProjectLeaderName`/`resolveCustomerType`/`resolveProjectType`/`buildPayload` 抽到同包 `OpportunityPredictionReminderSupport`（`final` + 私有构造 + 静态方法，对齐 `ProjectListEnrichmentSupport`），编排服务从 315 行降回 267 行，满足 line-budget 300 硬门禁。
- **预测逻辑口径（已对齐表格）**：`OpportunityPredictionPolicy` 的 input 与事件推送共用 `resolvedCustomerType`（`Project.customerType` 优先→`Tender.customerType` 兜底），预测提前月数规则（央企 6 月/其他 3 月）与表格完全一致。`Project.customerType` 空但标讯有值的项目，提醒触发时点随兜底后客户类型变化（如按央企提前 6 月），属对齐表格的预期行为（2026-09-06 review 后续确认预测逻辑一起对齐）。
- **副作用**：事件发布失败保留待重试状态，不阻断站内信/企微；后续扫描会继续调用 SDK，成功后停止（见关键决策3/8）。

## 数据模型

`opportunity_prediction_reminder_log`：id / project_id(UNIQUE) / predicted_bid_date / purchaser_name / notified_at /
event_published_at(NULL=待重试) / event_publish_claim_token / event_publish_claimed_at / created_at。

## 相关

- 预测规则：`OpportunityPredictionPolicy`（四条规则 + 1 年展示窗口（`VISIBLE_WINDOW_MONTHS = 12`） + 90 天红点）。2026-09-15 将展示窗口由 6 个月扩大到 1 年。
- 工作台展示：`OpportunityPredictionCard.vue` 支持按项目名称和招标主体搜索，支持导出 11 列商机预测表格（CSV）。表头筛选区采用 4 列二维网格实现上下左右两端对齐，导出按钮纵向跨两排（Row-Span 2）。
- 既有参考：`WarehouseExpiryScanTask`（每日 09:00 调度模板）、`QualificationExpiryNotificationService`（编排模板）
