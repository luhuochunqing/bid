# Implementation Plan: 标讯创建/详情表单字段修正

**Branch**: `codex/bidding-create` | **Date**: 2026-05-29 | **Spec**: specs/004-bidding-form-fix/spec.md

## Summary

对标讯创建表单（ManualTenderDialog.vue）和详情页（DetailPage.vue）进行5项字段修正：删除预算金额、标题改为项目名称、时间格式统一、客户类型选项扩充、来源平台创建时隐藏。

## Technical Context

**Language/Version**: JavaScript (Vue 3 Composition API)
**Primary Dependencies**: Element Plus, Vue 3, Vite 5
**Storage**: MySQL (后端不变)
**Testing**: Vitest (前端单元测试), Playwright (E2E)
**Target Platform**: Web (桌面端)

## Constitution Check

| Principle | Status |
|-----------|--------|
| I. FP-Java Architecture | N/A — 前端变更 |
| II. Real-API Only | PASS — 不涉及 mock |
| III. TDD | 需更新现有单元测试 |
| IV. Split-First | PASS — 修改限于单一组件 |
| V. Boring Patterns | PASS — 使用 Element Plus 标准组件 |

## Implementation Changes

### 1. 常量文件 (constants.js)
- 修改 `CUSTOMER_TYPE_OPTIONS` 为5项
- 修改 `createManualTenderForm()` 移除 budget 字段
- 修改 `MANUAL_FORM_RULES` 移除 budget 校验规则，更新 title 校验消息

### 2. 创建表单 (ManualTenderDialog.vue)
- 删除预算金额 el-col 区块（含 el-input-number 和 field-tip）
- 修改 label="标讯标题" → label="项目名称"
- 修改 placeholder → "请输入项目名称"
- 给 el-date-picker 添加 format="yyyy-MM-dd HH:mm" 和 value-format="yyyy-MM-dd HH:mm:ss"

### 3. 详情页 (DetailPage.vue)
- 修改 label="标题" → label="项目名称"
- 删除"预算金额" el-descriptions-item 行
- 将 formatTenderDate 改为 formatTenderDateTime 用于时间字段

### 4. Payload 构建 (helpers.js)
- buildManualTenderPayload 移除 budget 字段

## Test Plan

- 更新 constants.spec.js：验证 CUSTOMER_TYPE_OPTIONS 为5项，createManualTenderForm 不含 budget
- 更新 ManualTenderDialog.spec.js：验证标签文本和字段存在性
- 如有 Detail.spec.js，验证详情页标签

## Assumptions
- 后端 budget 列保留，仅前端移除展示
- el-date-picker value-format 使用 "yyyy-MM-dd HH:mm:ss" 保持与后端 LocalDateTime 兼容
- formatTenderDateTime 已存在于 bidding-utils.js
