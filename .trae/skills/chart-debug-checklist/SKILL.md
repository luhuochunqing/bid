---
name: "chart-debug-checklist"
description: "当图表渲染异常、图表空白、数据不显示、筛选不生效、X轴切换失败、筛选值与图表不匹配、API返回409/400错误、Hibernate FunctionArgumentException 时使用。覆盖M1趋势图/M2客户类型/M3项目类型/M4竞品分析的全链路排查。"
---

# Chart Debug Checklist — 数据分析图表排查清单

## 核心原则

> **先验证环境，再怀疑代码。先验证 API，再排查前端。**
> **出现筛选条件不生效时，优先检查后端是否接收到了该参数。**

## 何时使用

- 图表渲染异常、图表完全空白
- 切换筛选条件（区域/招标主体/项目状态/时间维度等）后图表不更新
- 筛选条件与图表数据不匹配（如选了项目状态但 X 轴显示所有状态）
- X 轴切换（日/周/月/年/非时间维度）后图表空白
- API 返回 409 或 400 错误
- 后端日志出现 `FunctionArgumentException`、`HibernateException`、`IllegalStateException`

## 何时不要使用

- 页面级 404 路由问题（走前端路由排查）
- 数据库连接超时（走后端启动排查）
- 权限 403 问题（走权限排查流程）

## 数据分析模块架构速查

### 模块结构

| 模块 | 组件 | 后端入口 |
|------|------|---------|
| M0 关键指标 | `m0-key-metrics/` | `OverviewService.getOverview()` |
| M1 多维度趋势 | `m1-trend-analysis/` | `TrendAnalysisService.getEnhancedTrends()` |
| M2 客户类型 | `m2-customer-type/` | `OverviewService.getOverview()` |
| M3 项目类型 | `m3-project-type/` | `OverviewService.getOverview()` |
| M4 竞品分析 | `m4-competitor-analysis/` | `CompetitorAnalysisQueryService.getCompetitorRows()` |

### M1 多维度趋势架构（**高频出错区域**）

```
TrendAnalysisController.getEnhancedTrends()
  └─ TrendAnalysisService.getEnhancedTrends()
       ├─ xAxis=time → TrendAnalysisQueryService.fetchTimeTrendRows()
       │              → TrendAnalysisComputationService.computeTimeTrend()
       └─ xAxis=dept|person|region|customerType|projectType|projectStatus|tenderEntity|competitor
            → TrendAnalysisDimensionQueryService.fetchXxxRows()
            → TrendAnalysisComputationService.computeDimensionTrend() 或 computeProjectStatusTrend()
```

**关键文件**：
- 路由分发：`TrendAnalysisService.java`
- 维度查询：`TrendAnalysisDimensionQueryService.java`
- 聚合计算：`TrendAnalysisComputationService.java`
- 筛选条件：`FilterOptionsQueryService.java`

## 快速排查 4 步法

### 第 1 步：确认服务都在线

```bash
# 后端健康检查
curl -s http://127.0.0.1:<backend-port>/actuator/health

# 前端健康检查
curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:<frontend-port>
```

两个必须都返回 200/UP，否则先启动服务。

### 第 2 步：确认前后端端口一致

前后端端口不匹配是最常见的"图表空白"根因。

- 后端 `start.sh` 中 `SERVER_PORT` 默认值可能与前端 `api/config.js` 中的 `DEFAULT_API_PORT` 不一致
- 启动后端时必须显式指定正确端口：
  ```bash
  export SERVER_PORT=<正确的端口号> && ./start.sh
  ```
- 检查前端 API 配置中的 `DEFAULT_API_PORT` 是否与后端实际端口一致

### 第 3 步：直接验证 API 响应（绕开前端）

用 curl 直接调用后端 API，确认后端返回了正确的数据：

