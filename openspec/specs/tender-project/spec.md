# tender-project Specification

## Purpose
TBD - created by archiving change align-tender-project-list-initiation. Update Purpose after archive.
## Requirements
### Requirement: Project List Column Alignment (REQ-TP-001)
项目列表页 SHALL 展示 PRD §4.3 规定的完整字段集，支持多维度筛选和导出。

#### Scenario: Full column display
- **GIVEN** user with project view permission navigates to project list
- **THEN** table shows: 项目名称, 业主单位, 入闱家数, 创建时间, 开标时间, 投标月份, 项目类型, 客户类型, 客户等级, 投标状态, 项目负责人, 负责人部门, 投标负责人, 投标状态, 中标状态, 投标平台

#### Scenario: Multi-dimension filter
- **GIVEN** user uses search area
- **WHEN** they filter by 项目类型, 客户类型, 客户等级, 投标状态, 项目负责人, 投标负责人
- **THEN** list refreshes with matching results

#### Scenario: Export to Excel
- **GIVEN** user clicks 导出按钮
- **WHEN** system generates Excel with current filter applied (all pages)
- **THEN** file downloads as `投标项目列表_YYYYMMDD_HHmmss.xlsx`

#### Scenario: Horizontal scroll for overflow
- **GIVEN** viewport width < 1400px
- **THEN** table shows horizontal scroll bar, minimum width 1400px maintained

### Requirement: Project Initiation Form Structure (REQ-TP-002)
项目立项表单 SHALL 按 PRD §4.3 分区布局，包含完整字段集和 AI 风险评估。

#### Scenario: Basic info section (3-column layout)
- **GIVEN** user opens initiation form
- **THEN** 基本信息区 shows fields in 3-per-row layout: 项目名称, 业主单位, 创建时间, 项目类型, 客户类型, 优先级, 总部所在地, 项目负责人, 项目负责人部门

#### Scenario: Bidding info section (mixed layout)
- **GIVEN** user scrolls to bidding info
- **THEN** 投标信息区 shows: first 6 fields in 3-per-row, next 6 in single-row: 入闱家数, 年度电商采购金额, 营业收入, 开标时间, 投标月份, 投标平台, 合同期限, 保证金金额, 保证金缴纳方式, 竞争对手, 预计中标率, 投标方案

#### Scenario: Customer info table (15 columns × 14 rows)
- **GIVEN** user expands customer info
- **THEN** 客户信息表格 shows 15 columns horizontally scrollable, 14 editable rows

#### Scenario: AI risk assessment
- **GIVEN** user uploads bidding document
- **WHEN** they click AI风险评估 button
- **THEN** system analyzes document and displays 高/中/低 risk tag

#### Scenario: Submission and approval flow
- **GIVEN** user fills all required fields
- **WHEN** they submit for review
- **THEN** project enters review state, admin can approve (assign team + lock fields) or reject (with reason)

### Requirement: Project List Default Display (REQ-TP-003)
项目列表默认展示 SHALL 按创建时间降序排列，每页 10 条，筛选为空时展示当前用户有权限访问的全部项目。

#### Scenario: Default view
- **GIVEN** user opens project list
- **THEN** default sort by 创建时间 desc, page size 10, all accessible projects shown

#### Scenario: Empty state
- **GIVEN** no projects match filter
- **THEN** display "暂无数据" empty state

