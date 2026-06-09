# Feature Specification: 标讯UI操作按钮一致性优化

**Feature Branch**: `019-bid-ui-optimize`

**Created**: 2026-06-09

**Status**: Draft

**Input**: Bug修复：创建标讯保存后页面按钮逻辑不一致 — 应统一跳转详情页由 actionMatrix 控制（Gitee Issue IJT8WG）

## User Scenarios & Testing

### User Story 1 — 管理员/组长分配后底部按钮正确 (P1)

管理员或组长创建标讯 → 保存 → 点击「分配」选择项目负责人 → 分配后底部应只显示「返回列表」按钮，不得出现「下一步」/「提交」。

**Why this priority**: 直接关联到管理员越权操作风险，当前创建页 `canProceedToNext` 绕过 actionMatrix 对 admin_lead 放行了 TRACKING 状态的底部按钮。

**Independent Test**: 以 bid_admin 登录，创建标讯并分配，在创建页可见底部仅「返回列表」按钮。

**Acceptance Scenarios**:

1. **Given** 用户为 bid_admin/bid_lead，**When** 创建标讯后分配项目负责人（状态变为 TRACKING），**Then** 创建页底部只显示「返回列表」按钮，不显示「下一步」/「提交」
2. **Given** 用户为 sales（项目负责人），**When** 创建页中标讯状态为 TRACKING 且当前用户为 projectManagerId，**Then** 底部正常显示「下一步」/「提交」

### User Story 2 — 非管理员角色创建保存后跳转详情页 (P1)

sales / bid_specialist 创建并保存标讯后，应统一跳转到详情页，由 actionMatrix 控制详情页的按钮展示。

**Why this priority**: Issue 问题二的核心 —— 创建后停留在创建页与详情页体验割裂，且创建页缺少创建人按钮逻辑。

**Independent Test**: 以 sales 登录，创建标讯保存后自动跳转到 `/bidding/{id}` 详情页。

**Acceptance Scenarios**:

1. **Given** 用户为 sales/bid_specialist，**When** 创建标讯并保存成功，**Then** 自动跳转到该标讯详情页 (`/bidding/{id}`)
2. **Given** 跳转到详情页后，**Then** 由 actionMatrix 统一控制头部/底部按钮展示

### User Story 3 — 创建人在详情页显示编辑/删除按钮 (P1)

sales / bid_specialist 作为创建人，在 PENDING_ASSIGNMENT 状态下进入详情页，头部应显示「编辑」和「删除」按钮。

**Why this priority**: 后端 TenderCommandAccessGuard 已允许创建人编辑/删除自己的 PENDING_ASSIGNMENT 标讯，但前端 actionMatrix 纯角色矩阵未传创建人 ID，导致 sales/specialist 的创建人身份被忽略。

**Independent Test**: 以 sales 登录，创建标讯后跳转到详情页，可见头部「编辑」和「删除」按钮。

**Acceptance Scenarios**:

1. **Given** 用户为 sales，**When** 访问自己创建的 PENDING_ASSIGNMENT 标讯详情页，**Then** 头部显示「编辑」和「删除」按钮
2. **Given** 用户为 bid_specialist，**When** 访问自己创建的 PENDING_ASSIGNMENT 标讯详情页，**Then** 头部显示「编辑」和「删除」按钮
3. **Given** 用户为 sales，**When** 访问其他人创建的 PENDING_ASSIGNMENT 标讯详情页，**Then** 头部无任何按钮
4. **Given** 用户为 admin/bid_admin/bid_lead，**When** 访问任意 PENDING_ASSIGNMENT 标讯，**Then** 头部显示「分配」和「删除」按钮（保持现有行为）

### Edge Cases

- 创建人身份 + 非 PENDING_ASSIGNMENT 状态：不应显示编辑/删除按钮
- bid_lead 角色作为创建人：已有独立逻辑（filter delete），编辑权限应与 admin_lead 组一致
- 管理员/组长作为创建人的场景：现有 admin_lead 逻辑已覆盖，不影响

## Requirements

### Functional Requirements

- **FR-001**: `actionMatrix.js` 的 `getHeaderActions` 和 `getBottomActions` 必须接收创建人 ID 参数，在 PENDING_ASSIGNMENT 状态下当当前用户为创建人时，sales/bid_specialist 角色获得编辑和删除权限
- **FR-002**: `TenderCreatePage.vue` 保存后必须跳转到详情页 `/bidding/{id}`，不区分角色
- **FR-003**: `TenderCreatePage.vue` 的 `canProceedToNext` 计算属性必须移除对 admin_lead 的无条件放行，TRACKING 状态下仅 creator 为 projectManagerId 的销售可操作
- **FR-004**: actionMatrix 的 `HEADER_MATRIX.PENDING_ASSIGNMENT.sales/bid_specialist` 在创建人身份下应包含 `['edit', 'delete']`
- **FR-005**: actionMatrix 的 `HEADER_MATRIX.PENDING_ASSIGNMENT` 中 admin_lead 保持现有行为不变

### Key Entities

- **标讯 (Tender)**: 包含 status（状态）、creatorId（创建人 ID）、projectManagerId（项目负责人 ID）等属性
- **操作按钮矩阵 (actionMatrix)**: 纯核心函数，根据 status + role + creatorId 返回可见按钮列表

## Success Criteria

- **SC-001**: 管理员/组长分配后在创建页不会看到越权按钮
- **SC-002**: 所有角色创建标讯保存后统一跳转到详情页
- **SC-003**: 创建人在详情页 PENDING_ASSIGNMENT 状态下能看到编辑/删除按钮
- **SC-004**: 非创建人在 PENDING_ASSIGNMENT 状态下看不到编辑/删除按钮（admin/lead 除外）
- **SC-005**: actionMatrix 现有测试全部通过，新增创建人场景测试覆盖

## Assumptions

- 后端 API 返回的 tender detail 已包含 `creatorId` 字段
- userStore 已提供 `currentUser.id` 用于获取当前用户 ID
- 创建页保存后跳转详情页的逻辑不改变已有编辑模式（isEditMode）下的行为
