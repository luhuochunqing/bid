# 部门筛选两级展示与 X 轴口径

数据分析-多维度趋势分析-筛选区部门字段：

1. 下拉只展示一级、二级（三级及以下不进树）。后端 `fetchDepartmentTree` 截断，前端 `capDepartmentTreeDepth` 再挡一层。
2. 只选一级（可多个）→ X 轴按一级成柱；子孙（含三级）折进该一级。
3. 只选二级（可多个）→ X 轴按二级成柱；三级折进该二级。
4. 一级+部分二级 → 忽略该一级，只按勾选的二级成柱。
5. 全选 / 人员轴勾满可见树 / 某一级下全部二级都勾了 → 该一级完整，按一级成柱，一级直属人员计入（不走第 4 条）。

`expand` 跟有效勾选走。全选名单仍是前端提交的一级+二级全集，由 `axisNames` 判断「二级是否勾满」后收成一级，不改下拉勾选 UI。
接线仍是 `TrendAnalysisService` / `TrendDrillDownService` / `FilterOptionsQueryService.fetchDistinctPersons`。

## 有意不做

- 时间轴图表仍不消费部门筛选（存量缺口）
- 同名 / 跨 `source_app` 分区
- 不缓存 `Index`（等于把已删的 Prepared 请回来）
- 后端截树 + 前端 `capDepartmentTreeDepth` 两层都留：API 回归时下拉仍只到二级
