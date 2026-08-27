# 实现笔记：数据分析-多维度趋势分析 部门两级展示口径

> 任务：部门筛选保留两级，选了一级只展示一级，只要有二级就展示二级。
> 分支：`agent/claude/analytics-dept-two-level`（自 origin/main 拉起）
> 日期：2026-08-27

## 需求解读（用户已确认）

- 勾选**无二级**的部门 → 图表展示该部门自身一根柱
- 勾选**有二级**的一级 → 不保留一级汇总柱，按其二级部门逐根展示
- 下拉树本身维持 check-strictly（勾父不自动打勾子）交互不变
- X 轴未启用部门筛选时的展示行为完全不变（回归面控制）

## 方案与改动点

| 文件 | 改动 |
|---|---|
| `analytics/service/AnalyticsDepartmentHierarchySupport.java` | 新增纯函数支持类：`expandWithDescendants`（匹配展开=选中∪后代）、`displayBucketNames`（X 轴桶集合）、`bucketMapper`(用户部门名→桶标签)。以 departmentCode 为键建父子索引，name 仅作输出。零框架依赖可纯单测 |
| `TrendAnalysisService.java` | criteria 的 departmentIds 先展开；dept 轴行 category 经 bucketMapper 重映射后计算；expectedCategories 改为 displayBucketNames |
| `FilterOptionsQueryService.java` | 新增 `fetchDepartmentNodes()` 纯 record 数据源；`fetchDistinctPersons` 展开选中名集合，人员联动选项覆盖后代部门人员 |
| `TrendDrillDownService.java` | 部门筛选 IN 使用展开集；dept 轴+有选中时走 `appendDeptAxisFilter` 反解分桶标签为名称集合（图数与明细同口径）；未选中年份保留旧精确等值路径 |
| `TrendDrillDownFilterSupport.java` | 新增 `appendDeptAxisFilter` |

## 规格之外不得不做的决策

1. **被选父节点直挂用户的罕见脏数据**：该名称不在任何子桶内、又不允许造一级汇总柱，回退为「自身名自成桶」。保证图上合计 = 筛选范围数目守恒；代价是极罕见场景下图上仍可能出现一个与一级同名的小柱。
2. **同名部门防御**：树 value 是 name 且存在同名可能，层级索引全部走 code 计算；同名多处时深浅排序用最小深度。
3. **人员联动必须同步展开**：若只改统计不改 `/filter-options/persons`，会出现"图上有数、下拉里却没这个人"的新口径分裂。
4. **下钻反解**：axisValue 现在可能是分桶标签而非原始 departmentName（例如三级坍缩），等值匹配会漏；改为 mapper 反解后的名称集合 IN 匹配。
5. **嵌套选择（父子同时勾）**：深层选择优先——buildCoveredNameToBucket 按节点深度升序处理、深者后写生效。

## 既有行为变化（有意）

- wiki §9 记录过"勾父不级联、精确匹配"是当时的刻意决策；本次需求明确要求勾父包含后代数据，wiki §9 已同步更新。
- 全量测试证据：analytics 包 150 个测试全部通过（含新增 AnalyticsDepartmentHierarchySupportTest 8 个用例、TrendDrillDownFilterSupportTest 新增 dept 轴反解用例）。

## 踩坑记录

- 行数门禁 300 行：TrendDrillDownService 从 284 行起步，注入 FilterOptionsQueryService 复用 fetchDepartmentNodes 而非本类私有一个 loader 才压回 297 行。
- TrendQueryCriteria.departmentIds 是 List，expand 返回 Set，null 语义="筛选未启用"，不能.isEmpty() 替代判空。
