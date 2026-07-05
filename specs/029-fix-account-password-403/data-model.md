# Data Model: 修复平台账号密码查看权限异常类型误用

**Date**: 2026-07-05

## 概述

本次修改 **不涉及任何数据模型变更**。

- 不新增实体
- 不修改现有实体（`PlatformAccount`、`User` 等）
- 不新增/修改/删除字段
- 不涉及 Flyway 迁移脚本
- 不涉及 repository 层改动

## 现有相关实体（仅说明，不修改）

### PlatformAccount

平台账号实体，包含加密密码字段。本次不修改其实体结构。

### User

当前操作用户实体，包含 `username` 和角色信息。本次不修改其实体结构。

### RoleProfile

角色配置实体。本次不修改其实体结构，仅依赖现有角色码 `admin` / `/bidAdmin` / `bid-TeamLeader` / `bid-Team` 的判定逻辑（已封装在 `PlatformAccountViewerPolicy` 中）。
