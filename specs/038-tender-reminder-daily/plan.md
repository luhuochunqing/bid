# Implementation Plan: 投标关键节点提醒改造（提前3天 + 每日重复）

**Branch**: `agent/claude/tender-reminder-daily-notify` | **Date**: 2026-07-16 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/038-tender-reminder-daily/spec.md`

## Summary

将投标关键节点提醒（报名截止/开标）从"只发一次"改为"每 24 小时重复发送直到截止"，并将新建提醒的默认提前小时数从 24 调整为 72（3 天）。核心改动是 `TenderReminderPolicy.shouldSendReminder` 的去重判断逻辑：从"`lastNotifiedAt != null` 即跳过"改为"距上次发送 < 24 小时才跳过"。涉及后端 4 个 Java 文件 + 1 个 Flyway 迁移 + 1 个单元测试 + 前端 1 个 composable。

## Technical Context

**Language/Version**: Java 21（后端）+ JavaScript ES2022（前端 Vue 3）

**Primary Dependencies**: Spring Boot 3.2 + Spring Data JPA + Flyway + Lombok（后端）；Vue 3 + Vite 5 + Element Plus（前端）

**Storage**: MySQL 8.0（通过 Flyway 管理 schema，本次新增 V 版本迁移修改 `tender_reminder_settings.remind_before_hours` DEFAULT 值）

**Testing**: JUnit 5 + Mockito（后端单元测试，`TenderReminderPolicyTest`）；现有 E2E 不涉及（提醒由定时任务触发，E2E 难以模拟时间）

**Target Platform**: Linux server（后端 Spring Boot）+ 浏览器（前端 SPA）

**Project Type**: Web service（前后端分离，现有架构）

**Performance Goals**: 沿用现有（每小时整点扫描一次，单次扫描处理所有启用的提醒设置，无新性能要求）

**Constraints**: 沿用现有约束。本次改造不引入新依赖、不改调度频率、不改消息渠道

**Scale/Scope**: 改动 6 个文件（4 Java + 1 SQL + 1 JS）+ 1 个测试文件。新增 0 个文件（迁移脚本除外）。总改动行数约 50-80 行

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 状态 | 说明 |
|---|---|---|
| I. FP-Java Architecture | ✅ 通过 | `TenderReminderPolicy` 是 `record`（纯核心），`shouldSendReminder` 是纯函数。本次改造在 Policy 中修改纯函数逻辑，`TenderReminderJob`（Imperative Shell）调用 Policy。保持 Pure Core / Imperative Shell 分层 |
| II. Real-API Only | ✅ 通过 | 沿用真实企微推送链路（`WeComPushService`），无 Mock |
| III. Test-Driven Development | ✅ 通过 | 先更新 `TenderReminderPolicyTest` 测试用例（Red：新增"距上次发送 ≥ 24h 应发送"用例，修改"已发送过应返回 false"为"距上次发送 < 24h 应返回 false"），再改 Policy 实现（Green） |
| IV. Split-First & Simplicity | ✅ 通过 | 不新增文件，仅修改现有文件。所有文件均远低于 300 行硬上限（`TenderReminderJob` 当前 257 行，改动后约 260 行） |
| V. OSS Integration | N/A | 本次改造不涉及 OSS 集成 |
| VI. Authorization Unification | ✅ 通过 | `TenderReminderController` 已用 `@PreAuthorize("isAuthenticated()")`，本次不涉及权限变更 |
| VII. Defensive Collection & Graceful Degradation | N/A | 本次改造不涉及 `Collectors.toMap`、enrichment、5xx 异常 handler |
| VIII. Boring Proven Patterns | ✅ 通过 | 沿用 `String.format` 消息模板、`@Scheduled` cron、`WeComPushService` 推送链路，不引入模板引擎/消息队列/新框架 |

**结论**：无违规，无需 Complexity Tracking 表。

## Project Structure

### Documentation (this feature)

```text
specs/038-tender-reminder-daily/
├── plan.md              # 本文件
├── research.md          # Phase 0 研究输出
├── data-model.md        # Phase 1 数据模型变更
├── quickstart.md        # Phase 1 验证步骤
├── checklists/
│   └── requirements.md  # spec 质量检查清单
└── tasks.md             # Phase 2 输出（由 /speckit-tasks 创建）
```

### Source Code (repository root)

```text
backend/src/main/java/com/xiyu/bid/tenderreminder/
├── domain/
│   └── TenderReminderPolicy.java          # 修改 shouldSendReminder 去重逻辑 + 默认值 24→72
├── entity/
│   └── TenderReminderSetting.java         # 修改 @Builder.Default 24→72
├── dto/
│   └── CreateReminderRequest.java         # 修改 @Builder.Default 24→72
└── job/
    └── TenderReminderJob.java             # 修改 shouldSendReminder 去重逻辑 + 默认值 fallback 24→72

backend/src/main/resources/db/migration-mysql/
└── V<新版本>__tender_reminder_default_72h.sql  # 新增迁移：ALTER COLUMN DEFAULT 24→72

backend/src/test/java/com/xiyu/bid/tenderreminder/domain/
└── TenderReminderPolicyTest.java          # 更新测试用例（Red→Green）

src/views/Bidding/list/components/
└── useReminderSettings.js                 # 修改 form 默认值 + openCreateDialog 默认值 24→72
```

**Structure Decision**: 沿用现有 `tenderreminder` 模块结构（domain/entity/dto/job 分层），不新增包或文件。改动集中在已有文件的局部修改，符合"最小改动"原则。

## Complexity Tracking

> 无 Constitution 违规，本表为空。
