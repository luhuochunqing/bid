---
title: 数据分析部门筛选虚拟根组织架构解包与业务根锚定机制
space: engineering
category: architecture
tags: [analytics, organization, department, filter, data-analysis, anchor]
created: 2026-08-29
updated: 2026-08-31
health_checked: 2026-08-31
sources:
  - docs/implementation-notes/analytics-dept-two-level-filter.md
  - docs/implementation-notes/dashboard-org-root-0002.md
  - backend/src/main/java/com/xiyu/bid/analytics/service/FilterOptionsQueryService.java
  - backend/src/main/java/com/xiyu/bid/analytics/service/FilterDepartmentTreeAnchorPolicy.java
backlinks:
  - _index
---

# 数据分析部门筛选虚拟根组织架构解包与业务根锚定机制

## 1. 业务背景

在"数据分析 - 多维度趋势分析"的筛选区中，部门下拉树与 X 轴成桶要求：
- 下拉树只展示**一级、二级**部门（三级及以下不进树）；
- 选了一级只展示一级，只要选了二级就按二级展示；
- 未勾选二级的一级按一级成桶。

## 2. 核心问题与根因

OSS 同步的组织机构表（`organization_departments`）包含顶层虚拟根容器：
- `department_code = 'rootorg'`（或 `root`）
- `department_name = '组织架构'`
- `parent_department_code = null`

真实的业务一级部门（如"销售总部"、"运营中心"）在数据库中的 `parent_department_code` 指向 `rootorg`。

若直接以物理层级构建树：
1. `rootorg`（"组织架构"）会被作为 Level 1 根节点；
2. 真实的一级部门（"销售总部"）被误算为 Level 2；
3. 真实的二级部门（"华东区"）被误算为 Level 3，并在两级深度截断（`pruneFilterTree(roots, 1)`）时被错误裁掉丢失。

## 3. 解决方案（Unwrap 虚拟根）

在 `FilterOptionsQueryService` 统一实现虚拟根解包：
1. **识别虚拟根**：`findVirtualRootCodes(allDepts)` 识别 `code in ('rootorg', 'root')` 或顶层 `name == '组织架构'` 节点；
2. **筛选树解包**：在 `fetchDepartmentTree()` 中跳过虚拟根节点，父节点为虚拟根的子部门直接挂为顶级 `roots` 节点，之后再执行保留两级裁剪；
3. **后台层级对齐**：在 `loadDepartmentNodes()` 中排除虚拟根节点，并将其直接子节点的 `parentDepartmentCode` 置为 `null`，确保 `AnalyticsDepartmentHierarchySupport.levelOf()` 计算出的深度与前台下拉树完全一致。

## 4. 演进：锚定业务根(0001)子树三级展示（2026-08-31）

解包虚拟根后仍有新问题：真实 OSS 树的前两层是**集团治理层**（`rootorg → 西域集团(0001) → 子公司`，如西域智慧供应链(0002)、威斯汀爱睿、狮行智慧物流），真实业务部门（业务中心及其下级）在物理 4~5 层。全树两级截断后下拉只到子公司层，业务部门不可见；单纯放大截断深度又会把"测试1"等治理/测试分支全部暴露。

最终方案：**把展示根从"解包后的全树"下移到"西域集团(0001)子树"，并在该子树内展示三级**：
- 一级 = 0001 的全部直接子节点（锚定节点，各子公司）；
- 二级 = 锚定节点的子节点（业务中心等）；
- 三级 = 锚定节点的孙节点；
- `rootorg`、`0001` 本身、其他根分支（测试1 等）一律不出现。

实现要点（`FilterDepartmentTreeAnchorPolicy`，纯静态策略类）：
1. **锚点按编码定位**：`ANCHOR_CODE = "0001"`（OSS 编码稳定，不用名称匹配，避免全角括号变体）；
2. **命中即锚定**：组树后以锚点 children 为新根并三级截断（`pruneFilterTree(roots, 1, 3)`），跳过 User 表兜底（否则杂项部门重新出现在顶层）；
3. **未命中回退**：锚点不存在（如环境未同步组织表）时回退"全树两级 + User 兜底"的旧行为，防止下拉空白；
4. **前端双保险**：`useFilterSearch.js` 的 `capDepartmentTreeDepth(…, 3)`；
5. **X 轴口径解耦**：部门维度的查询/展开/分桶（`loadDepartmentNodes → expand/bucketNames`）按选值名称匹配，与下拉展示根无关，未改动。

## 5. 教训沉淀

| 坑 | 为何发生 | 对策 |
|---|---|---|
| 固定两级截断假设"业务部门在浅层" | 集团型组织树前 1~2 层是治理容器，业务部门在深层 | 组织树展示范围应锚定**业务根**（按稳定编码），而非从物理根数层级 |
| 锚定后 User 兜底污染顶层 | 兜底逻辑无条件补根 | 兜底只在锚点未命中的回退路径生效 |
| 本地开发库无组织数据 | `organization_departments` 仅测试/生产由 OSS 同步 | 本地验证可用带标记（`source_app=xxx`）的临时数据走生产代码路径，验后清理；真实数据 UI 验收放到测试环境 |