```bash
# 先登录获取 token
TOKEN=$(curl -s -X POST 'http://127.0.0.1:<backend-port>/api/auth/login' \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"XiyuAdmin2026!"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")

# 测试时间维度
curl -s "http://127.0.0.1:<backend-port>/api/analytics/trends/enhanced?xAxis=time&timeDimension=day&startDate=2026-01-01&endDate=2026-12-31"

# 测试非时间维度（如项目状态）
curl -s "http://127.0.0.1:<backend-port>/api/analytics/trends/enhanced?xAxis=projectStatus&statuses=BIDDING,EVALUATING,WON,LOST,FAILED,ABANDONED"

# 测试带筛选条件（如区域）
curl -s "http://127.0.0.1:<backend-port>/api/analytics/trends/enhanced?xAxis=region&regionIds=北京"

# 测试竞品数据
curl -s "http://127.0.0.1:<backend-port>/api/analytics/competitor/rows"
```

- 后端返回 200 + 正确数据 → 问题在前端
- 后端返回 404/空数据/500 → 问题在后端
- 后端返回 401/403 → 需要先登录获取 token
- 后端返回 409 → 检查后端类型映射（如 `DimensionRow` 构造函数参数类型）
- 后端返回 400 → 检查 Hibernate 数据库函数参数

### 第 4 步：前端 ECharts 渲染排查

如果 API 返回了正确数据但图表空白，按以下顺序排查：

#### 4.1 确认数据到达前端
```javascript
// 在 API 回调中加 console.log
console.log('chart data:', response.data)
```

#### 4.2 检查图表容器 DOM 是否被销毁
**高频根因**：`v-if/v-else` 切换 loading/error/empty 状态会导致图表容器 DOM 被销毁重建，ECharts 实例指向废弃 DOM 元素，`setOption` 不生效。

**修复方案**：图表容器始终保留在 DOM 中，状态覆盖用 CSS overlay：

```vue
<template>
  <div class="chart-wrapper">
    <div ref="chartRef" class="chart-container" :class="{ 'chart-dimmed': loading }"></div>
    <div v-if="loading" class="chart-overlay"><el-icon class="is-loading" :size="28"><Loading /></el-icon></div>
    <div v-else-if="error" class="chart-overlay state-error">...</div>
    <div v-else-if="isEmpty" class="chart-overlay state-empty">...</div>
  </div>
</template>
```

#### 4.3 检查 ECharts 初始化时机
```javascript
onMounted(() => {
  nextTick(() => {
    initChart()
  })
})
```

#### 4.4 检查 watch 是否有数据变化时更新图表
```javascript
watch(() => props.data, (newData) => {
  if (chartInstance && newData) {
    chartInstance.setOption(buildChartOption(newData, props.xAxisType), true)
  }
}, { deep: true })
```

## 多维度趋势模块（M1）专项排查

### 高频错误模式 1：后端参数漏传

**现象**：选择了筛选条件（区域/招标主体/项目状态）后，图表不按筛选条件过滤。

**根因**：`TrendAnalysisService` 的 `switch` 分发中，新增维度分支时**漏传对应参数**给 `fetchXxxRows()`。

**排查清单**（**每次新增 xAxis 维度或修改筛选逻辑时必须检查**）：

| 分支 | 调用方法 | 必须检查的参数 |
|------|---------|--------------|
| `dept` | `fetchDeptRows()` | `departmentIds` |
| `person` | `fetchPersonRows()` | `userIds` |
| `region` | `fetchRegionRows()` | `regionIds` |
| `customerType` | `fetchCustomerTypeRows()` | `customerTypes` |
| `projectType` | `fetchProjectTypeRows()` | `projectTypes` |
| `projectStatus` | `fetchStatusRows()` | `statusEnums`（**最易漏**） |
| `tenderEntity` | `fetchTenderEntityRows()` | `tenderEntities` |
| `competitor` | `fetchCompetitorRows()` | `competitorNames` |

**修复方法**：在 `TrendAnalysisService` 对应 `case` 分支中补充缺失的参数，并确认 `TrendAnalysisDimensionQueryService` 中对应方法的签名也接收该参数。

### 高频错误模式 2：维度计算方法不匹配

