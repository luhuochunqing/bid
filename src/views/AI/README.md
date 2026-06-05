# AI 模块

> 一旦我所属的文件夹有所变化，请更新我。

## 职责说明
AI 页面模块负责 AI 智能中心的页面编排和功能入口。
该目录只组织页面级工作流和面板布局，不承载底层 AI 提示词或组件库实现。
与 AI 功能相关的复用组件统一沉到 `components/` 子目录。

## 边界清单

| 文件 | 地位 | 功能 |
|------|------|------|
| `Center.vue` | View | AI 智能中心页面 |
| `components/` | 目录 | AI 页面局部组件边界 |
| `components/FeatureCard.vue` | Component | AI 功能卡片 |
