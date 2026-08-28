# Common 模块

> 一旦我所属的文件夹有所变化，请更新我。

## 职责说明
通用组件模块负责跨页面复用的基础交互组件。
该目录聚合弹窗、看板、占位态、图标等可复用部件，不承载具体业务流程。
业务页面需要优先复用这里的组件，避免重复造轮子。

## 边界清单

| 文件 | 地位 | 功能 |
|------|------|------|
| `AnimatedNumber.vue` | Component | 数字动画组件 |
| `ApprovalDialog.vue` | Component | 审批对话框 |
| `CommonIcon.vue` | Component | 通用图标封装 |
| `FeaturePlaceholder.vue` | Component | 功能占位态 |
| `MentionInput.vue` | Component | 支持 @ 提及同事的输入框，用户候选复用真实用户搜索 API |
| `TaskBoard.vue` | Component | 任务看板 |
| `ScoreItemSelect.vue` | Component | 评分项下拉：编号+名称短标签、原文 tooltip、选中后原文预览 |
| `UserPicker.vue` | Component | 通用用户选择器，支持搜索/候选人模式和姓名工号展示 |
| `icons.ts` | Utility | 图标映射定义 |
