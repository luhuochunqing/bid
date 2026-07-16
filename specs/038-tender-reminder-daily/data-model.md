# Data Model: 投标关键节点提醒改造

**Date**: 2026-07-16
**Feature**: 038-tender-reminder-daily

## 概述

本次改造**不新增表、不新增字段、不修改字段类型**。仅修改已有表的 DEFAULT 值，并依赖已有字段实现新的去重逻辑。

## 已有表（无 schema 变更）

### tender_reminder_settings（提醒设置主表）

| 字段 | 类型 | 当前 DEFAULT | 改造后 DEFAULT | 说明 |
|---|---|---|---|---|
| id | BIGINT AUTO_INCREMENT | - | - | 主键 |
| tender_id | BIGINT NOT NULL | - | - | 标讯 ID |
| reminder_type | VARCHAR(50) NOT NULL | - | - | REGISTRATION_DEADLINE / BID_OPENING |
| **remind_before_hours** | INT | **24** | **72** | 提前提醒小时数（仅 DEFAULT 变更，字段类型不变） |
| reminder_targets | JSON | NULL | NULL | 接收人列表 |
| enabled | BOOLEAN | TRUE | TRUE | 启用状态 |
| **last_notified_at** | TIMESTAMP NULL | NULL | NULL | 最后通知时间（**语义变更**：从"是否发送过标记"变为"24 小时去重基准"） |
| created_by | BIGINT | NULL | NULL | 创建人 |
| created_at | TIMESTAMP | CURRENT_TIMESTAMP | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | CURRENT_TIMESTAMP ON UPDATE | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |

### tender_reminder_logs（提醒发送日志表，无变更）

沿用现有结构，记录每次发送的 `status`（SENT/FAILED/SKIPPED）和 `sent_at`。本次改造不修改此表。

## 字段语义变更说明

### last_notified_at

**改造前语义**：是否发送过的标记。`NULL` 表示未发送过，非 `NULL` 表示已发送过（`shouldSendReminder` 中 `lastNotifiedAt != null` 即跳过，永不重复发送）。

**改造后语义**：24 小时去重基准。`NULL` 表示从未发送，非 `NULL` 表示上次发送时间。`shouldSendReminder` 判断 `Duration.between(lastNotifiedAt, currentTime).toHours() >= 24` 时允许再次发送。

**影响**：
- 字段类型不变（TIMESTAMP NULL）
- 写入逻辑不变（每次发送后更新为 `currentTime`）
- 读取逻辑变更（从"非 NULL 即跳过"改为"距上次 < 24 小时才跳过"）
- 存量数据兼容：已有的 `lastNotifiedAt` 非空记录，在下次扫描时会按新逻辑判断（若距上次发送 ≥ 24 小时则重新开始每日提醒，若 < 24 小时则跳过）

## 迁移脚本

### 新增迁移：V<新版本>__tender_reminder_default_72h.sql

```sql
-- 修改 tender_reminder_settings.remind_before_hours 默认值为 72（3天）
-- 仅影响新建记录，不修改存量数据
ALTER TABLE tender_reminder_settings
    MODIFY COLUMN remind_before_hours INT DEFAULT 72 COMMENT '提前提醒小时数（默认72小时=3天）';
```

### 回滚脚本：U<新版本>__tender_reminder_default_72h.sql

```sql
-- 回滚：恢复默认值为 24
ALTER TABLE tender_reminder_settings
    MODIFY COLUMN remind_before_hours INT DEFAULT 24 COMMENT '提前提醒小时数';
```

## 实体映射变更

### TenderReminderSetting.java

```java
// 改造前
@Column(name = "remind_before_hours")
@Builder.Default
private Integer remindBeforeHours = 24;

// 改造后
@Column(name = "remind_before_hours")
@Builder.Default
private Integer remindBeforeHours = 72;
```

### CreateReminderRequest.java

```java
// 改造前
@Min(value = 1, message = "提前提醒小时数至少为1")
@Max(value = 168, message = "提前提醒小时数最多为168(7天)")
@Builder.Default
private Integer remindBeforeHours = 24;

// 改造后
@Min(value = 1, message = "提前提醒小时数至少为1")
@Max(value = 168, message = "提前提醒小时数最多为168(7天)")
@Builder.Default
private Integer remindBeforeHours = 72;
```

## 不涉及的数据变更

- ❌ 不新增表
- ❌ 不删除表
- ❌ 不新增字段
- ❌ 不删除字段
- ❌ 不修改字段类型
- ❌ 不 UPDATE 存量数据
- ❌ 不新增索引
- ❌ 不新增外键
