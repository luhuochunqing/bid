# 修复: TenderSyncController.resolveApiKeyUserId 异常处理过于宽泛

**标签**: `bug`

## 问题描述

`resolveApiKeyUserId()` 使用 `catch (Exception e)` 捕获所有异常，包括 NumberFormatException（principal 格式异常）、NPE 等，仅记录 `e.getMessage()` 不带堆栈，静默返回 null。

## 具体风险

1. API Key principal 格式异常（如 `api-key:abc`）→ `Long.parseLong` 抛 `NumberFormatException` → 被吞掉 → creatorId 为空，运维无法诊断
2. 任何编程错误（NPE 等）同样被吞掉

## 修复

- `NumberFormatException` 单独 catch，带 principal 原文日志
- `resolveUserIdByUsername` 异常单独 catch 带完整异常信息
