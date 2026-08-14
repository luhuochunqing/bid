---
title: 数据分析页面重构 — 架构与数据口径
space: engineering
category: architecture
tags: [analytics, data-analysis, m0-m4, competitor, echarts, fp-java]
created: 2026-08-14
updated: 2026-08-14
health_checked: 2026-08-14
sources:
  - .wiki/pages/data-analysis-revamp.md
backlinks:
  - _index
  - testing/module-06-analytics
---
# 数据分析页面重构 — 架构与数据口径

> 本页面记录 2026-08-14 数据分析页面全面重构（M0~M4）的架构决策、数据口径和关键陷阱。
> 分支：`agent/trae1/data-analysis-revamp`
> PRD 文档：https://my.feishu.cn/docx/ZiVMdeQR2oFLBcx0ic9cgIn8nSh
> 原型文件：数据分析模块-v4.html（桌面本地文件）

## 模块结构

| 模块 | 功能 | 前端 | 后端 |
|------|------|------|------|
| M0 | KPI 概览卡片 | `m0-kpi-cards/index.vue` | `OverviewController` 4 指标 |
| M1 | 多维趋势分析 | `m1-trend-analysis/index.vue` + `DrillModal.vue` | `TrendAnalysisController` |
| M2 | 客户类型分布 | `m2-customer-type/index.vue` | `CustomerTypeAnalyticsQueryService` |
| M3 | 项目类型分布 | `m3-project-type/index.vue` | `ProjectTypeAnalyticsQueryService` |
| M4 | 竞品对比分析 | `m4-competitor/index.vue` + composable | `CompetitorAnalysis*` 三层 |

## 关键架构决策

### 1. FP-Java 三层分离（M4 后端）

竞品分析后端严格遵循 FP-Java Contract：
- `CompetitorAnalysisQueryService` — 数据访问层（JPQL 查询 + 项目权限范围过滤）
- `CompetitorAnalysisComputationService` — 纯计算层（折扣解析 + min/avg/max 统计，无副作用）
- `CompetitorAnalysisAssemblerService` — 组装层（三模式响应组装）
- `CompetitorAnalysisService` — 门面层（委托给上述三层）

### 2. 三种分析模式（M4）

| 模式 | 触发条件 | 响应结构 |
|------|----------|----------|
| 默认 | 无招标主体、无项目名称 | `categories` + `series`（最低/平均/最高折扣） |
| 分组 | 勾选招标主体 | `groups`（每竞品 min/avg/max）+ `overallAvgLine` |
| 项目 | 输入项目名称 | `discounts` + `tableRows`（竞品明细表格） |

### 3. 前端 Composable 模式

每个模块拆分：`index.vue`（视图）+ `composables/useXxxData.js`（状态）+ `chartRenderer.js`（ECharts 配置）。
所有文件 ≤ 300 行（line-budget 硬门禁）。

## 数据口径要点（PRD 对齐）

### 时间过滤字段

M2/M3 时间过滤统一使用 `p.createdAt`（项目创建时间），**不是** `coalesce(p.startDate, p.createdAt)`。
PRD §6/§7 明确数据口径以项目创建时间为准。

### 折扣精度（M4）

折扣值从 `Integer` 改为 `Double`，支持小数精度。
`CompetitorAnalysisComputationService.parseDiscount()` 清除非数字字符后解析，`≤0` 返回 `null`。

### 纯数字显示（M4 表格）

PRD §9.15：表格折扣列只显示数字（不带百分号），账期列只显示天数。
前端 `parseDiscountValue` / `parsePaymentDays` 用正则提取纯数字。

## 关键陷阱

### 1. X 轴维度互斥（M1）

PRD §5：X 轴维度 checkbox 互斥，切换维度时清空旧选值。
部门-人员联动：选部门后人员列表过滤；清空部门时人员列表重置。

### 2. 招标主体 / 项目名称互斥（M4）

PRD §9.12 步骤 10/13：招标主体和项目名称互斥。
- 勾选招标主体 → 清空项目名称 + 关闭生成表格
- 勾选项目名称 → 清空招标主体
- 取消任一 → 切回默认模式（触发 `fetchData()`）

### 3. 竞品至少保留一个（M4）

PRD §9.12 步骤 7 + §9.14：竞品公司至少保留 1 个，不允许清空。
`onCompetitorChange` 检测空选值时恢复默认全选（`COMPETITOR_ENUM` 7 个竞品）。

### 4. 竞品枚举硬编码（M4）

PRD §9.4：竞品公司列表前端硬编码（震坤行/京东/阿里巴巴/米思米/固安捷/咸享国际/易买工品）。
不走后端枚举接口，避免不必要的服务端依赖。

## 文件清单

### 后端（analytics 包）

```
backend/src/main/java/com/xiyu/bid/analytics/
├── controller/
│   └── CompetitorAnalysisController.java      # REST 端点 + /project-names
├── service/
│   ├── CompetitorAnalysisService.java          # 门面
│   ├── CompetitorAnalysisQueryService.java     # 数据访问（JPQL + 权限过滤）
│   ├── CompetitorAnalysisComputationService.java # 纯计算（折扣解析 + 统计）
│   └── CompetitorAnalysisAssemblerService.java # 三模式响应组装
├── dto/
│   ├── CompetitorAnalysisRequest.java          # tenderEntities 列表 + projectName
│   ├── CompetitorAnalysisResponse.java         # 三模式响应
│   ├── CompetitorAnalysisSeriesDTO.java        # 默认模式 series
│   ├── CompetitorGroupDTO.java                 # 分组模式 min/avg/max
│   └── CompetitorTableRowDTO.java              # 项目模式表格行
└── model/
    └── CompetitorAnalysisRow.java              # 数据行 record
```

### 前端（data-analysis）

```
src/views/data-analysis/
├── index.vue                                   # 页面入口（M0~M4 布局）
├── composables/useAnalyticsData.js             # 公共数据加载
└── components/
    ├── m0-kpi-cards/index.vue                  # KPI 卡片
    ├── m1-trend-analysis/
    │   ├── index.vue                           # 趋势分析
    │   ├── DrillModal.vue                      # 钻取弹窗
    │   └── composables/useDrillDown.js         # 钻取逻辑
    ├── m2-customer-type/index.vue              # 客户类型饼图
    ├── m3-project-type/index.vue               # 项目类型饼图
    └── m4-competitor/
        ├── index.vue                           # 竞品分析
        ├── composables/useCompetitorData.js    # 状态管理
        └── chartRenderer.js                    # ECharts 配置 + 导出
```

## 验证证据

- `npm run build` — exit 0
- `npm run check:line-budgets` — 全部 ≤ 300 行
- pre-commit 钩子：202 test files, 1657 tests passed
- 架构测试：MaintainabilityArchitectureTest + FPJavaArchitectureTest + ProjectAccessGuardCoverageTest 12 tests passed
