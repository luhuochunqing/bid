---
title: 数据分析页面重构 — 架构与数据口径
space: engineering
category: architecture
tags: [analytics, data-analysis, m0-m4, competitor, echarts, fp-java]
created: 2026-08-14
updated: 2026-09-18
health_checked: 2026-09-18
sources:
  - .wiki/pages/data-analysis-revamp.md
backlinks:
  - _index
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

### 投标数口径（2026-08-19 修订，2026-09-18 补充 M0/M1 区分）

- M1 图表「投标数」= **全部项目**（全状态，含进行中/流标/弃标）；M1 图表「中标率」（2026-09-18 CO-615）= 中标数/(已中标+未中标)，与 M0 卡片一致，进行中/流标/弃标不计入分母。后端 `TrendAnalysisComputationService` 与下钻 `TrendDrillDownService` 同源锁定该口径。
- **口径变更排除项**：X 轴=项目状态（不展示中标率系列，winRateSeries 保持 win/bid）；X 轴=竞品公司（specs/055 专用口径：中标数=notes「是否中标」次数、中标率=中标数/该竞品参与项目数，禁止用项目 status 推导）。
- M0 卡片「投标数」（2026-09-18 由「投标总数」更名）= 投标中+评标中+已中标+未中标（4 状态，后端 `totalCount`）；M0 卡片「中标率」= 中标数/（已中标+未中标）。**两处同名不同口径**，前端分别用卡片底注、图例 tooltip 说明，禁止互相推导。
- 旧口径（仅 WON+LOST）导致：有总部所在地的项目多为进行中状态时，区域维度图表值全 0。
- 中标数 = WON 项目数，不变。下钻"投标数"系列不再追加 `status IN ('WON','LOST')` 过滤。

### 客户类型取值口径（2026-08-19 修订）

- 统一取值 `coalesce(nullif(pid.customerType, ''), p.customerType)`：**优先立项详情**（详情页可编辑值），回退项目主表创建时快照。
- 根因：立项表单只写 `project_initiation_details.customer_type`，从不同步 `projects.customer_type`，统计读主表导致与详情页展示分裂。
- 同步覆盖：M2 查询 / M1 customerType 维度与筛选 / M1 下钻 / M1 筛选选项（FilterOptionsQueryService）。
- **归一化口径补充（2026-08-20）**：coalesce 取回的是双源原始值（英文枚举/中文文本混杂），任何分组/筛选/下钻必须经 `CustomerTypeNormalizer.normalize()` 再比对，见关键陷阱 §12。

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

### 8. 项目模式请求不得被入口空检查拦截（M4，2026-08-19）

- `CompetitorAnalysisAssemblerService.analyze()` 入口原先对 `competitors` 为空的请求直接返回空响应；项目模式前端只传 `projectName` 不传竞品列表，请求被拦截导致永远"暂无数据"。
- 修复：入口判断改为 `(competitors 为空) && (非项目模式)` 才短路；项目模式按项目名定位，同时**忽略时间范围过滤**（`fetchProjectCompetitorRows` 无时间条件）——项目名下拉（`fetchProjectNames`）不带时间过滤，若查询带时间会造成"能搜到、查不到"的不一致。

### 9. 部门树形下拉的交互规范（M1，2026-08-19）

- 部门下拉（`shared/TreeSelectDropdown.vue` + `TreeSelectNode.vue`）必须与 `FilterSelect.vue` 交互一致：trigger 显示"请选择/全部/已选 N 项"+badge、面板含搜索框与全选/清空按钮。
- 部门下拉只展示一级和二级（三级及以下不进树）。勾选仍为 check-strictly。X 轴：只选一级按一级成柱，只选二级按二级成柱；一级+部分二级则忽略该一级。全选或某一级下全部可见二级都勾了（含人员轴自动勾满）视为该一级完整，按一级成柱，一级直属人员仍计入。纯函数见 `AnalyticsDepartmentHierarchySupport`。
- 树节点通过 provide/inject 共享选中集合与展开状态，递归组件 `TreeSelectNode` 无需逐层转发事件。

### 10. 竞品「是否中标」数据源 = notes 语义漂移字段（M4，2026-08-19）

