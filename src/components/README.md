# Components Directory (组件目录)

> 一旦我所属的文件夹有所变化，请更新我。

## 功能作用

存放可复用的 Vue 组件，按功能分类组织。

## 文件清单

| 子目录 | 内容 |
|--------|------|
| `layout/` | 布局组件 (MainLayout, Header, Sidebar) |
| `charts/` | 图表组件 (LineChart, BarChart, PieChart) |
| `common/` | 通用组件 (TaskBoard, AnimatedNumber, DynamicWorkflowForm) |
| `project/` | 项目详情组件 (TaskForm, ProjectTaskBoardCard 等) |
| `ai/` | AI 相关组件 (合规检查、版本管理等) |

## layout 组件

| 文件 | 功能 |
|------|------|
| `MainLayout.vue` | 主布局容器 |
| `Header.vue` | 顶部导航栏 |
| `Sidebar.vue` | 侧边菜单 |

## charts 组件

| 文件 | 功能 |
|------|------|
| `LineChart.vue` | 折线图 |
| `BarChart.vue` | 柱状图 |
| `PieChart.vue` | 饼图 |

## common 组件

| 文件 | 功能 |
|------|------|
| `TaskBoard.vue` | 任务看板 |
| `AnimatedNumber.vue` | 数字动画 |
| `DynamicWorkflowForm.vue` | schema 驱动流程表单，支持附件上传字段 |

## project 组件

| 文件 | 功能 |
|------|------|
| `TaskForm.vue` | 项目任务表单，负责人从组织候选人中选择并默认当前创建人，支持保存时上传任务附件和查看动态 Tab |
| `TaskActivityPanel.vue` | 任务动态面板，展示评论、历史快照并通过真实 API 发表评论 |
| `useTaskAssigneeOptions.js` | 任务负责人候选人加载、默认值和选择字段同步逻辑 |

## ai 组件

| 文件 | 功能 |
|------|------|
| `ComplianceCheck.vue` | 合规检查 |
| `VersionManager.vue` | 版本管理 |
| `CollaborationCenter.vue` | 协作中心 |
