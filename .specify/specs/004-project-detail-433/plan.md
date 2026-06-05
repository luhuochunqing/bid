# Implementation Plan: 项目详情 §4.3.3 — 6 项差距补齐

**Spec**: [spec.md](spec.md)
**Created**: 2026-05-26

## Overview

基于蓝图 §4.3.3 差距分析，现有代码覆盖率约 80%。本计划聚焦 6 项具体差距的补齐。

## Work Packages

### WP-1: 评标子阶段选项补齐（低风险，纯前端）
**Files**: `EvaluationStatusPanel.vue`
**Change**: RESULT_OUT 选项补回到前端下拉中
**Test**: 验证下拉含 4 个选项，RESULT_OUT 可选可提交

### WP-2: 复盘流程亮点字段（低风险，前后端）
**Backend**: `ProjectRetrospective` entity 新增 `processHighlights` 字段  
**Migration**: V1003 migration (ALTER TABLE project_retrospective ADD COLUMN)  
**Frontend**: `RetrospectiveStage.vue` 中标时显示"流程亮点"输入框  
**Test**: 后端单元测试 + 前端组件测试

### WP-3: 结果确认竞争对手情况（中风险，前后端）
**Backend**: `ProjectResult` entity 新增 `competitorsJson` JSON 字段  
**Migration**: V1004 migration  
**Frontend**: `ResultConfirmStage.vue` 新增竞争对手表格（3 行默认，可增删）  
**Test**: 提交/回显/空行处理

### WP-4: 评标时间线（高风险，前后端）
**Backend**: 
- `EvaluationTimelineEvent` entity (id, project_id, event_type, description, operator_id, created_at)  
- `EvaluationTimelineService` 插入/查询  
- 在 `EvaluationService.transitionSubStage()` 和 `attachEvidence()` 中自动记录事件  
- Migration: V1005  
**Frontend**: `EvaluationStage.vue` 新增时间线组件  
**Test**: 状态变更自动记录 + 文件上传自动记录 + 时间线展示

### WP-5: AI 生成复盘案例（中风险，后端 AI 集成）
**Backend**: 基于复盘数据调用 AI 生成案例草稿  
**Frontend**: `ClosureStage.vue` 新增"AI 生成复盘案例"按钮  
**Dependency**: 需确认现有 AI 接口是否可用

### WP-6: 项目文档导出（中风险，后端）
**Backend**: `ProjectExportService` 新增单项目导出方法  
**Frontend**: `ClosureStage.vue` 新增"导出项目总结"按钮

## Execution Order

```
Wave 1 (并行): WP-1 + WP-2  → 低风险，独立文件
Wave 2:         WP-3          → 中等风险
Wave 3:         WP-4          → 高风险，涉及新表
Wave 4:         WP-5 + WP-6   → 中风险，AI + 导出
```

## Gates

- Each WP must pass `npm run build` + `pnpm test:unit -- --run`
- Backend changes need `mvn compile -q` + `mvn test -Dtest=ArchitectureTest`
- Migration changes must have corresponding rollback script
