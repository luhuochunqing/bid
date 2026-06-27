---
title: 工作台 — 蓝图功能实现对照
space: engineering
category: testing
tags: [testing, 蓝图对照, 工作台, dashboard]
sources:
  - .wiki/sources/testing/module-01-workbench-test.md
  - .wiki/sources/testing/module-01-workbench-test.md
backlinks:
  - _index
created: 2026-05-28
updated: 2026-06-21
health_checked: 2026-06-27
---
> 蓝图章节：§4.1 工作台
> 对应飞书蓝图：https://my.feishu.cn/docx/FgLAdRmFho4QhwxncgAcfxKJn0d

## 覆盖度总览

| 蓝图功能 | 实现状态 | 测试方式 | 覆盖情况 |
|---------|---------|---------|---------|
| 指标卡片 | ✅ 已完成 | E2E + API | `e2e/dashboard-metric-drilldown.spec.js` |
| 快捷入口 | ✅ 已完成 | 手动 | 所有角色 Bannner 按钮有有效路由，处理待办路由跳转，Dialog 提交后导航到项目页 |
| 日程日历 | ✅ 已完成 | E2E + API | `GET /api/workbench/schedule-overview` 真实数据 |
| 我的待办 | ✅ 已完成 | API | 任务/预警/审批 4 独立端点 |
| 进行中项目 | ✅ 已完成 | API + E2E | `GET /api/projects` 真实数据 |
| AI 商机预测 | ⬜ 规划中 | — | 蓝图标明"规划中" |

## 功能 1：指标卡片

### 蓝图要求
分权限展示本日、本周、本月的报名截止、开标时间、保证金缴纳截止等关键节点的项目数量。

### 实现说明
- 前端：`src/views/Dashboard/useWorkbenchMetrics.js` — 指标数据获取与格式化
- 后端：`GET /api/analytics/summary` — 返回关键统计数据
- 角色化：`workbench-role-core.js` 根据角色过滤指标可见性

### 测试方式
API 测试 + E2E

### 测试示例
```bash
# 登录并获取 token
TOKEN=$(curl -s -X POST 'http://127.0.0.1:18081/api/auth/login' \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"XiyuAdmin2026!"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['token'])")

# 获取汇总统计
curl -s 'http://127.0.0.1:18081/api/analytics/summary' \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
```

## 功能 2：快捷入口

### 蓝图要求
快速发起项目、查看标讯、处理待办。

### 实现说明
- 前端：`src/views/Dashboard/useWorkbenchQuickStart.js` + `workbench-quick-start-core.js`
- 后端：多个独立 API（项目、资质、合同、费用）
- 角色化：不同角色显示不同快捷入口
- 2026-05-23 修复：Staff banner "我的任务"→`/project`、"日程"→`/dashboard?tab=schedule`；"处理待办"改为路由跳转；Dialog 提交后导航到项目页

### 测试方式
手动测试 — 以不同角色登录查看快捷入口差异

### 测试示例
```bash
# 验证 Staff 角色 Banner 可点击
# 登录 staff/Test@123 后：
# 1. 点击「我的任务」→ 应跳转到 /project
# 2. 点击「日程」→ 应跳转到 /dashboard?tab=schedule

# 验证处理待办
# 1. 点击「处理待办」→ 应跳转到 /project?tab=todo

# 验证 Dialog 提交后路由
# 1. 点击任一快捷入口（标书支持/资质借阅/费用申请）
# 2. 填写表单并提交
# 3. 提交成功后应跳转到 /project
```

## 功能 3：日程日历

### 蓝图要求
投标关键节点、项目里程碑可视化。

### 实现说明
- 前端：`src/views/Dashboard/workbench-calendar-core.js` + `useWorkbenchSchedule.js`
- 后端：
  - `GET /api/workbench/schedule-overview` — 日程概览
  - `GET /api/workbench/deadline-stats` — 截止节点统计
- 月份切换自动重载

### 测试方式
API 测试

### 测试示例
```bash
TOKEN=... # 同上

# 获取日程概览
curl -s 'http://127.0.0.1:18081/api/workbench/schedule-overview' \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool

# 获取截止节点统计
curl -s 'http://127.0.0.1:18081/api/workbench/deadline-stats' \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
```

## 功能 4：我的待办

### 蓝图要求
任务待办、任务审核、标书评审、流程审批。

### 实现说明
多个独立端点聚合展示：
- `GET /api/tasks/my` — 我的任务
- `GET /api/approvals/pending` — 待审批
- `GET /api/alerts/history/unresolved` — 未处理预警
- 前端：`useWorkbenchTodos.js` + `useWorkbenchApprovals.js`

### 测试方式
API 测试

### 测试示例
```bash
TOKEN=...

# 我的任务
curl -s 'http://127.0.0.1:18081/api/tasks/my' \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool

# 待审批
curl -s 'http://127.0.0.1:18081/api/approvals/pending' \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
```

## 功能 5：进行中项目

### 蓝图要求
项目进度一览，个人负责的活跃项目列表。

### 实现说明
- 前端：`useWorkbenchDerivedLists.js` 从项目数据派生
- 后端：`GET /api/projects` — 项目列表
- `normalizeProjectForWorkbench()` 纯函数格式化

### 测试方式
API 测试

### 测试示例
```bash
TOKEN=...

curl -s 'http://127.0.0.1:18081/api/projects?page=0&size=10' \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
```

## 功能 6：AI 商机预测

### 蓝图要求
存量客户招标窗口期预测。

### 状态说明
蓝图中已明确标注为"规划中"，当前未对应实现。后续排期进入 AI 能力阶段后实现。

---

## 相关文件
- E2E: `e2e/dashboard-metric-drilldown.spec.js`
- Wiki: `.wiki/pages/dashboard-gap-analysis.md`（工作台缺口分析）
