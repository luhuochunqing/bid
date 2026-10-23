# Feature Specification: 标讯截止节点自动提醒

**Feature Branch**: `agent/codex/tender-auto-reminder`

**Created**: 2026-10-23

**Status**: Ready for PR

**Input**: 每条标讯自动提醒。报名截止提前 3 天、开标提前 3 天，从进入窗口起每天提醒一次。不用逐条配置通知人。

## User Scenarios

### User Story 1 - 无配置也提醒 (Priority: P1)

标讯写入报名截止或开标时间后，系统在截止前 72 小时开始，每天向投标管理员、投标组长、投标系统管理员发送一次企业微信提醒，直到该时间点过去。

**Acceptance Scenarios**:

1. **Given** 标讯没有提醒设置，报名截止在 24 小时后，**When** 定时任务运行，**Then** 向 `NOTIFICATION_RECIPIENT_ROLES` 的启用用户各发一条报名截止提醒。
2. **Given** 同一标讯开标时间在 72 小时窗口内，**When** 定时任务运行，**Then** 另发开标提醒。
3. **Given** 截止时间在 72 小时之外或已经过去，**When** 定时任务运行，**Then** 不发送。
4. **Given** 某接收人 24 小时内已成功收到同一标讯同一类型提醒，**When** 再次扫描，**Then** 跳过该接收人；满 24 小时且仍在窗口内则再发。
5. **Given** 该标讯已有提醒设置且 `enabled=false`，**When** 扫描，**Then** 不自动发送。
6. **Given** 该标讯已有启用的手工提醒设置，**When** 扫描，**Then** 自动路径不再发送，仍由原配置路径发送。

## Requirements

- **FR-001**: 无提醒设置的标讯，报名截止与开标各自按 72 小时窗口、24 小时去重自动发送。
- **FR-002**: 接收人实时解析为投标管理员（`/bidAdmin`）、投标组长（`bid-TeamLeader`）、投标系统管理员（`bid-SystemAdmin`）。本地 `admin` 不接收。
- **FR-003**: 已存在的提醒设置独占该标讯该类型：启用走原配置，关闭则不自动补发。
- **FR-004**: 终态标讯（`WON` / `LOST` / `ABANDONED`）不进入自动扫描。
- **FR-005**: 只有企微返回成功才写入 `SENT` 日志；跳过或失败不占用 24 小时去重，下一次扫描可再试。
- **FR-006**: 自动发送的日志 `reminder_setting_id` 为空。

## Assumptions

- 复用现有 `TenderReminderJob` 整点调度与 `WeComPushService`。
- 窗口与去重规则与 `TenderReminderPolicy` 一致。
- 不回填历史已过期节点。
