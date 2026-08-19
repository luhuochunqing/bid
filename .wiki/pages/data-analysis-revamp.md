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

### 5. 部门树形层级与无效部门展示（M1）

- 组织架构具有多层级树形结构，通过 `parentDepartmentCode` 递归构建 `AnalyticsDepartmentTreeDTO`。
- 业务规范：数据分析筛选区的部门与人员不过滤无效/停用状态，确保历史项目数据能够完整按历史归属组织进行归类与统计。

### 6. 区域维度总部所在地一级地址匹配（M1）

- 项目详情中的 `headquartersLocation` 字段通常存有省级+市级拼接字符串（如“广东省深圳市”）。
- 趋势分析按“区域”维度聚合时，后端通过 SQL/JPQL `CASE` 表达式提取一级行政区（省/直辖市/自治区/特别行政区），与前端省份选项匹配。
- 下钻查询与通用筛选时，针对 `headquartersLocation` 需采用前缀模糊匹配（`LIKE '广东省%'`），避免精确 `IN` 匹配丢失下属城市数据。

### 7. 招标主体与项目模式竞品公司动态展示（M4）

- 招标主体模式下，竞品公司不强制受限于预设的 24 个枚举名单，而是动态收集所选招标主体项目下实际存在的全部竞品公司，生成对应的最高/最低/平均折扣柱子。
- 项目模式下直接提取项目“结果确认阶段-竞争对手情况”中的全部竞品及折扣生成图表与明细表格。
- ECharts DOM 生命周期：骨架屏 `v-if="loading"` 切换会导致容器 DOM 销毁重建，在 `renderChart` 中需检验 `chartInstance.getDom()` 是否与当前 ref 对齐，发生脱节时需重新初始化。

## 文件清单

### 后端（analytics 包）

```
backend/src/main/java/com/xiyu/bid/analytics/
├── controller/
│   ├── CompetitorAnalysisController.java      # REST 端点 + /project-names
│   └── TrendAnalysisController.java           # 趋势分析与筛选选项端点
├── service/
│   ├── CompetitorAnalysisService.java          # 门面
│   ├── CompetitorAnalysisQueryService.java     # 数据访问（JPQL + 权限过滤）
│   ├── CompetitorAnalysisComputationService.java # 纯计算（折扣解析 + 统计）
│   ├── CompetitorAnalysisAssemblerService.java # 三模式响应组装
│   ├── FilterOptionsQueryService.java          # 筛选区选项（含部门树与人员）
│   ├── TrendAnalysisDimensionQueryService.java # 多维度聚合查询（含区域一级地址提取）
│   ├── TrendAnalysisService.java               # 趋势分析门面
│   └── TrendDrillDownService.java              # 趋势分析下钻查询
├── dto/
│   ├── AnalyticsDepartmentTreeDTO.java         # 部门树形 DTO
│   ├── CompetitorAnalysisRequest.java          # tenderEntities 列表 + projectName
│   ├── CompetitorAnalysisResponse.java         # 三模式响应
│   ├── CompetitorAnalysisSeriesDTO.java        # 默认模式 series
│   ├── CompetitorGroupDTO.java                 # 分组模式 min/avg/max
│   └── CompetitorTableRowDTO.java              # 项目模式表格行
└── model/
    ├── CompetitorAnalysisRow.java              # 竞品数据行 record
    └── CustomerTypeAggregate.java              # 客户类型聚合 record
```

### 前端（data-analysis）

```
src/views/data-analysis/
├── index.vue                                   # 页面入口（M0~M4 布局）
├── composables/useAnalyticsData.js             # 公共数据加载
└── components/
    ├── shared/TreeSelectDropdown.vue           # 树形下拉选择组件
    ├── m0-kpi-cards/index.vue                  # KPI 卡片
    ├── m1-trend-analysis/
    │   ├── index.vue                           # 趋势分析
    │   ├── FilterBar.vue                       # 筛选栏（含部门树选择）
    │   ├── DrillModal.vue                      # 钻取弹窗
    │   └── composables/useDrillDown.js         # 钻取逻辑
    ├── m2-customer-type/index.vue              # 客户类型饼图
    ├── m3-project-type/index.vue               # 项目类型饼图
    └── m4-competitor/
        ├── index.vue                           # 竞品分析
        ├── composables/useCompetitorData.js    # 状态管理（含 DOM 重新挂载）
        └── chartRenderer.js                    # ECharts 配置 + 导出
```

## 验证证据

- `npm run build` — exit 0
- `npm run check:line-budgets` — 全部 ≤ 300 行
- pre-commit 钩子：208 test files, 1700 tests passed
- 架构测试：MaintainabilityArchitectureTest + FPJavaArchitectureTest + ProjectAccessGuardCoverageTest 12 tests passed

