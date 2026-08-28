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
| `analytics/service/AnalyticsDepartmentHierarchySupport.java` | 纯函数支持类，**单一入口 `prepare(nodes, selectedNames)` 返回 `Prepared` record**（`matchNames` 匹配展开=选中∪后代 / `bucketNames` X 轴展示桶 / `bucketByName` 用户部门名→桶标签 / `namesByBucket` 其倒排），一次构建供展开、分桶、下钻反解三方复用。正/反映射由同一份 `bucketByName` 构造（`invert()`），杜绝两侧演进漂移。父子索引一律以 `(sourceApp, code)` 为键，name 仅作输出标签。零框架依赖可纯单测 |
| `analytics/service/AnalyticsDepartmentTreeGateway.java` | 新增 `@Component` 读取网关：`organization_departments` → `DeptNode`，**脏行（code/name 为空）先 filter 再 map**，避免 DeptNode 构造器抛 IAE 把整条链路打成 500。图表/下钻/人员联动选项三方共用同一棵树 |
| `TrendAnalysisService.java` | criteria 的 `departmentMatchNames` 先展开；dept 轴取数改为一行 `fetchDeptRows(criteria, dept.bucketMapper())`（重映射下沉查询层）；expectedCategories 改为 `dept.bucketNames()` |
| `FilterOptionsQueryService.java` | `fetchDistinctPersons` 改走 Gateway 取树并展开选中名集合，人员联动选项覆盖后代部门人员；**不再有 `fetchDepartmentNodes()`**（组织树原始结构不是"筛选区下拉选项"，且该类已顶到 296/300 行门禁）|
| `TrendAnalysisDimensionQueryService.java` | `fetchDeptRows` 增加 `Function<String,String> categoryMapper` 参数（null=恒等），展示桶重映射在查询层内完成 |
| `TrendDrillDownService.java` | 部门筛选 IN 使用展开集；dept 轴+有选中时走 `appendDeptAxisFilter` 反解分桶标签为名称集合（图数与明细同口径）；无勾选时保留旧精确等值路径且不加载组织树 |
| `TrendDrillDownFilterSupport.java` | 新增 `appendDeptAxisFilter(where, params, Prepared, axisValue)`：直读 `Prepared.namesInBucket(axisValue)` 倒排 + axisValue 自身兜底 |

## 规格之外不得不做的决策

1. **被选父节点直挂用户的罕见脏数据**：该名称不在任何子桶内、又不允许造一级汇总柱，回退为「自身名自成桶」。保证图上合计 = 筛选范围数目守恒；代价是极罕见场景下图上仍可能出现一个与一级同名的小柱。
2. **同名部门防御**：树 value 是 name 且存在同名可能，层级索引全部走 code 计算；同名多处时深浅排序用最小深度。
3. **人员联动必须同步展开**：若只改统计不改 `/filter-options/persons`，会出现"图上有数、下拉里却没这个人"的新口径分裂。
4. **下钻反解**：axisValue 现在可能是分桶标签而非原始 departmentName（例如三级坍缩），等值匹配会漏；改为 `Prepared` 倒排反解后的名称集合 IN 匹配。
5. **嵌套选择（父子同时勾）**：深层选择优先——按节点深度升序处理选中名，深者后写生效（`prepare` 内 `bucketByName` 覆盖语义）。三级树 + 两种勾选提交顺序的断言才可回归该规则（两级 fixture 对顺序不敏感，见"踩坑记录"）。

## 反 AI-slop 收敛（ai-slop-cleaner 清理，行为不变）

- **三个分散方法收敛为单一入口**：初版把 `expandWithDescendants` / `displayBucketNames` / `bucketMapper` 拆成三个独立 public 方法，各自内部重复构建 `nodesByName` / `children` 索引。收敛为 `prepare()` 一次构建、`Prepared` record 承载四份结果，四个调用方（TrendAnalysisService / FilterOptionsQueryService / TrendDrillDownService / TrendDrillDownFilterSupport）全部改接，消除重复遍历。
- **`appendDeptAxisFilter` 签名收敛**：由 `(deptNodes, selectedDepartments, axisValue)` 改为直接收 `Prepared`；初版内部用 `allOrgNames()` 逐个正向映射回比（O(全组织名) 扫），现直读 `Prepared.namesByBucket` 倒排，正/反映射同源。
- **树外兜底判断修正**：初版用 `!covered.containsKey(selected)` 判断树外选中名，误把"有子节点的选中父级"也追加进桶（测试暴露：桶里多出"销售部"）。改为 `!byName.containsKey(selected)`——只对组织树外选中名追加桶，树内父级已由其直接子部门成桶。
- **格式还原**：slop 阶段把 `appendDeptAxisFilter(...); }` 压成同行 `}`，已还原为规范换行。
- 回归证据：analytics 全量 164 测试（含重构后 AnalyticsDepartmentHierarchySupportTest 12 用例）全部通过，行数门禁 `guarded_changes=9` 通过。

