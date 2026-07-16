# Research: 投标关键节点提醒改造

**Date**: 2026-07-16
**Feature**: 038-tender-reminder-daily

## 研究问题

本次改造范围明确，spec 无 [NEEDS CLARIFICATION] 标记。本节记录关键技术决策的备选方案与选择理由。

## 决策 1：去重间隔选择 24 小时

**Decision**: 距上次发送 ≥ 24 小时才允许再次发送

**Rationale**:
- spec 要求"每天提醒一次"，24 小时是"每天"最自然的语义
- 调度频率为每小时整点，24 小时间隔确保在窗口内每日最多发送一次
- 不引入"固定钟点发送"（如每天 9:00）的复杂度，避免时区/夏令时问题

**Alternatives considered**:
1. **固定钟点发送**（如每天 9:00）：需引入时区处理、计算下一个钟点、处理当日已过钟点等逻辑。复杂度高，且用户可能在非工作时间收到提醒。拒绝。
2. **12 小时间隔**：每天发送 2 次，过于频繁，可能打扰用户。拒绝。
3. **可配置间隔**（用户可选 12/24/48 小时）：增加前端复杂度和数据库字段。spec 未要求，YAGNI。拒绝。
4. **保留"只发一次"但增加"提前提醒阶段"字段**（如提前 3 天发第一次、提前 1 天发第二次）：需新增数据库字段、前端配置、调度逻辑。改动大且不灵活。拒绝。

## 决策 2：默认值调整为 72 小时

**Decision**: 新建提醒的默认 `remindBeforeHours` 从 24 改为 72

**Rationale**:
- spec 明确要求"提前 3 天"
- 72 小时 = 3 天，是业务惯例
- 前端下拉选项已有 72，用户可手动改为其他值
- 存量数据不动，避免影响已有配置

**Alternatives considered**:
1. **强制 72 小时，禁止用户修改**：限制灵活性，且 spec FR-005 要求保留可选选项。拒绝。
2. **新增"3天"作为独立选项（而非 72 小时）**：前端已有"提前72小时（3天）"选项，无需新增。拒绝。
3. **数据库批量迁移存量数据为 72**：违反 spec FR-006（保留存量数据），且可能影响已配置 24 小时的用户预期。拒绝。

## 决策 3：存量数据兼容策略

**Decision**: 数据库迁移仅修改 DEFAULT 值，不 UPDATE 存量数据

**Rationale**:
- spec FR-006 明确要求保留存量数据
- 用户已配置的提醒设置反映其个人/团队偏好，不应被强制覆盖
- DEFAULT 值变更仅影响新建记录

**Alternatives considered**:
1. **UPDATE tender_reminder_settings SET remind_before_hours = 72 WHERE remind_before_hours = 24**：违反 spec FR-006，且可能让用户困惑（"我没改过，怎么变成 72 了"）。拒绝。
2. **新增字段 `is_default` 标记是否为默认值，迁移时只更新标记为默认的记录**：过度工程化，YAGNI。拒绝。

## 决策 4：去重逻辑实现位置

**Decision**: 同时修改 `TenderReminderPolicy.shouldSendReminder`（纯核心）和 `TenderReminderJob.shouldSendReminder`（Imperative Shell 中的私有方法）

**Rationale**:
- `TenderReminderPolicy` 是纯核心策略，有完整单元测试覆盖
- `TenderReminderJob` 中有独立的 `shouldSendReminder` 私有方法（重复实现，违反 DRY，但已存在）
- 为保持一致性和测试覆盖，两处都需修改
- 理想情况下应让 `TenderReminderJob` 调用 `TenderReminderPolicy.shouldSendReminder`，但本次改造范围不包含重构（YAGNI），仅保持两处逻辑一致

**Alternatives considered**:
1. **仅改 Policy，让 Job 调用 Policy**：需重构 `TenderReminderJob.shouldSendReminder` 方法签名（Job 中的方法接收 `TenderReminderSetting` + `LocalDateTime` + `LocalDateTime`，Policy 的方法接收 `TenderReminderSetting` + `LocalDateTime` + `LocalDateTime` + `LocalDateTime`）。参数差异导致改动扩大。拒绝，留作后续技术债。
2. **仅改 Job，不动 Policy**：Policy 有单元测试覆盖，不改则测试用例与实际行为不一致。拒绝。

## 决策 5：数据库迁移版本号

**Decision**: 使用 `scripts/new-migration.sh` 自动获取下一版本号

**Rationale**:
- CLAUDE.md 强制要求"严禁手动猜测或 `ls | tail` 决定版本号"
- `new-migration.sh` 内部调用 `next-migration-version.sh`，fetch remote + 本地取 max+1
- 避免并行开发撞号

**Alternatives considered**: 无。CLAUDE.md 已明确禁止手动猜测版本号。

## 决策 6：前端默认值修改位置

**Decision**: 修改 `useReminderSettings.js` 中的 `form.remindBeforeHours` 初始值和 `openCreateDialog` 中的重置值

**Rationale**:
- `ReminderSettingsDialog.vue` 的 `el-select` 通过 `v-model="form.remindBeforeHours"` 绑定，不硬编码默认值
- `useReminderSettings.js` 是 form 状态的唯一来源，在此修改确保一致性
- `openCreateDialog` 函数在新建时重置 form，需同步修改

**Alternatives considered**:
1. **在 `ReminderSettingsDialog.vue` 中添加 `:default-first-option`**：el-select 无此属性，且会破坏编辑场景的预填行为。拒绝。
2. **后端 DTO `@Builder.Default` 已改为 72，前端不传值时后端补默认**：前端表单仍显示 24，用户体验不一致（用户看到 24，保存后变 72）。拒绝。

## 总结

所有决策均符合 Constitution 原则（FP-Java 分层、TDD、Split-First、Boring Proven Patterns）。无 NEEDS CLARIFICATION 残留。可进入 Phase 1 设计。
