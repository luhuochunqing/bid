# Implementation Plan: AI Solution Reuse Center

**Branch**: `007-solution-reuse` | **Date**: 2026-05-23 | **Spec**: [spec.md](spec.md)

## Summary

在 AI Center 新增"历史方案提取与复用"功能入口，提供搜索、预览、复制历史投标方案的能力。利用现有的 `casework` 后端案例库 API 作为数据源，复用 `AiRecommendDrawer` 的抽屉模式作为交互范式。

## Implementation

### Phase 1: AI Center Feature Card

**File: `src/views/AI/Center.vue`**
- 在"标书编制"Tab 中新增第 3 个 FeatureCard："历史方案提取与复用"
- Key: `solution-reuse`, icon: 文档+搜索
- 点击跳转到独立页面 `/ai-center/solution-reuse`

### Phase 2: Solution Reuse Page

**New file: `src/views/AI/SolutionReuse.vue`**
- 搜索栏：关键词输入 + 搜索按钮
- 筛选区：行业下拉、项目类型下拉
- 结果列表：方案名称、来源项目、行业、日期、匹配度标签
- 点击结果 → 打开详情抽屉（复用 AiRecommendDrawer 模式）
- 详情抽屉显示完整内容 + "复制内容"按钮

### Phase 3: Route & Menu

**File: `src/router/index.js`**
- 新增路由 `/ai-center/solution-reuse` → SolutionReuse.vue（作为 `/ai-center` 的子路由）

### Files to Modify

| File | Change |
|------|--------|
| `src/views/AI/Center.vue` | Add feature card entry for solution-reuse |
| `src/config/ai-prompts.js` | Add config entry for solution-reuse capability |
| `src/router/index.js` | Add solution-reuse route |

### Files to Create

| File | Purpose |
|------|---------|
| `src/views/AI/SolutionReuse.vue` | Main search + browse page |
| `src/views/AI/components/SolutionReuseDrawer.vue` | Detail drawer for previewing content |

## Verification
1. `npm run build` 通过
2. AI Center 标书编制 Tab 显示"历史方案提取与复用"卡片
3. 点击卡片导航到搜索页面
4. 搜索显示结果，点击查看详情，复制内容可用