## 审查修复波次（PR !2437 三透镜审查后）

> 诚实记录："有二级按二级成桶"的正向映射实际到 `efa31f9af`（第二个 commit）才生效，
> 首个 commit 只做了"勾父展开后代"——本波次不历史改写，以新 commit 补正。

- **CRITICAL：跨 source_app 串树**。`organization_departments` 主键为 `(source_app, department_code)`（V1076："使 oss/ehsy 等不同 source_app 的部门记录互不覆盖"），而旧索引按裸 code 建父子关系，会把两棵互不相关的树缝成一棵。修复：`DeptNode` 增 `sourceApp`，索引键统一 `sourceApp + \u0000 + code`。改前红：`oss:D01(销售部)` 与 `customer-org:D01(技术部)` 同源串树，勾销售部把隔壁源客服部拉进统计范围（探针实测）；改后绿：`跨source_app同code不得串树` 用例守位，且删除分区键的变异体会红。
- **构造性修复**：`Prepared.bucketMapper` 由 `Function` 改为 `Map bucketByName` + 倒排 `namesByBucket`（record 可 equals，不再有"同一 Prepared 两次调用映射结果不同"的黑盒）；`nameOf`/`depthOf` 不再线性扫全节点（原 O(n²)）。
- **结构收敛**：`fetchDepartmentNodes()` 从 `FilterOptionsQueryService` 移出为 `AnalyticsDepartmentTreeGateway`（296 → 286 行，恢复门禁余量；同时消除下钻服务"为借维度数据而依赖选项查询服务"的借数据边）；dept 轴重映射从 `TrendAnalysisService` 下沉到查询层，据此**删除了 `project-access-guard-baseline.txt` 新增行**（不再用注释论证换取门禁豁免）。
- **死条件与默认路径回退**：下钻外层 `if (hasDeptSelection || deptAxis)` 改为 `if (hasDeptSelection)`——无勾选时走精确等值旧路径，不再白读一次全量 `organization_departments`。
- **字段正名**：`TrendQueryCriteria.departmentIds` → `departmentMatchNames`（它早已不是 id 也不是原始勾选集），Javadoc 声明"已展开名集，null=未启用"。
- **去 mock**：两个 JPQL 回归测试不再 `Mockito.mock(FilterOptionsQueryService.class)`，而是用真 repository 构造真实 Gateway；Region 回归新增 `deptAxisBarValueMatchesDrillDownTotalOnRealData` 真库锚定。

## 遗留：同名部门跨分支纳入的核查 SQL（本波次不修）

筛选协议传的是**部门名**（下拉树 value = name），因此同名部门一旦分属不同分支，勾选该名会把多个子树一起纳入——这是协议层面的固有歧义，彻底修复需筛选协议由 name 迁 code（待本 PR 合入后单独开 issue 排期，不在本波次扩散到 users 查询协议）。上线前后用下面三条 SQL 确认量级；结果非空即在 PR/工单里记录条数。

