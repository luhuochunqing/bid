# 部门筛选两级展示与 X 轴口径

数据分析-多维度趋势分析-筛选区部门字段：

1. 下拉只展示一级、二级（三级及以下不进树）。后端 `fetchDepartmentTree` 截断，前端 `capDepartmentTreeDepth` 再挡一层。
2. 只选一级（可多个）→ X 轴按一级成柱；子孙（含三级）折进该一级。
3. 只选二级（可多个）→ X 轴按二级成柱；三级折进该二级。
4. 一级+二级混选 → 忽略一级，只按勾选的二级成柱。

`expand` 跟有效勾选走（混选时一级不进 WHERE / 人员联动 / 下钻范围）。
接线仍是 `TrendAnalysisService` / `TrendDrillDownService` / `FilterOptionsQueryService.fetchDistinctPersons`。

## 有意不做

- 时间轴图表仍不消费部门筛选（存量缺口）
- 同名 / 跨 `source_app` 分区
