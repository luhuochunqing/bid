# Feature Specification: M1/M4 筛选区竞品公司下拉接入动态字典口径 (056)

**Feature Branch**: `agent/ox/analytics-competitor-filter-dynamic`

**Created**: 2026-08-26

**Status**: Draft

**Input**: 用户需求——数据分析模块 M1 多维度趋势分析与 M4 竞品分析筛选区的「竞品公司」下拉，选项应包含结果确认阶段竞品公司列的可选值（ACTIVE 字典）∪ 已停用/删除但被引用过（有数据）的竞品公司。

## 业务背景与问题定义

PR !2414 将竞品公司升级为全局字典 `competitors`（ACTIVE/DISABLED 软停用、输入即建），结果确认页录入下拉已动态化；但 **M1/M4 两个分析模块的筛选区仍使用 PR !2414 迁移前的那份硬编码 24 家静态名单**：

- 新录入（allow-create）的竞品不出现在筛选区；
- 已停用竞品照样显示（误导用户选择无数据的项）；
- 静态名单与字典从此各自漂移。

## 用户故事与使用场景

### User Story 1: 筛选区竞品选项与业务数据同步 (P1)
**作为** 数据分析模块使用者，
**我希望** M1/M4 筛选区的竞品公司下拉 = 结果确认页可选项 ∪ 有历史数据的停用竞品，
**以便于** 筛选能力覆盖全部真实存在的竞品维度，不漏不错。

**Acceptance Scenarios**:

1. **Given** 字典新增 ACTIVE 竞品「新竞品A」（未被引用），**When** 打开 M1/M4 筛选区，**Then** 下拉含「新竞品A」。
2. **Given** 竞品「老竞品B」被项目引用过且已被停用，**When** 查看下拉，**Then** 「老竞品B」仍在（有数据可筛）。
3. **Given** ACTIVE 竞品从未被引用且后来停用，**When** 查看下拉，**Then** 不出现（既非 ACTIVE 也无引用）。
4. **Given** 快照中存在自由文本名（历史遗留），**When** 查看下拉，**Then** 该名保留（被引用过即显示）。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-01 (口径公式)**: 下拉选项 MUST = `competitors(status=ACTIVE)` ∪ `DISTINCT(project_result_competitor.name 且非空)`。
- **FR-02 (单一数据源)**: M1 与 M4 MUST 共用同一后端口径（`/api/analytics/filter-options` 的 `.competitor` 字段），禁止前端各自实现并集。
- **FR-03 (权限语义)**: 快照部分维持既有项目级 scope 过滤；字典部分为主数据，任何已认证用户可见（与结果确认页一致）。
- **FR-04 (排序)**: 合并后按名称统一排序（Java 端自然序，与 DB collation 解耦）。
- **FR-05 (M4 默认全选跟随)**: M4 初始选中集合 MUST 跟随动态 options（保持「默认全选」语义）；加载时序保证先于首次绘图。
- **FR-06 (失败兜底)**: 接口失败时前端回退现有静态常量，不白屏。
- **FR-07 (零回归)**: 图表查询/下钻/结果确认页/X 轴分组逻辑零改动；M1 的 `handleCompetitorSearch` 死代码顺带清理。

## Success Criteria

- **SC-001**: 后端单测覆盖并集/去重/排序/scope/空值过滤，全绿。
- **SC-002**: 前端 M1/M4 既有 spec 适配通过。
- **SC-003**: ArchUnit 全量规则通过（analytics→competitionintel 仅 import pure enum，无循环依赖）。

## Assumptions

- 「删除」在当前治理模型中仅软停用（DISABLED），物理删除的字典行不影响快照名提取。
- 本任务不含其他筛选维度（部门/人员等）的动态化（它们同样静态，但超出本次需求范围）。