- 项目详情「竞争对手情况」表格的「是否中标」下拉（枚举 `已中标/未中标`，定义于 `src/constants/competitors.js`）写入的是 `project_result_competitor.notes`——该列原名「其他说明」，前端语义漂移复用（`ResultConfirmStage.vue` L208 有注释）。
- 竞品分析任何按竞品维度取「是否中标」的功能必须读 `prc.notes` 并经 `parseCompetitorWon` 解析（仅「已中标」精确匹配为 true）；**禁止**用 `projects.status`/`ProjectResult.resultType`/`pid.bidResultStatus` 推导——那些是我司（项目级）的中标事实，会整列传染。
- 历史「其他说明」自由文本（非枚举）按未中标处理，与前端 select 显示为空的行为对齐。
- **2026-08-26 M1 对齐（specs/055）**：M1 多维度趋势分析竞品维度（图表 `computeCompetitorTrend` + 下钻 `TrendDrillDownService` 中标数过滤与 totalWins 汇总）对齐本口径；判定函数与「已中标」字面量提取至共享 support `CompetitorWonStatusSupport` 单一来源。投标数维持该竞品参与项目数不变，中标率语义=「该竞品对我司的中标率」。

### 11. 跨表字符集 Collation 与纯函数解耦（2026-08-19）

- **现象**：MySQL 中对不同 Collation 的列（如 `projects.customer_type` 与 `project_initiation_details.customer_type`）进行 `COALESCE` 时，返回表达式 Collation 会变为 `NONE`，导致后续 `!= ''` 或 `LIKE` 比较抛出 `Error 1267: Illegal mix of collations`，引发 500。
- **根治**：SQL 层取消跨表 COALESCE 比较，拆分为各自列的布尔过滤；区域省级提取剥离出 SQL 20 行 CASE 表达式，改由纯函数 `RegionDimensionExtractor.extractProvince()` 在 Java 内存计算，彻底免疫数据库字符集差异。

### 12. 客户类型双源字段"写入形态不一致"，任何消费方必须先归一化（2026-08-20）

- `project_initiation_details.customer_type` 存英文枚举名（`ProjectInitiationMapper.applyInput` 写 `enum.name()`），`projects.customer_type` 存中文文本——同一字段双源写入形态不一致，`coalesce(...)` 直匹配/原样分组必然在其中一个源上静默失效（X 轴英文 + 筛选漏统计）。
- 所有客户类型消费方（M1 分组/筛选/下钻/时间维度、M2 饼图）必须经同一纯函数入口 `CustomerTypeNormalizer.normalize()`（英文枚举/中文变体 → 5 标准分类，不匹配返回 null 不计数）；筛选无法下推 SQL 时改用"预计算命中 ID 集"模式（`AnalyticsCustomerTypeFilterSupport`，仿 `AnalyticsRegionFilterSupport`）。
- **新增任何客户类型消费方时禁止重新实现归一化或直接 SQL 匹配**，grep `CustomerTypeNormalizer` 找既有口径。

### 13. M4 折扣图并排口径与 0 占位值标签抑制（2026-08-27）

- 默认模式与招标主体分组模式的最低/平均/最高折扣柱为**并排**（无 `stack`）展示，y 轴固定 `min:0 max:100`（折扣百分比语义）；分组模式每柱顶置显示折扣数字。
- 后端 `CompetitorAnalysisAssemblerService` 对「该主体无此竞品数据」以 `0.0` 占位填充，前端标签必须经 `discountLabelFormatter`（0/null 返回空串）抑制贴轴“0”噪音——折扣业务值恒大于 0，0 仅为占位语义。
- 行为断言固化于 `chartRenderer.spec.js`（无 stack/标签/0 抑制/y 轴边界），后续改图表配置前先跑该 spec。

### 14. M4 竞品选项排序 = 参与项目数降序，默认选中前 8（2026-08-28）

- `/api/analytics/filter-options` 的 `competitor` 字段排序口径：快照侧按 `count(distinct projectId)` 降序（同数按名称序），字典独占（无参与记录）按名称自然序追加末尾；排名在 SQL `order by` 完成，Java 端不再按名称重排。
- 前端默认选中 = 选项前 8 个（`DEFAULT_TOP_COMPETITOR_COUNT = 8`），初始/重置/静态兜底三处口径一致；该字段仅 M4 消费（M1 竞品用前端常量），改排序不影响 M1。
- 排名遵守数据权限 scope（`AnalyticsProjectScopeSupport.scopedProjectIds`），不受日期范围筛选（累计口径）。
- 行为断言：后端 `FilterOptionsCompetitorNamesTest`（排名/补齐/去重/scope）+ 前端 `useCompetitorData.spec.js`（前 8/重置/不足 8 全选/兜底）。

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


## 2026-08-19 缺陷修复与口径补齐

为修复测试环境下的多维度统计、客户类型饼图及竞品表格问题，进行如下口径升级与缺陷修复：

