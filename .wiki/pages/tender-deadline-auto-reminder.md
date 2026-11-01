---
title: 标讯截止节点自动提醒
space: engineering
category: feature
tags: [标讯, 提醒, 企微, 定时任务, 报名截止, 开标]
created: 2026-10-23
updated: 2026-10-23
health_checked: 2026-11-01
sources:
  - specs/075-tender-deadline-auto-reminder/spec.md
  - backend/src/main/java/com/xiyu/bid/tenderreminder/job/TenderAutoReminderScanner.java
  - backend/src/main/java/com/xiyu/bid/tenderreminder/job/TenderReminderJob.java
backlinks:
  - _index
---

# 标讯截止节点自动提醒

## 规则

- 没有 `tender_reminder_settings` 行的标讯，报名截止和开标各自自动提醒。
- 窗口是截止前 72 小时到截止时刻。进入窗口后每 24 小时一次。
- 接收人每次扫描实时解析：`/bidAdmin`、`bid-TeamLeader`、`bid-SystemAdmin`。不含本地 `admin`，不含全体 `bid-Team`。
- 该标讯该类型已有设置行时，自动路径不发。`enabled=false` 是关闭，不是改走自动。
- 终态 `WON` / `LOST` / `ABANDONED` 由现有标讯区间查询排除。
- 只有企微 `successful && !skipped` 才写 `tender_reminder_logs`，`reminder_setting_id` 为空，状态 `SENT`。跳过或失败不占 24 小时。
- 已过期的节点不回补。任务 cron 为每小时整点。

## 根因

- PR !2102（spec 038）只把已配置提醒从发一次改成每天，并把默认提前小时从 24 改成 72。
- `TenderReminderJob` 原先只读 `enabled=true` 的设置。生产库设置表和日志表都是 0 行，所以整点日志一直是 `processed=0, sent=0`。
- 企微通道本身在发其他通知。给单条标讯补配置不能覆盖以后的新标讯。

## 副作用

- V1221 把 `tender_reminder_logs.reminder_setting_id` 改成可空。回滚先把空值写成 0，0 不是真实设置行，再改回 NOT NULL 不会恢复 NULL。
- 自动扫描用独立事务。扫描失败不回滚同一小时已经写出的手工提醒。
- 手工配置路径仍不看推送结果就记 `SENT`。
