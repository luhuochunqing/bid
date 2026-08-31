# Implementation Notes: M1 部门下拉树锚定西域集团(0001)子树三级展示

> 分支：`agent/trae/dashboard-org-root-0002` ｜ 日期：2026-08-31 ｜ 代理：trae (ZCode)

## 需求背景

`/analytics/dashboard` 多维度趋势分析的部门选择器，此前已落地"rootorg 虚拟根解包"（lessons-learned #184，2026-08-29），两级截断下展示到全树一、二级（西域集团、测试1…为一级；西域智慧供应链、威斯汀爱睿…为二级）。用户仍看不到真实业务部门（业务中心及其下级，物理层级 4~5 层）。

## 最终需求（经两轮对话修正 + 两处歧义确认）

树结构锚定**西域集团(0001)子树**，共三级：

| 层级 | 内容 |
|---|---|
| 不显示 | rootorg、西域集团(0001) 本身、其他根分支（测试1、新甘青宁项目组、上海项目组、江苏项目组） |
| 一级 | 锚定节点 = 西域集团(0001) 的全部直接子节点（西域智慧供应链、威斯汀爱睿、狮行智慧物流…） |
| 二级 | 锚定节点的子节点（业务中心等） |
| 三级 | 锚定节点的孙节点 |

## 理解修正过程（记录给后续会话）

1. **第一版理解（错误）**：以为只锚定单个编码 0002（西域智慧供应链），展示其下两级。
2. **用户纠正**：西域集团是根节点，其下**所有**二级节点都是锚定节点（一批，非单个），要展示的是这些锚定节点下面的两级。
3. **AskUserQuestion 确认的两个歧义**：
   - 锚定节点本身**显示**，作为下拉第一级 → 树共三级（选两级拍平会导致跨子公司同名部门 value 冲突，后端按部门名称匹配会串数据）；
   - 其他根分支（测试1 等）**全部不展示**。

## 关键决策（spec 未覆盖部分）

| 决策 | 结论 | 理由 |
|---|---|---|
| 锚点定位方式 | 硬编码常量 `ANCHOR_CODE = "0001"`（OSS 组织编码） | OSS 同步编码稳定；名称含全角括号易变体；单处使用，后续需要再配置化 |
| 锚点未命中行为 | 回退现有全树两级 + User 兜底 | 防止环境组织表未同步时下拉空白；同时保住既有测试语义 |
| 锚点命中时 User 兜底 | 跳过 | 否则"测试1"等杂项重新出现在顶层，违背"只展示" |
| X 轴部门维度口径 | 不动（维持全树层级 expand/bucketNames） | 选值仍是部门名称字符串，查询/展开/分桶与树展示根解耦 |
| 前端截断 | `capDepartmentTreeDepth(…, 2)` → `3`（调用参数） | 函数纯参数化不改；后端已截断，前端是双保险 |
| 300 行门禁拆分 | 新增 `FilterDepartmentTreeAnchorPolicy`，迁出 `pruneFilterTree` | 主文件 298 行仅剩 2 行余量，RULES §2.6.2 要求超限前先拆分 |

## 修改文件

- 新增 `backend/src/main/java/com/xiyu/bid/analytics/service/FilterDepartmentTreeAnchorPolicy.java`
- 修改 `backend/src/main/java/com/xiyu/bid/analytics/service/FilterOptionsQueryService.java`
- 修改 `backend/src/test/java/com/xiyu/bid/analytics/service/FilterOptionsDepartmentTreeTest.java`
- 修改 `src/views/data-analysis/components/m1-trend-analysis/composables/useFilterSearch.js`
- 修改 `src/views/data-analysis/components/m1-trend-analysis/capDepartmentTreeDepth.spec.js`

## 验证证据

| 验证项 | 结果 |
|---|---|
| TDD Red | `shouldAnchorTreeToXiYuGroupSubtree` 先失败（实际输出 `["西域集团","测试1","幽灵兜底部门"]` = 现状行为） |
| TDD Green | `FilterOptionsDepartmentTreeTest` 6/6 通过（4 既有 + 2 新增：锚定三级树、锚点未命中回退） |
| 前端单测 | `capDepartmentTreeDepth.spec.js` 3/3 通过（新增 depth=3 用例）；全量 `test:unit` 3061 通过 / 1 skip（既有基线） |
| 架构门禁 | `ArchitectureTest` + `MaintainabilityArchitectureTest` 35/35 通过 |
| 行数门禁 | `npm run check:line-budgets` 通过（主文件 298 → 292，新类 53 行） |
| 构建 | `npm run build` 通过（10.35s） |
| 运行时回退路径 | 新 jar 部署开发宿主后 `/api/analytics/filter-options` 200，本地无组织数据 → department 空、其余 6 维度数量与改动前一致（无回归） |
| 运行时锚定路径 | 临时插入带标记验收数据（rootorg→0001→锚定节点→业务中心→销售部门，含 T001 测试1 干扰项）→ API 实测返回三级树：一级=西域智慧供应链/威斯汀爱睿/狮行智慧物流，二级=业务中心等，三级=销售一部/二部；rootorg/西域集团/测试1 全部排除。验证后数据已清理（`organization_departments` 恢复 0 行） |

## 遗留与备注

- **本地库无 OSS 组织数据**（`organization_departments` 空表、users 表 3439 人部门字段全空），本地 UI 无法看到真实树；真实数据的三级树 UI 验收待部署测试环境后复核。
- 本地开发宿主 3306 端口存在 Homebrew mysqld 与 Docker 端口共存，后端实际连 Homebrew 实例（`ps eww` 提取 `DB_USERNAME=xiyu_user`）；容器内 `xiyu-bid-local-mysql` 的 xiyu_bid_main 为空库，排查时勿混淆。
- Mockito 严格模式（UnnecessaryStubbingException）天然锁定了"锚点命中时不触 User 兜底查询"——主用例不 stub entityManager，实现若误调兜底将 NPE 失败。
- X 轴部门维度分组/下钻（`loadDepartmentNodes` → `expand`/`bucketNames`）未改动，按选值名称匹配与树展示根解耦；如后续需要把 X 轴层级口径也锚定到 0001 子树，另开任务。
