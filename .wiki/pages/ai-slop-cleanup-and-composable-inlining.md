---
title: AI Slop 代码清理与单组件专属小型 Composable 内联收敛
space: engineering
category: refactoring
tags: [refactoring, ai-slop, dead-code, composable, code-health]
created: 2026-08-31
updated: 2026-08-31
health_checked: 2026-08-31
sources:
  - src/views/Project/stages/DraftingStage.vue
  - src/views/Project/stages/InitiationStage.vue
  - src/views/Project/stages/ClosureStage.vue
  - src/views/Project/stages/RetrospectiveStage.vue
  - src/views/Knowledge/BrandAuth.vue
  - src/views/Knowledge/Qualification.vue
  - src/views/Resource/Account.vue
  - src/views/Resource/components/CAFormDialog.vue
backlinks:
  - _index
---

# AI Slop 代码清理与单组件专属小型 Composable 内联收敛

## 背景与问题

随着 AI 快速生成与迭代代码，容易积累两类典型 AI Slop（冗余代码与过度抽象）：
1. **死代码与死导入（Dead Code / Unused Imports）**：功能演进（如上传改 OBS 直传后），旧的 `uploadHeaders`、`uploadUrl`、`getApiUrl`、`watch` 等未及时清理。
2. **过度工程化小型 Composable（Over-engineering Composables）**：将只有几十行、且仅在单一组件中使用的内部状态/行为拆分为外部 `.js` 文件（如 `useAccountRowActions.js`、`useAccountExport.js`、`useCaPasswordReveal.js`），增加了文件跳跃成本与维护复杂度。

---

## 治理与收敛策略

依据 `RULES.md §2.5 Composable 内联规则`：
- **内联条件**：当组合式函数满足「唯一引用」+「小型逻辑 $\le$ 80 行」+「无跨组件复用预期」时，必须直接内联在组件 `<script setup>` 中。
- **门禁约束**：`scripts/check-composable-placement.mjs` 在预提交与构建阶段进行自动检测。
- **清理结果**：
  - 彻底清理各 stage 与 knowledge 页面中的死导入及未绑定的计算属性；
  - 内联 `useAccountRowActions`、`useAccountExport` 到 `Account.vue`；
  - 内联 `useCaPasswordReveal` 到 `CAFormDialog.vue`；
  - 删除 4 个单组件专属冗余文件，全量通过 11 道前置门禁与单元测试。