```sql
-- 1) 同组织源内同名不同 code（名称歧义的候选集；层级索引已按 (source_app, code) 分区，不受影响）
SELECT source_app, department_name,
       COUNT(DISTINCT department_code) AS code_variants,
       GROUP_CONCAT(CONCAT(department_code, '<-', COALESCE(parent_department_code, 'ROOT'))) AS placements
FROM organization_departments
WHERE enabled = 1
GROUP BY source_app, department_name
HAVING COUNT(DISTINCT department_code) > 1
ORDER BY source_app, code_variants DESC;

-- 2) 其中真正会跨分支的：同名部门挂在不同的根节点下（MySQL 8.0 WITH RECURSIVE）
WITH RECURSIVE root_of AS (
  SELECT source_app, department_code, department_name, parent_department_code,
         department_code AS root_code
  FROM organization_departments
  WHERE enabled = 1 AND (parent_department_code IS NULL OR parent_department_code = '')
  UNION ALL
  SELECT d.source_app, d.department_code, d.department_name, d.parent_department_code, r.root_code
  FROM organization_departments d
  JOIN root_of r ON d.source_app = r.source_app AND d.parent_department_code = r.department_code
)
SELECT source_app, department_name, COUNT(DISTINCT root_code) AS roots,
       GROUP_CONCAT(DISTINCT root_code) AS root_codes
FROM root_of
GROUP BY source_app, department_name
HAVING COUNT(DISTINCT root_code) > 1;

-- 3) 影响面：上述名字下挂了多少用户（决定优先级；users.department_name 与组织表同为 name 口径）
SELECT u.department_name, COUNT(*) AS users_cnt
FROM users u
WHERE u.department_name IN (/* 第 2 条查出的 department_name */)
GROUP BY u.department_name
ORDER BY users_cnt DESC;
```

第 2 条只覆盖"可从根抵达"的节点；孤儿/成环行不会出现在结果里（`prepare` 侧另有环状脏数据收敛保护，见 `AnalyticsDepartmentHierarchySupportTest#环状脏数据不死循环`），因此第 2 条为空时仍需看第 1 条。

## 既有行为变化（有意）

- wiki §9 记录过"勾父不级联、精确匹配"是当时的刻意决策；本次需求明确要求勾父包含后代数据，wiki §9 已同步更新。
- 全量测试证据：analytics 包 164 个测试全部通过（AnalyticsDepartmentHierarchySupportTest 12 用例、TrendDrillDownFilterSupportTest 6 用例、AnalyticsRegionFilterJpqlRegressionTest 13 用例含真库 dept 轴锚定）；`ArchitectureTest` + `ProjectAccessGuardCoverageTest` 同跑全绿。
- **本 PR 不修的既有缺口**：time 轴（xAxis=time）图表完全不消费部门筛选——`fetchTimeTrendRows` 路径上不取 `departmentMatchNames`，属于与本需求无关的存量不一致，已留待独立 issue。

## 踩坑记录

- 行数门禁 300 行：`TrendDrillDownService` 293 行、`FilterOptionsQueryService` 286 行（曾一度 296），`TrendAnalysisDimensionQueryService` 因重映射下沉一度到 310 行越线，靠删除单参重载（无外部调用方）+ 压缩 Javadoc 回到 300 行以内。门禁只允许"不超线"，不会提醒邻居文件越线，改接时需 `node scripts/check-line-budgets.mjs` 实跑。
- TrendQueryCriteria.departmentMatchNames 是 List，expand 返回 Set，null 语义="筛选未启用"，不能.isEmpty() 替代判空。
- **三级坍缩反解：不是现存 bug，但不能无锁**。审查时怀疑"选一级、点二级柱 → 三级成员反解不到（图数 > 明细）"；探针实测证实旧实现靠 `allOrgNames` 全量正向比对恰好能反解出来，**并不漏数**。风险在于它是"路径巧合"：桶定义一旦改为归入其他标签，没有测试会变红。现倒排同源 + `TrendDrillDownFilterSupportTest#deptAxisBucketReverseResolution`（三级 fixture）与 Region 真库锚定两道钉死。
- **跨源 code 分区**：不能只按 `departmentCode` 建父子索引（同表内同一 code 可属多棵组织树），否则`nameOf`/`childrenOf` 静默串树。索引键用 `\u0000` 分隔而非 `:`/`-`，避免 code 自带分隔符时拼出同一键。
- **"深者优先"一度不可回归**：两级 fixture 下删除排序代码后原 7 条断言全绿（变异存活）。换成三级 fixture 并同时断言 [父,子] / [子,父] 两种提交顺序后，删排序立即变红（实测：expected 华东组 but was 销售一部）。
- 脏数据防护位置：`DeptNode` 构造器对空 code 抛 IAE，因此过滤必须在 map **之前**（`filter(...).map(...)`）；先构造后过滤等于把一行脏数据升级为全接口 500。
