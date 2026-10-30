---
title: 全局任务看板保证金任务放权与响应式更新
space: engineering
category: guide
tags: [任务看板, 保证金, 项目负责人, 响应式更新, TaskBoard, CO-574]
created: 2026-10-22
updated: 2026-10-22
health_checked: 2026-10-30
sources:
  - src/views/TaskBoard/TaskBoardPage.vue
  - src/views/TaskBoard/components/TaskBoardTaskDrawer.vue
  - src/views/TaskBoard/components/TaskBoardTaskDrawer.spec.js
  - src/views/TaskBoard/TaskBoardPage.spec.js
backlinks:
  - _index
---
# 全局任务看板保证金任务放权与响应式更新

> **一句话**：在全局任务看板（`/task-board`）中，保证金缴纳任务在 `TODO` 状态下允许项目立项发起人（`projectLeaderId`）修改执行人；打开任务抽屉时自动补全当前项目上下文，保存后通知看板卡片即时局部更新，无需 F5 刷新。

## 业务规则与权限口径

1. **权限一致性**：项目详情页与全局看板的任务抽屉对保证金缴纳任务（`deposit-payment` / `status === 'TODO'`）的执行人修改权限口径完全一致。除投标主管（`isBidManager`）、主投标负责人（`primaryLeadUserId`）、辅助负责人（`secondaryLeadUserId`）外，立项发起人（`projectLeaderId`）也具备编辑权限。
2. **上下文按需加载**：全局看板进入抽屉时默认无 `currentProject` 上下文。当抽屉加载任务（`loadTask`）时，若缺少所属项目详情，需通过 `projectStore.getProjectById(task.projectId)` 异步补齐，保证权限判定有法可依。
3. **即时局部更新**：执行人修改保存成功后，抽屉触发 `submitted` 事件向父组件（`TaskBoardPage`）传递更新数据；看板就地更新卡片模型（同步 `assigneeName`、`owner`、`assignee`、`assigneeId`），消除刷新浏览器才展示的问题，随后后台静默重新加载对齐。

## 致错因果链

1. **致错 1（权限漏判）**：`TaskBoardTaskDrawer.vue` 早期复制自简化版本，权限计算属性 `canEditDepositTaskAssignee` 仅判断了主/辅负责人，遗漏了 `projectLeaderId` 校验。
2. **致错 2（上下文缺失）**：用户从左侧菜单直接进入 `/task-board` 时，`projectStore.currentProject` 为 `null`，导致即使加上 `projectLeaderId` 也因读不到项目对象而被短路为 `false`。
3. **致错 3（视图响应断裂）**：抽屉保存只调用了 API，父组件看板未监听保存事件进行就地属性同步，必须等待全量刷新浏览器重新调用列表接口。

## 验证防护

- `TaskBoardTaskDrawer.spec.js`：覆盖项目负责人放权、普通无权成员拦截、抽屉打开自动加载项目详情逻辑。
- `TaskBoardPage.spec.js`：覆盖保存后 `@submitted` 事件捕获并即时原地更新看板卡片上的执行人姓名。
