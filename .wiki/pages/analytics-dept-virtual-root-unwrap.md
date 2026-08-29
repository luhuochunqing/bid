---
title: 数据分析部门筛选虚拟根组织架构解包机制
space: engineering
category: architecture
tags: [analytics, organization, department, filter, data-analysis]
created: 2026-08-29
updated: 2026-08-29
health_checked: 2026-08-29
sources:
  - docs/implementation-notes/analytics-dept-two-level-filter.md
  - backend/src/main/java/com/xiyu/bid/analytics/service/FilterOptionsQueryService.java
backlinks:
  - _index
---

# 数据分析部门筛选虚拟根组织架构解包机制

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
