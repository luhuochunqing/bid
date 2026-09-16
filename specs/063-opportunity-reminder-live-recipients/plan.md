# 商机预测历史补发 Implementation Plan

> **For agentic workers:** 按本计划在当前隔离 worktree 内执行，严格遵守 Red → Green → Refactor；本轮只允许部署测试环境，不得触碰生产。

**Branch**: `agent/codex/opportunity-reminder-history-replay` | **Date**: 2026-09-16 | **Spec**: [spec.md](spec.md)

**Goal:** 在不改动旧提醒日志和事件投递状态的前提下，把执行日前已提醒过的到期历史项目，按执行时实时在职的“项目经理 + 投标管理员 + 投标组长”完整补发一次。

**Architecture:** 保留现有每日扫描不变，新增默认关闭的一次性薄调度任务。补发服务从既有 reminder log 选择历史存量，复用实时收件人快照和通知应用服务，以独立通知来源形成项目级持久幂等标记；启动阶段永远只预演，只有指定日期 09:00 且显式 `execute` 才写入。

**Tech Stack:** Java 21、Spring Boot 3.2、Spring Data JPA、MySQL 8.0、JUnit 5、Mockito、H2 事务边界测试。

## Global Constraints

- 历史补发不新增表或 Flyway 迁移，不创建、更新或删除 `opportunity_prediction_reminder_log`。
- 历史补发不发布 `OpportunityPrediction` 事件，也不改变原事件的领取或投递状态。
- 收件人只采用 OSS 本轮明确返回为在职的项目经理及两个目标角色，本地缓存只负责工号到用户 ID 映射。
- `cutoff` 不得晚于 `runDate`；配置缺失、日期不匹配、默认关闭或启动阶段均不得写入。
- 测试环境执行前必须看到启动 preview；本轮禁止生产部署及生产数据写入。

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 3.2、Spring Scheduling、Spring Data JPA、现有 `NotificationApplicationService` 与 OSS `OrganizationDirectoryGateway`

**Storage**: MySQL 8.0 既有 `opportunity_prediction_reminder_log`、`notification`、`user_notification`、`notification_delivery_task`

**Testing**: JUnit 5 + Mockito 单测；Spring Boot + H2 事务边界测试；项目架构与 pre-push 门禁

**Target Platform**: 西域数智化投标管理平台 Linux 服务；首轮仅测试服务器 `172.16.38.78`

**Project Type**: 后端 Web 服务内部定时任务，无新增 HTTP 接口和前端页面

**Performance Goals**: 测试库 598 个项目、274 个历史候选在 60 秒内完成候选解析和通知创建；OSS 实时快照每轮只取一次

**Constraints**: 默认关闭、单实例执行、逐项目短事务、失败项目隔离、可重跑、无 schema 变更、无生产操作

**Scale/Scope**: 测试基线 274 个历史候选、324 个未来项目；每个候选最多创建一条补发通知，单条最多 1000 个去重接收人

## Constitution Check

*GATE: Phase 0 前及 Phase 1 后均通过。*

- **FP-Java**: 文案与 payload 派生留在无框架纯函数；调度、查询和写入留在 imperative shell。
- **Real API only**: 生产代码继续调用真实 OSS Gateway；测试只在单元边界 Mock，不引入运行时 Mock 模式。
- **TDD**: 先补消息、候选、幂等、调度安全测试并确认红灯，再写最小实现。
- **Split-first**: 新调度器只负责触发；既有 transaction service 负责短事务；补发 service 只编排一个用例。
- **OSS integration**: 一轮补发复用一个批量实时快照，不按项目重复访问 OSS。
- **Defensive collection**: DB 数据转 Map 使用显式循环或带 merge function 的 `toMap`，单项目异常只计入失败。
- **Security**: 不新增 Controller、权限或公网入口；开关由部署环境变量注入。
- **Deployment**: 先定向测试、全量门禁、测试部署、启动 preview、09:00 execute、执行后幂等复核；生产另行授权。

## Interfaces and Decisions

### Candidate query

`OpportunityPredictionReminderLogRepository` 新增：

```java
List<OpportunityPredictionReminderLog>
findByPredictedBidDateLessThanEqualAndNotifiedAtBeforeOrderByProjectIdAsc(
        LocalDate cutoff, LocalDateTime notifiedBefore);
```

边界固定为 `runDate.atStartOfDay()`，因此正常扫描在执行日新建的日志不进入历史补发。

