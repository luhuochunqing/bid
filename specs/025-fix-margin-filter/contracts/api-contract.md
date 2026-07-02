# API Contract: 保证金看板状态筛选修复

**Date**: 2026-07-02
**Status**: Complete
**Feature**: [spec.md](../spec.md)

## 概述

本次修复**不改变 API 契约**。HTTP 接口的请求参数、响应结构、状态码全部保持不变。本文件说明修复对 API 行为的影响（返回数据集的准确性提升）。

## 既有 API（不改）

### GET /api/resource/margin

**用途**: 获取保证金看板列表

**权限**: `@PreAuthorize("isAuthenticated()")`（不变）

**请求参数**（不改）:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| page | int | 否 | 页码，默认 0 |
| size | int | 否 | 每页条数，默认 20，最大 100 |
| status | String | 否 | 状态筛选：`PENDING`（未到期）/ `RETURNED`（已退回）/ `OVERDUE`（已超期） |
| projectName | String | 否 | 项目名称模糊筛选 |
| startDate | Date | 否 | 时间范围起 |
| endDate | Date | 否 | 时间范围止 |

**响应结构**（不改）:

```json
{
  "content": [
    {
      "projectId": 123,
      "projectName": "...",
      "status": "PAID",
      "statusLabel": "未到期",
      "amount": 50000.00,
      "expReturnDate": "2026-08-01",
      ...
    }
  ],
  "totalElements": 42,
  "totalPages": 3,
  "page": 0,
  "size": 20
}
```

## 修复影响：行为层契约（不改结构，改数据集正确性）

### 修复前（Bug 行为）

| 请求 | 预期返回 | 实际返回 |
|------|----------|----------|
| `?status=PENDING` | 所有"未到期"行（含 init 占位行） | 漏掉 init 占位行（`exp_return_date IS NULL` 被 `>= NOW()` 排除） |
| `?status=RETURNED` | 所有"已退回"行（RETURNED + CANCELLED） | 漏掉 CANCELLED 行（只匹配 `= 'RETURNED'`） |
| `?status=OVERDUE` | 所有"已超期"行 | 正确 |

### 修复后（正确行为）

| 请求 | 返回 |
|------|------|
| `?status=PENDING` | 所有 `status NOT IN ('RETURNED','CANCELLED')` 且 (`exp_return_date IS NULL` 或 `>= NOW()`) 的行 |
| `?status=RETURNED` | 所有 `status IN ('RETURNED','CANCELLED')` 的行 |
| `?status=OVERDUE` | 所有 `status NOT IN ('RETURNED','CANCELLED')` 且 `exp_return_date < NOW()` 的行（不变） |

## 一致性契约

修复后，以下不变式成立（对应 spec 的 SC-001/002/003）：

```
对于任意 status ∈ {PENDING, RETURNED, OVERDUE}:
  COUNT(未筛选时 statusLabel = X 的行)
  = COUNT(?status=X 筛选时返回的行)
```

即"标签 X 出现的行数" === "筛选 X 返回的行数"。

## 汇总接口（不改）

### GET /api/resource/margin/summary

**用途**: 获取保证金看板汇总数据（各状态计数）

**响应结构**（不改）:

```json
{
  "total": 100,
  "pending": 60,
  "returned": 20,
  "overdue": 20
}
```

**修复影响**: `summaryBase` 已经用 `NOT IN ('RETURNED','CANCELLED')` 定义"非退回"，汇总计数本身正确。修复只影响列表筛选，不影响汇总。汇总接口的 `returned` 字段已经包含 RETURNED + CANCELLED（与 label() 一致），无需改动。

## 不涉及的其他接口

- `POST /api/resource/margin`：新增保证金（不改）
- `PATCH /api/resource/margin/{id}/status`：更新状态（不改）
- `GET /api/resource/margin/export`：导出（不改，但导出会复用筛选逻辑，修复后导出数据也正确）

## 结论

API 契约零改动。修复只影响 `?status=PENDING` 和 `?status=RETURNED` 两个筛选分支的返回数据集正确性，HTTP 层面无任何变化。前端无需配合改动。
