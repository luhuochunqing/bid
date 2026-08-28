# 部门两级统计口径（精简实现）

UI 仍为 check-strictly。后端只改统计口径，两个纯函数：

- `expand(selected)` = 勾选 ∪ 子孙（筛选 WHERE / 人员联动 / 下钻范围）
- `toBucket` / `bucketNames` 仅 `xAxis=dept`：有二级则按直接子部门成柱，三级折进二级

接线三处：`TrendAnalysisService`、`TrendDrillDownService`、`FilterOptionsQueryService.fetchDistinctPersons`。
组织树按需加载（有勾选才读），不新建 Gateway。

## 有意不做

- 时间轴图表仍不消费部门筛选（存量缺口，另开）
- 同名 / 跨 `source_app` 分区
- 父子同时勾选的「深者优先」
- 一级直挂人员的守恒小柱

下钻部门轴：`IN expand(selected)` 且 `IN expand(柱名)`。一级残留柱若被点到会把子孙算进去，接受。

## anti-slop（相对 472ed0da4）

保留 expand / toBucket / bucketNames 语义，不把已删的 Prepared/Gateway 加回来。

- 下钻树加载与人员联动共用 `FilterOptionsQueryService.loadDepartmentNodes()`，删掉 `TrendDrillDownService` 对 `OrganizationDepartmentRepository` 的重复映射。
- 删 `TrendAnalysisService.buildCriteria` 一次调用包装，以及未使用的 `DimensionRow` / `TimeDimensionRow` / `Map` import。
- `Index.of` 在 expand/bucketNames/toBucket 各建一次：组织树很小，缓存会重新引入刚删的 Prepared，有意不收。
- `FilterOptionsDepartmentTreeTest.loadDepartmentNodesSkipsDirtyRows` 锁 tree 映射跳过脏行（下钻与人员联动共用入口）。