### Persistent idempotency

`NotificationRepository` 按 `sourceEntityType + sourceEntityId + type` 查询。固定来源为 `OpportunityPredictionHistoryReplay`，类型仍为 `OPPORTUNITY_PREDICTION`，`sourceEntityId` 为项目 ID。`OpportunityPredictionReminderTransactionService` 提供：

```java
Set<Long> findExistingNotificationSourceIds(
        String sourceEntityType, String type, Collection<Long> sourceEntityIds);

boolean createNotificationIfAbsent(
        CreateNotificationRequest request, Long systemActor);
```

后一个方法在 `REQUIRES_NEW` 短事务内再次检查并创建；返回 `false` 表示已存在。单实例是无唯一索引方案的明确运行前提。

### Replay service

`OpportunityPredictionHistoryReplayService` 暴露：

```java
ReplayOutcome run(LocalDate runDate, LocalDate cutoff, ReplayMode mode);
```

`ReplayMode` 仅有 `PREVIEW`、`EXECUTE`。预演和执行共用同一候选查询、项目批量加载、实时收件人解析和幂等判断；`PREVIEW` 不调用任何写方法。

### Trigger safety

`OpportunityPredictionHistoryReplayProperties` 绑定：

- `XIYU_OPPORTUNITY_HISTORY_REPLAY_ENABLED`，默认 `false`
- `XIYU_OPPORTUNITY_HISTORY_REPLAY_MODE`，默认 `PREVIEW`
- `XIYU_OPPORTUNITY_HISTORY_REPLAY_RUN_DATE`，默认空
- `XIYU_OPPORTUNITY_HISTORY_REPLAY_CUTOFF`，默认空

`OpportunityPredictionHistoryReplayTask` 在 `ApplicationReadyEvent` 上只调用 `PREVIEW`；09:00 调度只有在启用、配置完整、`today == runDate` 且模式为 `EXECUTE` 时调用 `EXECUTE`。09:00 后重启只预演，不追补执行。

## Project Structure

```text
backend/src/main/java/com/xiyu/bid/
├── notification/repository/NotificationRepository.java
└── workbench/
    ├── domain/OpportunityPredictionAlertMessage.java
    ├── job/
    │   ├── OpportunityPredictionHistoryReplayProperties.java
    │   └── OpportunityPredictionHistoryReplayTask.java
    ├── repository/OpportunityPredictionReminderLogRepository.java
    └── service/
        ├── OpportunityPredictionHistoryReplayService.java
        ├── OpportunityPredictionReminderSupport.java
        └── OpportunityPredictionReminderTransactionService.java

backend/src/main/resources/application.yml

backend/src/test/java/com/xiyu/bid/workbench/
├── domain/OpportunityPredictionAlertMessageTest.java
├── job/OpportunityPredictionHistoryReplayTaskTest.java
└── service/
    ├── OpportunityPredictionHistoryReplayServiceTest.java
    └── OpportunityPredictionReminderTransactionBoundaryTest.java
```

**Structure Decision**: 不新增通用补发框架、不新增端点、不新增数据表。调度安全配置放在 `workbench/job`，业务编排放在 `workbench/service`，纯文案和 payload 规则复用现有 workbench 纯核心。

## Verification Strategy

1. **Red**: 消息测试先锁定“历史补发”可见标识；服务测试锁定完整去重收件人、preview 零写、旧通知不算送达、重跑幂等；任务测试锁定启动只预演和日期/模式门禁。
2. **Green**: 添加最小 repository 查询、短事务幂等创建、补发编排和默认关闭任务。
3. **Refactor**: 删除重复组装，保证正常 `OpportunityPredictionReminderService` 与事件发布代码零行为变化。
4. **Targeted**: 运行所有 opportunity prediction 相关测试和事务边界测试。
5. **Regression**: 运行 Architecture/FP-Java/Maintainability、完整后端测试、前端 build、pre-push gate。
6. **Test deployment**: 按 `xiyu-server-deploy` SOP 部署测试环境；启动 preview 必须显示候选 274、未来 324 不入选，差异即停止。
7. **09:00 acceptance**: 核对新增补发通知数、接收关系数、企微异步结果、旧 reminder log/event 状态不变；立即再 preview 应显示可新增 0。

## Complexity Tracking

无 Constitution 例外。一次性代码保留在独立 job/service，完成测试与未来生产补发后可整体删除；持久审计事实仍留在既有通知表。
