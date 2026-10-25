---
title: 项目列表服务端分页（快慢两路径与"一个筛选都不下推"的准入判据）
space: engineering
category: guide
tags: [项目列表, 服务端分页, 性能, collation, 权限下推, 全序]
created: 2026-09-22
updated: 2026-09-22
health_checked: 2026-10-25
sources:
  - backend/src/main/java/com/xiyu/bid/project/service/ProjectListQueryService.java
  - backend/src/main/java/com/xiyu/bid/project/service/ProjectQueryService.java
  - backend/src/main/java/com/xiyu/bid/project/dto/ProjectListQuery.java
  - backend/src/main/java/com/xiyu/bid/repository/ProjectRepository.java
  - backend/src/main/java/com/xiyu/bid/project/controller/ProjectController.java
backlinks:
  - _index
  - project-search-owner-unit
---
# 项目列表服务端分页（快慢两路径与"一个筛选都不下推"的准入判据）

> **一句话**：`GET /api/projects` 原先与查询条件无关——`findAll()` 拉全表、对**每一行**做 6 组关联富化
> （标讯 / 立项详情 / 负责人指派 / 阶段 / 部门 / 评标），最后才 `subList` 切页；2026-09-22 改为
> 「无筛选 + 带 `size` + 非 demo」时把排序 / 分页 / 权限过滤下推 SQL、**只富化当页**，
> 其余情况回落全量内存路径且行为逐字保持。

## 路由：两条路径，一个入口

| 条件 | 路径 | 取数与富化 |
|---|---|---|
| `size != null` 且 `hasNoEffectiveFilter()` 且 `!demoModeService.isEnabled()` | 快路径 `ProjectListQueryService.getProjectsPage` | SQL `ORDER BY created_at DESC, id ASC` + `LIMIT/OFFSET`，只取当页 id，**只富化当页** |
| 其余（`size` 缺省 / 含任一筛选 / e2e demo） | 慢路径（原样保留） | `ProjectQueryService.getAllProjects()` 全量取数 + 全量富化 |

入口只有一条：控制器 → `ProjectService.getProjects(ProjectListQuery)` → `ProjectListQueryService`。
`ProjectListQuery` 是 18 元 `record`（16 个筛选 + page/size）。用 record 而不是把 14 个"否决参数"
平铺进方法签名（16 个筛选里 14 个是 `String`、走"非空白否决"，另 2 个 `Long` 走 null 否决；出处见
`ProjectListQuery.java` 的类 javadoc）——平铺签名传错一个位置就会**静默丢掉回落**。

## 准入判据：为什么"一个筛选都不能下推"

可下推的**第四条**必要条件（前三条：实体列、非派生值、等值而非 contains）：

> **SQL 等值比较的语义必须与 `String.equals` 一致（空白折叠 + collation）。**

- `projects` 建表 collation 是 `utf8mb4_unicode_ci`（`B73__full_schema_baseline.sql:970`，
  `create table projects` 起于 `:940`；`docker-compose.yml` 也把 `collation-server` 钉成它）
  ⇒ **大小写不敏感**：`?stage=drafting`
  在慢路径下与 `DRAFTING` 不等（空结果），在 SQL `=` 下却会命中 ⇒ **放大可见结果**。
- H2 测试环境（`src/test/resources/application-test.yml`）的 `VARCHAR` 比较**区分大小写**，
  对这第四条**没有任何证明力**——"全绿"不能证伪它。
- 没有筛选满足第四条（`region` 还另有 `LIKE` 元字符与 `String.contains` 不等价的问题）。
  故准入收紧为「**本次一个筛选都没传**」，SQL 里不出现任何筛选参数 ⇒ 空白筛选（F1）与
  collation（F2）两类反例**结构性不可达**。

**代价（明确，且不是回归）**：带筛选的列表页回到全量路径，与改动前一致。

## 三处必须逐字对齐的契约

| # | 契约 | 落点 |
|---|---|---|
| 1 | 全序 `createdAt DESC, id ASC`：慢路径排序与两个分页查询的 `ORDER BY` 必须同源 | 定义在 `ProjectListQueryService#sortByCreatedAtDescIdAsc`；`ProjectRepository` 的 javadoc 指向它 |
| 2 | 权限分支判据 = 显式布尔 `currentUserHasAdminAccess()`，**不得**用 `allowedIds` 是否为空 | `getAllowedProjectIds` 对管理员**与**"合法零项目"的非管理员**都**返回空集 |
| 3 | `size == null` 仍返回**全量** | 前端首屏 `src/api/modules/projects.js` 与 `ProjectExportService` 都依赖它 |

## 明确未做（勿读成"已经快了"）

- **未部署、未复测**：2026-09-21 `standard` 档该链路 P95 2,224.5 ms（门禁 `p(95)<1000`，超标 122%）；
  改善必须部署后按同档位复测确认。
- **DB 侧仍是全表扫 + filesort**：`projects.created_at` 无索引。本次收益是"不再把 N 行搬到应用层富化"，
  **不是**扫描量下降。补索引是另一次带迁移的改动（`Project` / DDL 属 hot-paths）。
- **前端仍一次性取全量**（依赖 `size` 缺省返回全量），服务端分页收益要在前端改为按页请求后才体现在真实页面；
  该改造会把返回结构从裸数组改成 `{content, total}`。
- **不要顺手合并** `ProjectExportService` 那份近乎重复的筛选块：两份**语义不同**
  （导出对 `status` 用 `equalsIgnoreCase`、列表对 `stage` 用大小写敏感的 `equals`），合并必然改其中一侧的对外行为。

## 副作用（Cross-Module Impact）

- `getAllProjects()` 补了 `id ASC` 次级键 ⇒ **导出 / 工作台 / CRM 增量同步**三个下游的并列行顺序会变
  （物理序 → 确定序）。列表端点本身已被 `tiedCreatedAtShouldBreakTheTieTheSameWayOnBothPaths`
  覆盖（并列顺序 + `size=1` 的逐页边界）；三个下游**没有**顺序断言。
- `enrichWithTenderAndDetails` 由 `private` 放宽为**包级可见**（跨类调用，未提成 `public`）。
  Spring `publicMethodsOnly` 默认 true ⇒ **该方法自身的事务分界实际失效**；当前无影响
  （两个调用点都在已激活的只读事务内），但"它自己的 `@Transactional` 还在守"不成立。
- 相关页：[[project-search-owner-unit]]（招标主体数据链路，同一条富化链）。
- 交付过程的门禁教训见 [[gate-command-scope-pitfalls]]。
