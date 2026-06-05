# Feature Specification: 标讯创建/详情表单字段修正

**Feature Branch**: `codex/bidding-create` | **Created**: 2026-05-29 | **Status**: Draft

**Input**: 标讯创建基本信息表单中删除预算金额字段；标讯标题改为项目名称；报名截止/开标时间格式改为 yyyy-MM-dd HH:mm；客户类型字段值选项改为5项；来源平台字段创建时不展示。

## User Scenarios & Testing

### User Story 1 — 删除预算金额字段 (Priority: P1)

用户在标讯创建表单和标讯详情页中不再看到"预算金额"字段。

**Why this priority**: 蓝图要求删除该字段，避免冗余信息录入。

**Acceptance Scenarios**:
1. **Given** 用户打开人工录入标讯对话框, **When** 查看基本信息表单, **Then** 不显示"预算金额（元）"输入框
2. **Given** 用户打开标讯详情页, **When** 查看基本信息 tab, **Then** 不显示"预算金额"行

### User Story 2 — 标讯标题更名为项目名称 (Priority: P1)

标讯创建表单和标讯详情页中，"标讯标题" / "标题" 标签统一改为"项目名称"。

**Acceptance Scenarios**:
1. **Given** 用户打开人工录入对话框, **When** 查看第一个表单项, **Then** 标签显示"项目名称"
2. **Given** 用户打开标讯详情页基本信息 tab, **When** 查看第一个字段, **Then** 标签显示"项目名称"

### User Story 3 — 时间格式修正 (Priority: P1)

报名截止时间和开标时间显示/录入格式统一为 `yyyy-MM-dd HH:mm`。

**Acceptance Scenarios**:
1. **Given** 用户在创建表单中选择报名截止时间, **When** 使用 el-date-picker 选择, **Then** 格式为 yyyy-MM-dd HH:mm
2. **Given** 标讯详情页, **When** 查看报名截止时间和开标时间, **Then** 显示格式为 yyyy-MM-dd HH:mm

### User Story 4 — 客户类型选项修正 (Priority: P1)

客户类型下拉框选项从3项改为5项。

**Acceptance Scenarios**:
1. **Given** 用户打开客户类型下拉, **When** 点击下拉框, **Then** 显示5个选项：政府机关/事业单位/高校、央企、地方国企、民企、港澳台及外企

### User Story 5 — 来源平台字段隐藏 (Priority: P2)

标讯创建表单不展示"来源平台"字段；该字段在详情页根据实际来源自动显示。

**Acceptance Scenarios**:
1. **Given** 用户打开人工录入对话框, **When** 查看所有表单字段, **Then** 没有"来源平台"字段
2. **Given** 人工录入创建的标讯, **When** 查看详情页, **Then** 来源平台显示"人工录入"
3. **Given** 第三方平台拉取的标讯, **When** 查看详情页, **Then** 来源平台显示对应平台名称

## Requirements

### Functional Requirements
- **FR-001**: 标讯创建表单 MUST NOT 包含"预算金额"字段
- **FR-002**: 标讯详情页基本信息 tab MUST NOT 显示"预算金额"行
- **FR-003**: 创建表单标题字段标签 MUST 为"项目名称"（原"标讯标题"）
- **FR-004**: 详情页标题字段标签 MUST 为"项目名称"（原"标题"）
- **FR-005**: 报名截止时间与开标时间 MUST 使用 yyyy-MM-dd HH:mm 格式
- **FR-006**: 客户类型选项 MUST 为：政府机关/事业单位/高校、央企、地方国企、民企、港澳台及外企
- **FR-007**: 创建表单 MUST NOT 展示"来源平台"选择字段
- **FR-008**: 来源平台在详情页 MUST 根据 sourceType 自动显示对应文本

## Success Criteria
- **SC-001**: 创建表单和详情页均不显示预算金额
- **SC-002**: 所有"标讯标题"/"标题"标签已改为"项目名称"
- **SC-003**: 时间格式在所有展示位置均为 yyyy-MM-dd HH:mm
- **SC-004**: 客户类型下拉选项为5项
- **SC-005**: 创建表单无来源平台字段

## Assumptions
- 后端 budget 字段保留（数据库列不删除），仅前端不展示
- el-date-picker type=datetime 需显式设置 format 确保一致性
- 详情页时间已使用 formatTenderDateTime 函数支持显示完整时间
