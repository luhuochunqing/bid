---
title: 评分项下拉短标签（ScoreItemSelect：短标签契约 / filterable 原文搜索陷阱 / 测试 stub 反模式）
space: engineering
category: guide
tags: [评分项, ScoreItemSelect, 短标签, tooltip, element-plus, 前端测试]
sources:
  - src/components/common/ScoreItemSelect.vue
  - src/utils/scoreItemOptionDisplay.js
  - src/views/Project/stages/components/AiRecommendDrawer.vue
  - src/views/Bidding/components/AICaseRecommendDrawer.vue
backlinks:
  - _index
  - ai-case-recommend-data-source-fix
  - frontend-pitfalls
created: 2026-08-28
updated: 2026-08-28
health_checked: 2026-08-28
---

# 评分项下拉短标签（ScoreItemSelect）

> **一句话**：两处 AI 案例推荐入口共用 `ScoreItemSelect`——选项只显示 `code + dim` 短标签，
> 标书原文走 tooltip + 选中后 aria-live 预览。本页记录显示契约、Element Plus filterable
> 陷阱、以及「defineExpose 只为单测服务」的审查反模式。

## 显示契约（防回归事实）

| 位置 | 内容 | 禁止 |
| --- | --- | --- |
| 选项行 `.score-option-line` | `{code} {dim}`（如 `3.1 技术评分`） | 拼 `detail` 原文（超长撑爆视口） |
| 选项 tooltip | 仅 `detail` 标书原文，`teleported` + `max-width:400px; max-height:240px` 滚动 | 重复编号/名称（短标签已有） |
| 选中后预览 `.score-item-detail-preview` | 常驻原文摘要，`aria-live="polite"`，`max-height:120px` | 回退成 hover-only（触控/键盘无 hover） |
| 下拉结构 | 扁平列表 | 按 `dim` 分组（dim 是名称不是类目，会出「组名=选项名」同文） |

显示规则共享于 `src/utils/scoreItemOptionDisplay.js`（`scoreItemOptionTitle` / `scoreItemOptionTooltip` / `scoreItemMatchesQuery`）。

## Element Plus filterable 陷阱

短标签后 EP 默认 filterable 只按 `label`（短标题）搜索，用户拿原文关键词（如「应急预案」）搜不到。
必须 `filter-method` + 自定义匹配 `code/dim/detail`，并在 `visible-change(false)` 时清空 query，
否则下次打开残留过滤。

## 审查反模式：defineExpose 只为单测服务

`defineExpose({ filterScoreItems, onScoreSelectVisible })` 仅被单测 `wrapper.vm.*` 调用 →
生产组件开了测试专用内部通道（双 reviewer HIGH）。正确做法：测试走 stub 的**公开契约**：

```js
// el-select stub：声明 filterMethod prop，DOM 钩子触发契约行为
'el-select': {
  props: ['modelValue', 'noDataText', 'filterMethod'],
  emits: ['update:modelValue', 'change', 'visible-change'],
  template: `<div class="el-select-stub" :data-no-data="noDataText"><slot />
    <input class="select-filter-input" @input="filterMethod && filterMethod($event.target.value)" />
    <button class="select-close-btn" @click="$emit('visible-change', false)" />
  </div>`
}
// 用例：find('.select-filter-input').setValue('应急预案') / find('.select-close-btn').trigger('click')
```

附带收益：`filter-method` / `visible-change` 与 el-select 的连线本身进入断言路径
（戳 vm 方法时这条连线是没测到的）。

## 样式 DOM 锁

招标侧宽度规则挂子组件根 class：`.item-score-select .score-item-select { flex: 1; min-width: 0 }`。
子组件改名会让 scoped 规则**静默失效**，父组件 spec 需挂载后断言该选择器存在。
`.item-score-select` 需 `align-items: flex-start` 覆盖 `.filter-item` 的 center——
否则「评分项：」标签垂直居中到「下拉 + 120px 预览」整块上。
