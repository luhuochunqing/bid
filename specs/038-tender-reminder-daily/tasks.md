---

description: "Task list for tender-reminder-daily feature"
---

# Tasks: 投标关键节点提醒改造（提前3天 + 每日重复）

**Input**: Design documents from `/specs/038-tender-reminder-daily/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, quickstart.md

**Tests**: 本次改造遵循 Constitution III (TDD)，测试先行。

**Organization**: US1（报名截止每日提醒）和 US2（开标每日提醒）共享同一代码路径（`shouldSendReminder` 不区分 ReminderType），合并到 Phase 3 一起实现。US3（默认值调整）独立在 Phase 4。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **后端**: `backend/src/main/java/com/xiyu/bid/tenderreminder/`、`backend/src/test/java/com/xiyu/bid/tenderreminder/`
- **数据库迁移**: `backend/src/main/resources/db/migration-mysql/`、`backend/src/main/resources/db/rollback/migration-mysql/`
- **前端**: `src/views/Bidding/list/components/`

---

## Phase 1: Setup (数据库迁移)

**Purpose**: 创建 Flyway 迁移脚本修改 DEFAULT 值

- [ ] T001 使用 `bash scripts/new-migration.sh tender_reminder_default_72h` 创建迁移脚本，生成 `backend/src/main/resources/db/migration-mysql/V<版本>__tender_reminder_default_72h.sql`，内容为 `ALTER TABLE tender_reminder_settings MODIFY COLUMN remind_before_hours INT DEFAULT 72 COMMENT '提前提醒小时数（默认72小时=3天）'`
- [ ] T002 [P] 创建回滚脚本 `backend/src/main/resources/db/rollback/migration-mysql/U<版本>__tender_reminder_default_72h.sql`，内容为 `ALTER TABLE tender_reminder_settings MODIFY COLUMN remind_before_hours INT DEFAULT 24 COMMENT '提前提醒小时数'`（版本号与 T001 一致）

---

## Phase 2: Foundational (TDD 核心去重逻辑改造)

**Purpose**: 修改 `shouldSendReminder` 去重逻辑从"只发一次"改为"每 24 小时发一次"，这是 US1/US2 共享的核心改动

**⚠️ CRITICAL**: US1/US2 的所有验收场景都依赖此 Phase 完成

### Tests (Red - 先写测试，确保 FAIL)

- [ ] T003 [P] [Red] 修改 `backend/src/test/java/com/xiyu/bid/tenderreminder/domain/TenderReminderPolicyTest.java`：
  - 修改用例 `shouldReturnFalseWhenAlreadyNotified` → `shouldReturnFalseWhenLastNotifiedWithin24Hours`（距上次发送 < 24 小时应返回 false）
  - 新增用例 `shouldReturnTrueWhenLastNotifiedAtLeast24HoursAgo`（距上次发送 ≥ 24 小时应返回 true）
  - 修改 `GetEffectiveRemindBeforeHoursTests.shouldReturnDefaultForInvalidInput` 断言：默认值 24 → 72
  - 新增用例 `shouldReturnDefaultRemindBeforeHoursAs72`（验证 `getDefaultRemindBeforeHours()` 返回 72）

### Implementation (Green - 实现使测试通过)

- [ ] T004 [Green] 修改 `backend/src/main/java/com/xiyu/bid/tenderreminder/domain/TenderReminderPolicy.java`：
  - `shouldSendReminder` 方法：将 `if (lastNotifiedAt != null) return false` 改为 `if (lastNotifiedAt != null && Duration.between(lastNotifiedAt, currentTime).toHours() < 24) return false`（添加 `import java.time.Duration`）
  - `getDefaultRemindBeforeHours` 方法：返回值 24 → 72
  - `getEffectiveRemindBeforeHours` 方法：fallback 值 24 → 72（通过调用 `getDefaultRemindBeforeHours()`）
  - `shouldSendReminder` 方法内 `hoursBefore` fallback：24 → 72（通过调用 `getEffectiveRemindBeforeHours`）
- [ ] T005 修改 `backend/src/main/java/com/xiyu/bid/tenderreminder/job/TenderReminderJob.java`：
  - `shouldSendReminder` 私有方法（第 155-166 行）：将 `if (setting.getLastNotifiedAt() != null) return false` 改为 `if (setting.getLastNotifiedAt() != null && Duration.between(setting.getLastNotifiedAt(), currentTime).toHours() < 24) return false`（添加 `import java.time.Duration`）
  - `shouldSendReminder` 方法内 `hoursBefore` fallback：24 → 72
  - `buildReminderContent` 方法内 `hours` fallback：24 → 72

**Checkpoint**: 运行 `mvn test -Dtest=TenderReminderPolicyTest` 应全绿（Red → Green 完成）

---

## Phase 3: User Story 1 + 2 - 报名截止 + 开标每日提醒 (Priority: P1) 🎯 MVP

**Goal**: 报名截止和开标提醒在触发窗口内每日发送一次，相邻两次间隔 ≥ 24 小时

**Independent Test**: 配置一个报名截止时间为 7 月 20 日 10:00 的标讯，系统应在 7 月 17 日 10:00 起每日发送一次企微提醒，7 月 20 日 10:00 后停止。开标提醒同理。

**说明**: US1 和 US2 共享 `shouldSendReminder` 代码路径（不区分 `ReminderType`），Phase 2 的核心逻辑改造已覆盖两者。本 Phase 仅需完成默认值调整（实体 + DTO），使新建提醒默认 72 小时。

### Implementation for User Story 1 + 2

- [ ] T006 [P] [US1] 修改 `backend/src/main/java/com/xiyu/bid/tenderreminder/entity/TenderReminderSetting.java`：`@Builder.Default private Integer remindBeforeHours = 24` → `= 72`
- [ ] T007 [P] [US1] 修改 `backend/src/main/java/com/xiyu/bid/tenderreminder/dto/CreateReminderRequest.java`：`@Builder.Default private Integer remindBeforeHours = 24` → `= 72`

**Checkpoint**: US1 + US2 实现完成。运行 `mvn test -Dtest=TenderReminderPolicyTest` 确认全绿。报名截止和开标提醒均在窗口内每日触发。

---

## Phase 4: User Story 3 - 新建提醒默认提前时间为3天 (Priority: P2)

**Goal**: 前端"添加提醒设置"对话框打开时，"提前提醒时间"字段默认显示"提前72小时（3天）"

**Independent Test**: 打开"添加提醒设置"对话框，"提前提醒时间"下拉框默认显示"提前72小时（3天）"

### Implementation for User Story 3

- [ ] T008 [P] [US3] 修改 `src/views/Bidding/list/components/useReminderSettings.js`：
  - 第 14 行 `form` 初始值：`remindBeforeHours: 24` → `remindBeforeHours: 72`
  - 第 62 行 `openCreateDialog` 函数内：`form.remindBeforeHours = 24` → `form.remindBeforeHours = 72`

**Checkpoint**: US3 实现完成。前端新建提醒对话框默认显示 72 小时。

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: 验证无回归，准备提交 PR

- [ ] T009 运行后端单元测试：`cd backend && mvn test -Dtest=TenderReminderPolicyTest`（确认全绿）
- [ ] T010 [P] 运行后端架构测试：`cd backend && mvn test -Dtest=ArchitectureTest,FPJavaArchitectureTest,MaintainabilityArchitectureTest`（确认无新增违规）
- [ ] T011 [P] 运行前端构建：`npm run build`（确认构建成功）
- [ ] T012 [P] 运行前端数据边界检查：`npm run check:front-data-boundaries && npm run check:doc-governance && npm run check:line-budgets`（确认全绿）
- [ ] T013 Git 状态确认：`git status` 检查只修改了授权文件（见 quickstart.md 第 8 步预期文件列表）
- [ ] T014 提交 PR（使用 Gitee MCP）：分支 `agent/claude/tender-reminder-daily-notify` → `main`，PR 描述包含 spec/plan/research 引用

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 无依赖，可立即开始。T001 → T002（T002 依赖 T001 的版本号）
- **Foundational (Phase 2)**: 无依赖（与 Phase 1 可并行）。T003 → T004 → T005（TDD 顺序：Red → Green → Job 同步）
- **User Story 1+2 (Phase 3)**: 依赖 Phase 2 完成（核心去重逻辑就绪）。T006、T007 可并行
- **User Story 3 (Phase 4)**: 无依赖（与 Phase 2/3 可并行）。T008 独立
- **Polish (Phase 5)**: 依赖 Phase 1-4 全部完成。T009 → T010/T011/T012（可并行）→ T013 → T014

### Within Each Phase

- Phase 2 严格遵循 TDD：T003（Red）→ T004（Green）→ T005（Job 同步）
- Phase 3/4 的默认值调整可并行（不同文件）

### Parallel Opportunities

- T002（回滚脚本）可与 T003（测试用例）并行（不同文件）
- T006（Entity）+ T007（DTO）+ T008（前端）可并行（不同文件，不同技术栈）
- T010（架构测试）+ T011（前端构建）+ T012（数据边界）可并行

---

## Parallel Example: Phase 3 + 4

```bash
# 默认值调整任务可并行（不同文件，无依赖）：
Task: "T006 [P] [US1] 修改 TenderReminderSetting.java @Builder.Default 24→72"
Task: "T007 [P] [US1] 修改 CreateReminderRequest.java @Builder.Default 24→72"
Task: "T008 [P] [US3] 修改 useReminderSettings.js 默认值 24→72"
```

---

## Implementation Strategy

### MVP First (Phase 1-3)

1. 完成 Phase 1: 创建迁移脚本
2. 完成 Phase 2: TDD 改造核心去重逻辑（Red → Green）
3. 完成 Phase 3: 默认值调整（Entity + DTO）
4. **STOP and VALIDATE**: 运行 `mvn test -Dtest=TenderReminderPolicyTest` 确认全绿
5. 此时 US1 + US2 已可交付（报名截止/开标每日提醒 + 默认 72 小时）

### Incremental Delivery

1. Phase 1-3 → 后端核心功能就绪（US1 + US2 + 后端部分 US3）
2. Phase 4 → 前端默认值就绪（US3 完整）
3. Phase 5 → 全量验证 + PR 提交

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- US1 和 US2 共享代码路径，合并在 Phase 3 实现
- 严格遵循 TDD：Phase 2 的 T003（Red）必须在 T004（Green）之前
- 每个任务完成后提交 atomic commit（遵循 RELIABILITY.md 原子提交 + 测试证据）
- 数据库迁移版本号由 `new-migration.sh` 自动获取，禁止手动猜测
