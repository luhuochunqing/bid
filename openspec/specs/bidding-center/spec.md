# Bidding Center Specification

## Purpose
标讯中心是投标管理系统的数据入口模块，负责标讯信息的采集、管理和初步筛选，为后续投标项目立项提供数据基础。本规格基于产品蓝图 V1.1 制定，覆盖标讯全生命周期：多渠道采集 → 智能去重 → 分配转派 → 评估决策 → 台账导出。
## Requirements
### Requirement: Tender Data Model (REQ-BC-001)
标讯实体 SHALL 包含完整的 28 字段数据模型，覆盖基本信息、联系人体系、人员分配和业务分类。

#### Scenario: Create tender with required fields
- **GIVEN** a user with tender creation permission
- **WHEN** they submit with 项目名称, 招标机构, 业主单位, 总部所在地, 报名截止时间, 开标时间, 联系人, 联系方式, 客户类型, 优先级
- **THEN** tender is created with status PENDING_ASSIGNMENT and all fields persisted

### Requirement: Tender Status Lifecycle (REQ-BC-002)
标讯 SHALL 支持 7 个状态枚举及其完整流转规则。

#### Scenario: Normal status flow
- **GIVEN** PENDING_ASSIGNMENT → admin assigns → TRACKING
- **WHEN** evaluation submitted → EVALUATED
- **WHEN** "立即投标" clicked → BIDDING
- **THEN** status transitions correctly at each step

#### Scenario: Abandon with reason
- **GIVEN** tender in EVALUATED
- **WHEN** "放弃投标" with reason
- **THEN** status → ABANDONED, abandonmentReason persisted

#### Scenario: Final states immutable
- **GIVEN** tender in WON / LOST / ABANDONED
- **WHEN** status change attempted
- **THEN** operation rejected

### Requirement: Tender List Page (REQ-BC-003)
标讯列表页 SHALL 展示 18 列完整信息并支持 11 维度筛选。

#### Scenario: 18 column display
- **GIVEN** user views tender list
- **THEN** table shows: 复选框, 序号, 项目名称, 来源平台, 总部所在地, 业主单位, 项目类型, 客户类型, 营业收入, 报名截止日期, 开标时间, 标讯状态, 项目负责人, 项目部门, 投标负责人, 优先级, 创建人, 操作

#### Scenario: Personnel dimension filters
- **GIVEN** user filters by 项目负责人 / 投标负责人 / 创建人
- **THEN** list shows only matching tenders

#### Scenario: Status tab with counts
- **GIVEN** user clicks status tab
- **THEN** list filters by that status, each tab shows count badge

#### Scenario: Export with full columns
- **GIVEN** user clicks export
- **THEN** Excel contains all 17 data columns (excl checkbox/action), respecting permission scope

### Requirement: Tender Detail Page (REQ-BC-004)
标讯详情页 SHALL 采用 3 Tab 布局 (基本信息/项目评估表/操作日志)。

#### Scenario: Header information card
- **GIVEN** user opens tender detail
- **THEN** header shows breadcrumb, title, status tag, priority tag, source tag, meta info

#### Scenario: Basic Info Tab with 27 fields
- **GIVEN** user on "基本信息" tab
- **THEN** all 27 blueprint fields displayed, readonly by default
- **AND** includes full contact1/contact2 and personnel assignments

#### Scenario: Evaluation Tab in all statuses
- **GIVEN** any tender status
- **WHEN** user switches to "项目评估表" tab
- **THEN** evaluation form displayed with status-appropriate content

#### Scenario: Operation Log Tab with timeline
- **GIVEN** user switches to "操作日志" tab
- **THEN** reverse-chronological timeline shows 操作时间, 操作人, 操作类型, 详情, before/after values

#### Scenario: Bottom action bar persists
- **GIVEN** user switches tabs
- **THEN** bottom action bar remains visible and position unchanged

### Requirement: Manual Tender Entry (REQ-BC-005)
人工录入 SHALL 使用分步填写模式复用详情页布局。

#### Scenario: Two-step entry flow
- **GIVEN** user clicks "人工录入"
- **WHEN** detail page opens in create mode on "基本信息" tab, [下一步] visible
- **WHEN** required fields filled + click [下一步]
- **THEN** switch to "项目评估表" tab, [上一步] [保存] shown

#### Scenario: AI-assisted parsing
- **GIVEN** user on basic info tab in create mode
- **WHEN** upload file or paste bid notice → click "AI解析"
- **THEN** parsed fields auto-populate form

### Requirement: Deduplication (REQ-BC-006)
标讯 SHALL 基于 4 字段组合去重。

#### Scenario: Auto-skip from third-party
- **GIVEN** source sync, match on (项目名称+业主单位+报名截止+开标时间)
- **THEN** auto-skipped, skipped count incremented

#### Scenario: Prompt on manual duplicate
- **GIVEN** manual entry matches existing by 4-field combination
- **THEN** dialog: "已存在，是否覆盖" with [覆盖] [新建] options

### Requirement: Source Configuration (REQ-BC-007)
标讯源配置 SHALL 包含 11 项字段。

#### Scenario: Full config form
- **GIVEN** admin opens source config
- **THEN** shows: 标讯源平台, API端点, API密钥, 业务单位, 关键字, 地区, 预算min/max, 自动同步, 自动匹配后入库, 自动去重
- **AND** test connection functional when API fields filled

### Requirement: Role-Based Access Control (REQ-BC-008)
标讯中心 SHALL 实现 4 角色数据权限与功能权限矩阵。

#### Scenario: Admin full access
- **GIVEN** 投标管理员
- **THEN** all tenders visible, all operations allowed per status

#### Scenario: Specialist restricted
- **GIVEN** 投标专员
- **THEN** only assigned tenders visible, no distribute/reassign

#### Scenario: Readonly on terminal states
- **GIVEN** tender in BIDDING/WON/LOST/ABANDONED
- **THEN** form readonly for all roles

### Requirement: Audit Logging (REQ-BC-009)
标讯中心所有操作 SHALL 记录审计日志。

#### Scenario: Log all operation types
- **GIVEN** any of: 创建, 编辑, 分配, 转派, 评估提交, 立即投标, 放弃投标, 状态变更, 删除
- **THEN** audit log entry with correct action type, user, timestamp

#### Scenario: Log field changes
- **GIVEN** user edits a field
- **THEN** audit log records oldValue and newValue

### Requirement: Evaluation Notification (REQ-BC-010)
评估提交后 SHALL 通知相关人员。

#### Scenario: Notify decision makers
- **GIVEN** evaluation submitted
- **THEN** todo created for 投标管理员, 投标组长, 投标负责人
- **AND** 企微 message sent

