# Feature Specification: 标讯详情页 Bug 修复

**Feature Branch**: `015-bidding-detail-bugfix`

**Created**: 2026-06-01

**Status**: Draft

## User Scenarios & Testing

### User Story 1 - 标讯详情页导航修复 (Priority: P1)

用户在标讯详情页(`/bidding/{id}`)能看到返回按钮，点击后回到标讯列表。切换菜单再切回标讯详情页时页面正常显示，刷新时保持在当前页而不是跳转到工作台。

**Independent Test**: 打开详情页 → 点击返回 → 回到列表。切换菜单 → 回到标讯 → 页面正常。刷新 → 保持在详情页。

**Acceptance Scenarios**:
1. **Given** 用户在标讯详情页，**When** 点击返回按钮，**Then** 回到标讯列表页
2. **Given** 用户在标讯详情页，**When** 切换到其他菜单再切回，**Then** 页面正常显示
3. **Given** 用户在标讯详情页，**When** 刷新页面，**Then** 保持在当前详情页

---

### User Story 2 - 编辑按钮与底部按钮修复 (Priority: P1)

未分配的标讯在详情页底部显示【编辑】按钮（不显示【保存】【取消】）。支持创建人、投标管理员、投标组长编辑基本信息。

**Independent Test**: 打开未分配标讯详情页 → 底部显示【编辑】按钮 → 点击可编辑基本信息。

**Acceptance Scenarios**:
1. **Given** 标讯状态为 PENDING_ASSIGNMENT，**When** 创建人/投标管理员/投标组长查看详情，**Then** 底部显示【编辑】按钮
2. **Given** 标讯状态为 PENDING_ASSIGNMENT，**When** 用户查看详情，**Then** 底部不显示【保存】【取消】按钮
3. **Given** 非授权角色查看待分配标讯，**When** 用户查看详情，**Then** 底部不显示【编辑】按钮

---

### User Story 3 - 移除收藏功能 (Priority: P2)

去掉标讯收藏功能，侧边栏菜单不展示收藏菜单项。

**Independent Test**: 标讯列表页无收藏按钮，侧边栏无收藏菜单。

**Acceptance Scenarios**:
1. **Given** 用户在标讯列表页，**When** 查看标讯行，**Then** 无收藏按钮
2. **Given** 用户查看侧边栏菜单，**Then** 无收藏相关菜单项

---

### User Story 4 - 头部信息卡补充 (Priority: P2)

标讯详情页头部信息卡展示标讯ID、创建人、创建时间。

**Independent Test**: 打开标讯详情页 → 头部信息卡显示标讯ID、创建人、创建时间。

**Acceptance Scenarios**:
1. **Given** 用户打开标讯详情页，**When** 查看头部信息卡，**Then** 展示标讯ID
2. **Given** 用户打开标讯详情页，**When** 查看头部信息卡，**Then** 展示创建人和创建时间

---

## Requirements

### Functional Requirements

- **FR-001**: 标讯详情页 MUST 显示返回按钮，点击后导航到标讯列表
- **FR-002**: 标讯详情页 MUST 在路由切换和刷新时保持正确的 URL 和页面状态
- **FR-003**: 待分配状态标讯 MUST 在底部显示【编辑】按钮（仅创建人/投标管理员/投标组长）
- **FR-004**: 待分配状态标讯 MUST 不显示【保存】【取消】按钮
- **FR-005**: 标讯收藏功能 MUST 被移除，包含收藏按钮和收藏菜单
- **FR-006**: 标讯详情页头部信息卡 MUST 展示标讯ID、创建人、创建时间
- **FR-007**: 编辑按钮 MUST 打开编辑表单，允许修改标讯基本信息

### Key Entities

- **Tender (标讯)**: 标讯实体，包含 ID、标题、状态、创建人、创建时间等字段
- **DetailPage**: 标讯详情页，包含头部信息卡、底部操作区、Tab 面板
- **RoleProfile**: 角色权限配置，控制编辑按钮的可见性

## Success Criteria

### Measurable Outcomes

- **SC-001**: 用户从详情页返回列表页步骤不超过1次点击
- **SC-002**: 刷新详情页后 URL 保持不变，内容正常加载
- **SC-003**: 待分配标讯详情页【编辑】按钮按角色正确显隐
- **SC-004**: 收藏功能相关代码完全移除，前端无残留引用
- **SC-005**: 头部信息卡展示标讯ID、创建人、创建时间三项数据

## Assumptions

- 返回按钮使用现有 BackButton 组件（`showBack` meta 属性）实现
- 路由刷新问题通过正确的路由配置修复（确保 `/bidding/{id}` 有独立路由）
- 编辑功能复用现有 `TenderRequest` DTO 和 `updateTender` 端点
- 收藏功能在前端和后端均无数据库依赖，纯前端移除
- 头部信息卡数据来源于 `TenderDTO` 的现有字段（id、createdBy、createdAt）