### 1. 部门/人员联动兜底
- **背景**：测试环境可能未同步 OSS 组织架构数据，导致 `organization_departments` 表为空。
- **改动**：`FilterOptionsQueryService.fetchDepartmentTree()` 增加了 `users.department_name` 去重提取和自动合并。若组织表为空或存在孤立部门，将其平级补齐为根节点，确保任何环境下部门树均有值。前端未选择任何部门时，人员下拉恢复为加载全量有效人员。

### 2. 区域提取与 Left Join 改动
- **背景**：原区域提取使用 INNER JOIN `ProjectInitiationDetails` 且强过滤 `headquartersLocation`，导致未立项或总部所在地为空的项目被全部过滤。
- **改动**：改为 `left join`，区域提取与过滤参数化统一使用 `coalesce(nullif(pid.headquartersLocation, ''), t.region)`。优先提取立项总部，兜底回退到标讯区域，保证未立项项目也正常参与区域维度统计，解决柱状图数据全为 0 的问题。

### 3. 客户类型英文枚举映射补齐
- **背景**：数据库 `customer_type` 字段可能存储来自外部集成或标讯同步的英文枚举名（如 `GOVERNMENT`/`CENTRAL_SOE` 等）。原 `normalizeCustomerType` 仅支持中文字符，导致数据匹配失败归为 null 并在统计中过滤。
- **改动**：在 `CustomerTypeAnalyticsComputationService.normalizeCustomerType` 中全面追加英文枚举代码及中英文变体别名到 5 类标准分类（政府、央企、地方国企、民企、外企）的解析映射；`CustomerTypeAnalyticsQueryService` 采用多重 coalesce 兜底，解决饼图数字不显示且统计均为 0 的问题。

### 4. 竞品表格是否中标真实状态关联
- **背景**：原判定仅依赖结果确认的 `result_type == 'WON'`。若项目还未流转到结果确认阶段，但实际上项目状态 `projects.status` 已经为 `WON` 或立项详情中标状态 `pid.bid_result_status` 已经为 "已中标" 时被错误判定为未中标。
- **改动**：`fetchProjectCompetitorRows` 补充查询 `coalesce(nullif(pr.resultType, ''), nullif(pid.bidResultStatus, ''), cast(p.status as string))`，并由 `isWonStatus` 辅助方法综合判定项目是否中标，准确展现项目的最新中标状态。
- **⚠ 2026-08-19 修订（PR !2339 验收遗留）**：上述口径属**语义层级错配**——`resultType`/`status` 是项目级（我司）中标结果，不能推导每个竞品各自的中标状态，项目 WON 时整列误显「已中标」。正确数据源为 `project_result_competitor.notes`（结果确认「竞争对手情况」表格的「是否中标」列经前端语义漂移写入该字段，枚举 `已中标/未中标`）。现行实现：两条 JPQL 均带出 `prc.notes`，`parseCompetitorWon(notes)` 精确匹配「已中标」为 true，其余（含历史自由文本）为 false；`isWonStatus` 已删除。详见 lessons-learned §127。



### 5. 全库 collation 统一（V1198，2026-08-19）
- **背景**：上述第 2/3 条的跨表 `coalesce(...)` 表达式在测试环境触发 MySQL ERROR 1267（`Illegal mix of collations`）——历史表（`tenders`/`projects` 等 141 张）为 `utf8mb4_unicode_ci`，新表为库默认 `utf8mb4_0900_ai_ci`；不同 collation 列做 COALESCE 结果退化为 `utf8mb4_bin`+NONE，LIKE/<> 比较直接报错，多维度趋势「区域统计」500。
- **代码层**：PR !2347 消除区域维度跨表 COALESCE（改 Java 纯函数 `RegionDimensionExtractor`），但 customerType 维度仍残留跨表 coalesce。
- **数据层根治**：V1198 迁移用存储过程遍历 `information_schema.TABLES`，把全部非 `utf8mb4_0900_ai_ci` 基础表 CONVERT 对齐库默认（幂等）。测试环境演练：141 表/51MB，10.8s 完成，执行前已全量备份，原崩溃 SQL 复验通过。
- **教训**：collation/charset 属环境级配置漂移，本地与生产不一致时单测全绿也会线上炸；详见 lessons-learned §128/§129。

## 2026-08-20 区域筛选与分组口径统一（省份归一化）