**现象**：项目状态 X 轴显示的 Y 值不正确（如少了某些状态的项目数）。

**根因**：通用 `computeDimensionTrend()` 方法只统计 `WON + LOST` 项目，而项目状态维度需要统计**所有状态**的项目数。

**排查清单**：
- `xAxis=projectStatus` → 必须使用 `computeProjectStatusTrend(dimensionRows, statuses)`
- 其他 xAxis → 使用 `computeDimensionTrend(dimensionRows, null)`

**修复方法**：在 `TrendAnalysisService` 中：
```java
// 项目状态维度走专用方法（保留选中顺序，数量为 0 也显示在 X 轴）
result = "projectStatus".equals(ax)
    ? computationService.computeProjectStatusTrend(dimensionRows, statuses)
    : computationService.computeDimensionTrend(dimensionRows, null);
```

### 高频错误模式 3：JPQL 查询字段类型不匹配

**现象**：API 返回 409 错误，后端日志出现 `HibernateException` / `IllegalStateException`。

**根因**：`DimensionRow` 构造函数期望 `String` 类型，但 JPQL 查询返回了枚举类型。Hibernate 无法将 `Project.Status` 枚举传入 `String` 参数，抛出异常后被 `GlobalExceptionHandler` 转为 409。

**排查方法**：
1. 查看后端日志中的 `HibernateException` / `IllegalStateException`
2. 检查 `DimensionRow` 的构造函数参数类型
3. 检查 JPQL 查询的 `select` 字段是否与构造函数参数类型匹配

**修复方法**：在 JPQL 中使用 `cast(xxx as string)` 将枚举转换为字符串：
```java
// 错误：select 返回枚举类型
"p.status"

// 正确：cast 为字符串类型
"cast(p.status as string)"
```

### 高频错误模式 4：数据源字段错误

**现象**：筛选条件不生效，或图表数据与实际不符。

**排查清单**：
- 区域维度：使用 `pid.headquartersLocation`（项目所在地），而非 `t.region`（招标主体所在地）
- 竞品数据：通过 `project_result_competitor` → `project_result` → `project` 链路关联
- 招标主体：使用 `t.purchaserName`（招标主体名称）

### 高频错误模式 5：前端参数构造错误

**现象**：切换时间维度（日/周/月/年）后图表空白，或筛选条件不生效。

**根因**：前端 `buildApiParams` 未正确构造 API 参数。

**排查清单**：
- `xAxis=time` 时，必须发送 `timeDimension` 参数（day/week/month/year）
- `xAxis=非时间维度` 时，不应发送 `timeDimension` 参数
- 筛选条件（`regionIds`/`statuses`/`tenderEntities` 等）必须在 `params` 中

**修复方法**：检查 `index.vue` 中的 `buildApiParams` 方法：
```javascript
function buildApiParams() {
  const params = { xAxis: xAxisType.value }
  if (xAxisType.value === 'time') {
    params.timeDimension = timeDimension.value
  }
  // 添加所有筛选条件
  if (selectedRegion.value?.length) params.regionIds = selectedRegion.value
  if (selectedStatus.value?.length) params.statuses = selectedStatus.value
  // ...
  return params
}
```

### 高频错误模式 6：Hibernate 数据库函数参数不匹配

**现象**：选择时间维度中的"周"后，图表空白或 API 返回 400 错误，后端日志出现 `FunctionArgumentException`。

**根因**：Hibernate 的 `function('week', p.createdAt, 1)` 传了 2 个参数给 MySQL 的 `WEEK()` 函数，但 MySQL 的 `WEEK()` 函数只接受 1 个参数（日期），不接受第 2 个参数（mode）。

**复现条件**：
- `xAxis=time` + `timeDimension=week`
- 后端 JPQL 中使用了 `function('week', p.createdAt, 1)`
- 后端日志出现 `org.hibernate.query.sqm.function.SqmFunctionDescriptor` 相关的 `FunctionArgumentException`

