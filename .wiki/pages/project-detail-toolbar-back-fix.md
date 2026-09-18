---
title: 项目详情页横条返回按钮无响应修复（el-page-header back 事件未接）
space: engineering
category: guide
tags: [项目详情, el-page-header, 返回按钮, 事件绑定, 前端修复]
created: 2026-09-16
updated: 2026-09-16
health_checked: 2026-09-18
sources:
  - src/components/project/detail/ProjectDetailHeader.vue
  - src/components/project/detail/ProjectDetailHeader.spec.js
  - src/composables/projectDetail/useProjectDetailNavigation.js
backlinks:
  - _index
---
# 项目详情页横条返回按钮无响应修复

> **一句话**：项目详情页基本信息卡上方的横条工具栏由 Element Plus `el-page-header`
> 渲染，其 `__back` 区域点击后 emit `back` 事件，但组件从未绑定 `@back` 监听器——
> 事件发出去无人接，点击无任何反应。修复：`@back="ctx.goBack"` 一行绑定。

## 现象

用户反馈：项目详情页，公共卡片（项目基本信息卡）上方的横条框中，点击"返回"按钮无反应。

## 根因

[ProjectDetailHeader.vue](../../../src/components/project/detail/ProjectDetailHeader.vue) 的模板：

```vue
<el-page-header class="page-header project-detail-toolbar">
```

Element Plus 2.5 的 el-page-header DOM 结构中，`el-page-header__back` 区域（含默认
"返回"标题文字，role=button）自带 onClick → `emit('back')` 协议（见
`node_modules/element-plus/es/components/page-header/src/page-header*.mjs` 的
`handleClick`）。组件模板未绑定 `@back`，emit 无人消费，点击自然无反应。

排查时曾疑似三个候选（全局 Header 返回按钮 / 详情横条 / CSS 遮挡），最终以读
EP 组件源码确认 DOM 结构与事件协议定位为未接事件，而非样式问题
（`.project-detail-toolbar .el-page-header__icon { display: none }` 只藏图标，
`__back` 的"返回"文字仍在渲染且可点）。

## 修复

```vue
<el-page-header class="page-header project-detail-toolbar" @back="ctx.goBack">
```

`ctx.goBack` 来自 Shell `provide` 的详情上下文（`...navigation` 展开），即
[useProjectDetailNavigation.js](../../../src/composables/projectDetail/useProjectDetailNavigation.js)
的 `router.push('/project')`——与页面错误态"返回项目列表"按钮同源同行为，
导航真相单点收口，未新增导航路径。

## 测试

- [ProjectDetailHeader.spec.js](../../../src/components/project/detail/ProjectDetailHeader.spec.js)
  新增回归用例：stub 模拟 EP 2.5 的 `__back` 可点击区域（带 `emits: ['back']` 声明），
  点击后断言 `goBack` 被调用 1 次。
- `beforeEach(vi.clearAllMocks)` 保证模块级共享 context mock 的调用计数不跨用例污染。

## 陷阱与边界

- **EP stub 默认不渲染 back 区域**：测试中 stub el-page-header 若只渲染 content/extra
  插槽，点击路径根本不存在，用例会假绿。stub 必须忠实复刻 `__back` + emit 协议。
- **`__icon` 被隐藏 ≠ back 区域不存在**：CSS 只藏了箭头图标，默认"返回"文字
  （`el.pageHeader.title` i18n）仍在 `__back` 内渲染且整体可点击。
- **全局 Header 的返回按钮与详情横条是两条独立链路**：前者走 MainLayout
  `handleBack`（按路径前缀回退），后者走详情 ctx `goBack`（固定回项目列表）。

## 关联

- 教训库：`docs/lessons/lessons-learned.md`（组件库事件协议以源码为准，勿凭 DOM 猜测）
- SOP：[[agent-sop-quickref]]