## 2026-08-27 竞品下拉 label/value 格式修复（PR #2419 后续）
- **背景**：M1/M4 竞品公司下拉框从后端 `TrendAnalysisService.getFilterOptions()` 返回的 `TrendFilterOptionsResponseDTO.competitor` 字段（`List<AnalyticsFilterOptionDTO>`，含 `label/value` 字符串）前端映射错误，导致 `names.map((name) => ({ label: name, value: name }))` 中的 `name` 实际上是完整 DTO 对象而非字符串。结果造成 FilterSelect 显示 "label and value" 格式（或 "[object Object]"），并触发 "请求体格式错误"。
- **改动**：两处同等修正：`src/views/data-analysis/components/m1-trend-analysis/FilterBar.vue:107` 与 `src/views/data-analysis/components/m4-competitor/composables/useCompetitorData.js:282`，将 `names.map((name) => ...)` 改为 `names.map((dto) => ({ label: dto.label, value: dto.value }))`，确保正确提取 DTO 属性。
- **验证**：前端全量 vitest 1726 用例绿灯，后端 mvn test 39/39 通过，ArchitectureTest 32/32 通过。补测建议：在 `FilterOptionsCompetitorNamesTest` / `TrendAnalysisComputationServiceTest` 中加入映射正确性断言。

**2026-08-27 医疗健康检查更新**：同根因记录、口径边界确认。

- **背景**：多维度趋势分析选定区域筛选后，投标数/中标数 KPI 与图表柱子数量不一致。
- **根因（三口径分裂）**：①筛选 SQL 用 LIKE 前缀匹配原始文本，分组侧却做省份归一化提取，两端口径不同步；②区域取值空串回退不对称（一侧回退、一侧不回退）；③时间维度查询不消费 regionIds，筛选对时间趋势不生效。
- **修复方案**：筛选/分组/下钻/时间趋势四条链路统一走 `RegionDimensionExtractor`：`resolveRawLocation` 双源取值（`pid.headquartersLocation` 非空白优先，空白回退 `t.region`）+ `extractProvince` 省份归一（含 34 条短省名别名表 SHORT_NAME_ALIASES；「海南」等歧义短名仅精确匹配避免误归省份）。`AnalyticsRegionFilterSupport` 区域命中集 endDate 上界放宽至次日零点与下钻口径对齐；客户类型过滤统一 coalesce 口径堵住空串语义裂缝。
- **口径边界**：双源取值不含 `projects.region`（曾尝试三源口径，经确认本地数据缺失系 mock 数据无总部所在地所致，已回退）；纯市级自由文本（如「苏州市」）无法归省，需市→省映射表，另开任务。
- **验证**：175+ 定向测试全绿（Trend/Region/Analytics/DrillDown/ArchitectureTest）+ line-budget 门禁 + 三轮代码审查。分支 `agent/qoder/fix-trend-region-filter-stats`。

## 2026-08-20 客户类型分组/筛选/下钻归一化口径统一

- **背景**：M1 客户类型维度 X 轴显示 `CENTRAL_SOE/GOVERNMENT` 英文枚举名；筛选器选中文客户类型时已立项项目被静默漏统计。另有前端侧：`PROJECT_STATUS_OPTIONS` 缺 `PENDING_INITIATION/INITIATED`，projectStatus 维度 X 轴回退显示英文枚举名。
- **根因**：归一化逻辑只存在于 M2 计算服务私有方法（`CustomerTypeAnalyticsComputationService.normalizeCustomerType`），M1 趋势新链路漏接——分组原值透传、筛选 `coalesce(...) IN (中文值)` 对英文枚举行永远不命中。
- **修复方案**：
  - 抽取纯函数工具 `CustomerTypeNormalizer`（FP-Java 纯核心，无注入无 IO），M2 委托复用，M1 分组/筛选/下钻/时间维度（含 `TrendAnalysisQueryService` month/week）全部经同一入口；分组归一化为 null 的脏数据行丢弃（对齐 M2"未分类不计数"决策）。
  - 筛选改"归一化后 ID 集"过滤：新建 `AnalyticsCustomerTypeFilterSupport.customerTypeMatchedProjectIds()`（原生 SQL 取双源原始值 → Java 归一化匹配 → 收集 projectId），维度/时间/下钻（含 customerType 轴点击 axisValue）统一 `p.id IN :ids`，保证"筛选后数字 = 柱子之和"。
  - 前端：`filterConstants.js` 补 `待立项/已立项` 选项与着色；`useDrillDown.js` buildTitle 状态枚举转中文；M2 饼图扇区标签 `{b}\n{c}个 · {d}%` → `{b}\n{d}%`（tooltip 保留数量）。
- **口径边界**：中标率口径与下钻负责人兜底本次不动；非 5 分类脏数据不计数、不做 Flyway 数据订正。
- **验证**：`mvn test -Dtest='*Analytics*,*CustomerType*'` 65 例全绿（含英文枚举分组/筛选/下钻回归）+ `vitest run src/views/data-analysis` 4 例全绿。分支 `agent/qoder/fix-m1-trend-cn-labels`。