**排查方法**：
1. 用 curl 直接调用 API 验证：`curl -s "http://127.0.0.1:<backend-port>/api/analytics/trends/enhanced?xAxis=time&timeDimension=week&startDate=2026-01-01&endDate=2026-12-31"`
2. 查看后端日志是否包含 `FunctionArgumentException`
3. 检查 `TrendAnalysisQueryService` 中时间维度的 JPQL 查询

**修复方法**：将 `function('week', p.createdAt, 1)` 改为 `function('week', p.createdAt)`（单参数，使用 MySQL 默认 mode 0，周日为一周第一天）。

```java
// 错误：传了 2 个参数
case "week" -> "function('year', p.createdAt), 0, function('week', p.createdAt, 1), 0";

// 正确：传 1 个参数
case "week" -> "function('year', p.createdAt), 0, function('week', p.createdAt), 0";
```

**注意**：其他时间维度函数（`year`、`month`、`day`）都是单参数，不会出现此问题。只有 `week` 容易因为想传 mode 参数而踩坑。

## 典型问题速查

| 现象 | 最可能原因 | 排查顺序 |
|------|-----------|---------|
| 图表完全空白，F12 无报错 | 后端端口不匹配 / 后端未启动 | 1 → 2 → 3 |
| 图表空白，F12 报 404/409 | 后端启动失败 / API 路径错误 / 类型不匹配 | 1 → 3 |
| 切换时间维度后空白 | 后端未处理 timeDimension 参数 / 前端未发送该参数 | 3 → 5 |
| 选择"周"维度后 400 错误 | Hibernate `week()` 函数多传了 mode 参数 | 3 → 排查模式 6 |
| 筛选条件不生效（区域/招标主体/项目状态） | 后端 switch 分支漏传参数 | 3 → 排查模式 1 |
| 项目状态 Y 值不正确 | 使用了通用 computeDimensionTrend 而非专用方法 | 3 → 排查模式 2 |
| 项目状态 API 返回 409 | JPQL 查询返回枚举而非 String | 3 → 排查模式 3 |
| 区域筛选不生效 | 使用 t.region 而非 pid.headquartersLocation | 3 → 排查模式 4 |
| 加载中态不消失 | 后端挂了 / 请求超时 | 1 → 3 |
| loading/error 切换后图表空白 | v-if 销毁了图表容器 DOM | 4.2 |
| 数据更新后图表不刷新 | watch 未触发 / chartInstance 丢失 | 4.4 |

## 红牌警告（Red Flags）

排查过程中出现以下信号时，停下来重新检查前提：

- 后端日志有 `HibernateException` 但没看日志就开始查前端代码
- 筛选条件不生效时，先怀疑前端传参而不是先 curl 验证 API
- 修改了 `TrendAnalysisService.switch` 分支但没检查参数传递完整性
- 新增了 xAxis 维度但没在排查模式 1 的表格中对照检查
- 换了时间维度但没检查 `week()` 函数参数

## 常见误区

| 误区 | 正确做法 |
|------|---------|
| 图表空白先查前端 ECharts 配置 | 先 curl 验证 API 返回，确认后端数据正确再查前端 |
| 筛选条件不生效先查前端传参 | 先 curl 带参数调 API，看后端是否收到参数 |
| 409 错误先查数据库 | 409 通常是 Java 类型映射问题，不是数据库问题 |
| 400 错误先查请求格式 | 400 可能是 Hibernate 函数参数问题，先看后端日志 |
| 怀疑"后端改了别的东西" | 先 git diff 对比改动，再 curl 验证，不要靠猜 |

## 调用时机

- 当图表渲染异常、数据显示不全时
- 当切换筛选条件后图表不更新时
- 当 API 返回数据但前端展示空白时
- 当时间维度（日/周/月/年）切换不生效时
- 当筛选条件与图表数据不匹配时
- 当 API 返回 409 或 400 错误时
- **每次新增 xAxis 维度或修改筛选逻辑后，必须对照"高频错误模式 1"检查参数传递**