# Analytics Phase 3 收口执行计划（2026-03-18）

## 目标

收口 `DashboardAnalyticsService` 中剩余 4 条高风险 analytics 路径，移除全量 `findAll()` 主实现、循环查库与明显 N+1，并补齐测试以证明查询次数下降。

范围限定：

1. `getProductLinePerformance()`
2. `getDrillDown(String type, String key)`
3. `getWinRateDrillDown(...)`
4. `getTeamDrillDown(...)`

## 标准作业流程

### 1. 规划

1. 明确每条路径当前的数据来源、聚合逻辑与 DTO 兼容要求
2. 区分可直接下沉到 repository 的统计逻辑，与必须在 service 层保留的派生逻辑
3. 预先定义“查询次数下降”的验证口径

### 2. 开发

1. 优先扩展 `DashboardAnalyticsRepository`
2. 对 4 条路径按“先测试、后实现、再收口”的顺序推进
3. 保持 controller、API 结构和响应字段兼容

### 3. 质检

1. 回归现有 analytics 集成测试
2. 新增或增强测试覆盖：
   - `/api/analytics/product-lines`
   - `/api/analytics/drill-down?type=trend`
   - `/api/analytics/drill-down?type=competitor`
   - `/api/analytics/drill-down?type=product`
   - `/api/analytics/drilldown/win-rate`
   - `/api/analytics/drilldown/team`
3. 用 Hibernate statistics 或 SQL statement count 证明查询次数下降

### 4. 维护

1. 删除已无必要的辅助查询路径
2. 收敛重复组装逻辑
3. 保留必要注释与风险说明

## 并行拆分

### Worker A：主实现重构

负责：

1. `DashboardAnalyticsRepository`
2. `DashboardAnalyticsService`

目标：

1. 以聚合查询和批量装载替代全量扫描
2. 保留 DTO 兼容
3. 不扩大到无关模块

### Worker B：测试与证明

负责：

1. analytics 集成测试
2. 查询次数下降的验证机制

目标：

1. 证明功能输出不回归
2. 证明关键路径查询次数下降
3. 不依赖外部 Redis 或额外基础设施

## 具体实施顺序

1. 先补测试骨架与查询统计工具
2. 再重构 `product-lines` 与 `drill-down`
3. 然后重构 `win-rate` 与 `team`
4. 最后合并并回跑整组验证

## 验收标准

### 功能验收

1. 4 条路径全部返回 200
2. 响应结构与关键字段语义保持兼容
3. 既有 analytics 集成测试保持通过

### 性能验收

1. 4 条路径不再以 `findAll()` 作为主实现入口
2. 不再在循环内逐条查询 `Project`、`Task`、`User`
3. 测试能够证明关键接口 SQL statement 数量下降

### 风险边界

1. 本轮不做前端契约调整
2. 本轮不引入新缓存策略
3. 本轮不处理索引上线与真实压测，仅为后续索引优化创造稳定查询结构
