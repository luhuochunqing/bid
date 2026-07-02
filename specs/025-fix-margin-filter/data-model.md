# Data Model: 保证金看板状态筛选修复

**Date**: 2026-07-02
**Status**: Complete
**Feature**: [spec.md](./spec.md)

## 概述

本次修复**不新增任何实体**，复用既有的 `Fee` 和 `ProjectInitiationDetails` 两张表。本文件说明数据链路如何形成派生表 `m`，以及修复点如何影响派生表的筛选行为。

## 既有实体（不改）

### Fee（保证金缴费记录）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| project_id | BIGINT | 关联项目 |
| fee_type | VARCHAR | 缴费类型，保证金场景固定为 `'BID_BOND'` |
| status | VARCHAR | 状态枚举：`PENDING`（待支付）/ `PAID`（已支付）/ `RETURNED`（已退回）/ `CANCELLED`（已取消） |
| fee_date | TIMESTAMP | 缴费日期（已支付时表示支付时间，用于派生表的 `exp_return_date`） |
| amount | DECIMAL | 金额 |

**来源**: [backend/src/main/java/com/xiyu/bid/fees/entity/Fee.java](../../backend/src/main/java/com/xiyu/bid/fees/entity/Fee.java)

### ProjectInitiationDetails（立项详情）

| 字段 | 类型 | 说明 |
|------|------|------|
| project_id | BIGINT | 关联项目（主键之一） |
| need_deposit | VARCHAR | 是否需要保证金：`'YES'` / `'NO'` |
| deposit_amount | DECIMAL | 保证金金额（> 0 时产生占位行） |

**来源**: `project_initiation_details` 表

## 派生表 m（UNION ALL，不改结构）

派生表 `m` 由两支 UNION ALL 构成，定义在 [MarginDerivedTableColumns.java](../../backend/src/main/java/com/xiyu/bid/resources/service/MarginDerivedTableColumns.java)：

### 分支 1: fees 分支（已缴纳的 BID_BOND）

```sql
SELECT
  f.project_id,
  f.status       AS status,           -- PENDING/PAID/RETURNED/CANCELLED
  f.fee_date     AS exp_return_date,  -- 有值
  f.amount,
  ... -- 其他列
FROM fees f
WHERE f.fee_type = 'BID_BOND'
```

### 分支 2: init 分支（立项未缴占位行）

```sql
SELECT
  pid.project_id,
  'PENDING'      AS status,           -- 字面量
  NULL           AS exp_return_date,  -- NULL
  pid.deposit_amount,
  ... -- 其他列
FROM project_initiation_details pid
WHERE pid.need_deposit = 'YES'
  AND pid.deposit_amount > 0
  AND NOT EXISTS (
    -- 排除已有非 CANCELLED 的 BID_BOND fee 的项目
    SELECT 1 FROM fees f2
    WHERE f2.project_id = pid.project_id
      AND f2.fee_type = 'BID_BOND'
      AND f2.status <> 'CANCELLED'
  )
```

## 修复影响：状态筛选谓词

派生表 `m` 的 `status` 列和 `exp_return_date` 列的取值组合：

| 来源 | m.status | m.exp_return_date | label() 标签 |
|------|----------|-------------------|--------------|
| fees 分支 | PENDING | 有值 | 取决于日期比较 |
| fees 分支 | PAID | 有值 | 取决于日期比较 |
| fees 分支 | RETURNED | 有值 | 已退回 |
| fees 分支 | CANCELLED | 有值 | 已退回 |
| init 分支 | PENDING（字面量） | NULL | 未到期 |

### 修复前的筛选谓词（Bug）

| 筛选值 | 谓词 | 漏掉的行 |
|--------|------|----------|
| PENDING | `m.status NOT IN ('RETURNED','CANCELLED') AND m.exp_return_date >= NOW()` | init 分支行（NULL >= NOW() 为 NULL/falsy） |
| RETURNED | `m.status = 'RETURNED'` | CANCELLED 行 |
| OVERDUE | `m.status NOT IN ('RETURNED','CANCELLED') AND m.exp_return_date < NOW()` | 无（正确） |

### 修复后的筛选谓词（对齐 label() 语义）

| 筛选值 | 谓词 | 覆盖的行 |
|--------|------|----------|
| PENDING | `m.status NOT IN ('RETURNED','CANCELLED') AND (m.exp_return_date IS NULL OR m.exp_return_date >= NOW())` | fees 分支未到期 + init 分支行 |
| RETURNED | `m.status IN ('RETURNED','CANCELLED')` | RETURNED + CANCELLED 行 |
| OVERDUE | `m.status NOT IN ('RETURNED','CANCELLED') AND m.exp_return_date < NOW()` | fees 分支已超期（不变） |

## 状态转移图（不改）

Fee 实体的状态转移保持不变：

```
PENDING ──pay──→ PAID ──return──→ RETURNED
   │
   └──cancel──→ CANCELLED
```

- `PENDING → PAID`：用户支付保证金（`FeeService.markPaid`）
- `PAID → RETURNED`：保证金退回（`FeeService.refund`）
- `PENDING → CANCELLED`：取消已发起的缴费（`FeeService.cancelFee`）

修复不触碰任何状态转移逻辑，只改读取端的筛选谓词。

## 验证规则（不改）

- `Fee.fee_type = 'BID_BOND'` 才会被纳入保证金派生表
- `ProjectInitiationDetails.need_deposit = 'YES' AND deposit_amount > 0` 才会产生占位行
- 一个项目在派生表中最多出现一行（fees 分支或 init 分支，互斥）

## 实体关系图（不改）

```
┌──────────────────────────┐
│ ProjectInitiationDetails │
│  - project_id (PK)       │
│  - need_deposit           │
│  - deposit_amount         │
└──────────┬───────────────┘
           │ 1:1
           │
           ▼
      ┌─────────┐         ┌──────────────────┐
      │ Project │ 1:N     │ Fee              │
      │         │────────→│  - id (PK)       │
      └─────────┘         │  - project_id    │
                          │  - fee_type      │
                          │  - status        │
                          │  - fee_date      │
                          │  - amount        │
                          └──────────────────┘
```

## 结论

数据模型零改动。修复完全限定在 `MarginQuerySupport.appendStatusFilter` 方法的 3 个 case 分支谓词内，派生表结构、实体定义、状态转移、验证规则全部保持不变。
