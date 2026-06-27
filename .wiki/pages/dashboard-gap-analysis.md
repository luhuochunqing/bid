---
title: 工作台卡片 vs 标书要求对照
space: engineering
category: business
tags: [工作台, dashboard, 标书, 需求追溯, 缺口分析, gap]
sources:
  - docs/research/COMMERCIAL_SCOPE.md
  - src/views/Dashboard/Workbench.vue
backlinks:
  - _index
  - design-system
created: 2026-04-16
updated: 2026-06-21
health_checked: 2026-06-27
---
# 工作台卡片 vs 标书要求对照

> 本页对照甲方标书要求与 `/dashboard` 工作台实际实现情况，
> 识别哪些卡片是标书明确要求的，以及当前的真实接通状态。

## 1. 标书对工作台的原文要求

**来源**：讲标文件 §1.1 统一项目工作台

> **标书要求**：为销售、投标团队、管理层提供角色化视图，集中展示个人待办任务、负责的项目及全局投标日历。

拆解为 5 项具体功能：

1. **角色化视图** — 不同角色看到不同内容
2. **待办任务中心** — 按优先级分类展示
3. **个人负责项目列表** — 活跃项目进度概览
4. **全局投标日历** — 截标日、开标日等关键节点
5. **客户跟进卡片** — 客户动态与跟进提醒

### 关联标书要求

| 来源 | 要求 |
|------|------|
| 讲标文件 §1.2 | 智能日程与预警：关键时间节点多级超前预警 |
| 实施计划书 §3.1 第 6 条 | 管理驾驶舱（年度/季度分析）、多维分析报表、数据穿透下钻 |
| 功能响应表 | 一站式流程发起：快速发起入口 |
| COMMERCIAL_SCOPE.md | Dashboard 白名单：真实聚合统计 + 非伪造的下钻数据 |
| 实施计划书第三阶段门禁 | 彻底清理 API 模式下的 Mock 数据硬编码路径 |

---

## 2. 逐卡片对照表

| # | 工作台卡片 | 标书要求 | 要求来源 | 数据来源 | API 端点 | 真实性 |
|---|-----------|---------|---------|---------|---------|--------|
| 1 | 顶部统计卡片 | **是** | 管理驾驶舱 + 投标数据看板 | `dashboardApi.getSummary()` | `GET /api/analytics/summary` | **真实** |
| 2 | 待审批 | **是** | 审批流程管理 | `approvalApi.getPendingApprovals()` | `GET /api/approvals/pending` | **真实** |
| 3 | 我的流程 | **是** | 审批流程管理 | `approvalApi.getMyApprovals()` | `GET /api/approvals/my` | **真实** |
| 4 | 角色化视图切换 | **是** | 标书原文 | 前端逻辑 | — | **真实** |
| 5 | 优先待办（任务 + 预警） | **是** | "集中展示个人待办任务" + "多级超前预警" | `tasksApi.getMine()` + `alertHistoryApi.getUnresolved()` | `GET /api/tasks/my` + `GET /api/alerts/history/unresolved` | **真实** |
| 6 | 投标日历 | **是（明确）** | 标书原文"全局投标日历" | `calendarApi.getMonthEvents()` | `GET /api/calendar/month/{year}/{month}` | **真实**，月份切换自动重载 |
| 7 | 项目列表 | **是（明确）** | 标书原文"个人负责项目列表" | `projectsApi.getList()` + `normalizeProjectForWorkbench()` | `GET /api/projects` | **真实** |
| 8 | 客户跟进 | **是（明确）** | 讲标文件"客户跟进卡片" | `extractCustomersFromProjects()` 从项目数据聚合 | 复用 `GET /api/projects` | **真实**（从项目数据派生） |
| 9 | 快捷操作按钮 | **是** | "一站式流程发起" | UI 导航 | — | **仅 UI**，部分按钮无真实目标 |
| 10 | 热门标讯 | 否 | — | `ref([])` | — | **空状态**（P2 降级） |
| 11 | 团队任务分配 | 间接 | "流程驱动与任务协同" | `ref([])` | — | **空状态**（P2 降级） |
| 12 | 我的技术任务 | 间接 | "待办任务"角色化展示 | `ref([])` | — | **空状态**（P2 降级） |
| 13 | 待审阅 | 间接 | "审批流程" | `ref([])` | — | **空状态**（P2 降级） |
| 14 | 团队绩效 | 否 | — | `ref([])` | — | **空状态**（P2 降级） |

---

## 3. 已完成修复（2026-04-16）

### P0 — 标书白纸黑字要求 ✅ 已修复

| 卡片 | 标书原文 | 修复内容 |
|------|---------|---------|
| **投标日历** | "全局投标日历" | 接通 `calendarApi.getMonthEvents()`，新增 `normalizeCalendarEvent()` 纯函数，`watch(calendarDate)` 月份切换自动重载 |
| **项目列表** | "个人负责项目列表" | 接通 `projectsApi.getList()`，新增 `normalizeProjectForWorkbench()` 纯函数，按角色过滤 |

### P1 — 标书要求 + 讲标承诺 ✅ 已修复

| 卡片 | 标书原文 | 修复内容 |
|------|---------|---------|
| **客户跟进** | "客户跟进卡片" | 从已加载项目数据聚合客户，新增 `extractCustomersFromProjects()` 纯函数 |
| **系统预警** | "多级超前预警" | 接通 `alertHistoryApi.getUnresolved()`，新增 `normalizeAlertForTodo()` 纯函数，支持 Spring Page 分页响应 |

### P2 — 非标书要求 ✅ 已降级

热门标讯、团队绩效、团队任务分配、我的技术任务、待审阅、最新动态、我的流程初始数据 — 硬编码假数据已全部清除，初始化为 `ref([])`，显示空状态。

### 代码质量改进

- 移除 `isMockMode()` 全部引用
- 移除 `if (true)` 死代码分支
- 修复 `formatCurrency` NaN 边界情况
- `handleTaskComplete` 改为服务端重载，不再直接修改 computed 对象
- 所有 normalizer 纯函数提取到 `workbench-utils.js`（38 条单元测试覆盖）

---

## 4. 门禁状态

实施计划书第三阶段门禁第 3 条：

> **彻底清理 API 模式下的 Mock 数据硬编码路径，确保数据链路真实性。**

**当前状态**：工作台所有标书要求的卡片（P0 + P1）已接通真实 API，P2 非标书要求的卡片已降级为空状态。**门禁风险已消除。**

---

## 5. 剩余待改进项

| 项目 | 优先级 | 说明 |
|------|--------|------|
| 角色切换 UI | P1 | 当前无角色切换器，角色由登录用户 JWT 决定；角色内容使用 `displayName` 精确匹配而非 `roleCode` |
| 快捷操作按钮 | P2 | 部分按钮（如费用申请）路由目标页面未完全实现 |
| P2 卡片接通 | P3 | 热门标讯可考虑后续接通 `/api/tenders`（数据库已有 5 条真实数据） |

---

## 6. 与其他 wiki 页面关联

- [[requirements]] — 需求追溯矩阵中工作台功能标记为"✅ 已实现"
- [[modules]] — 模块目录 §2.1 工作台描述
- [[business-process]] — 投标生命周期中"日历"和"预警"环节
