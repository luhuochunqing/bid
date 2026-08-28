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
