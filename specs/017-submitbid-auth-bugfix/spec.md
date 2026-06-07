# Feature Specification: submitBid 权限校验修复 & 审核人页面隐藏完成投标

**Feature Branch**: `017-submitbid-auth-bugfix`

**Created**: 2026-06-07

**Status**: Draft

**Input**: User description: "PR #278 submitBid 后端角色校验未修复好，提交投标报错无权限；标书审核人页面需要隐藏【完成投标】区域"

## 问题分析

### 现状问题

1. **PR #278 引入 bug**：`ProjectDraftingService.submitBid()` 新增了业务角色校验，但依赖 `User.getRoleCode()` 方法，该方法在 `roleProfile` 为 null 时会回退到 `User.role` 枚举名的 lowercase，导致某些用户被错误拒绝。

2. **审核人页面隐藏完成投标**：前端第76行 `v-if="reviewState === 'approved' && perm.canSubmitBid"`，对于 `auditor` 角色 `canSubmitBid` 为 `false`，已正确隐藏。

### 范围边界

- **修复范围**：`ProjectDraftingService.submitBid()` 的角色校验逻辑
- **验证范围**：前端 DraftingStage 审核人页面完成投标区域显示/隐藏
- **不在范围**：其他 submitBid 流程（审核状态校验、任务闸门校验）

## 测试场景

### 后端角色校验

| 场景 | roleProfile.code | 预期结果 |
|---|---|---|
| sales 提交投标 | "sales" | 放行 |
| bid_admin 提交投标 | "bid_admin" | 放行 |
| bid_lead 提交投标 | "bid_lead" | 放行 |
| auditor 提交投标 | "auditor" | 403 拒绝 |
| task_executor 提交投标 | "task_executor" | 403 拒绝 |
| 无 roleProfile 的用户提交投标 | null | 403 拒绝（不回退到 User.role 枚举） |

### 前端完成投标区域可见性

| 场景 | 角色 | reviewState | 完成投标区域 |
|---|---|---|---|
| auditor 查看审核通过的页面 | auditor | approved | 不可见 |
| sales 查看审核通过的页面 | sales | approved | 可见 |
