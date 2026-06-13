# Feature Specification: 标讯收藏功能

**Feature Branch**: `011-tender-favorites`

**Created**: 2026-05-27

**Status**: Draft

**Input**: User description: "标讯收藏功能。用户可以在标讯列表和详情页点击收藏按钮，把感兴趣的标讯加入个人收藏列表。已收藏的标讯在列表页显示收藏状态图标。收藏列表页面可以查看所有已收藏的标讯，支持按收藏时间排序和取消收藏。后端用 Spring Boot + MySQL，前端用 Vue 3 + Element Plus。前后端都加。"

## User Scenarios & Testing

### User Story 1 - 标讯列表页收藏/取消收藏 (Priority: P1)

用户在浏览标讯列表时，可以对感兴趣的标讯一键收藏。已收藏的标讯在列表中有一个醒目的已收藏图标，方便用户快速识别。

**Why this priority**: 列表页是用户浏览标讯的主要入口，在此处直接收藏是最高频的使用路径，是收藏功能的核心体验。

**Independent Test**: 用户登录后打开标讯列表页，点击某条标讯的收藏按钮，该按钮变为已收藏状态。再次点击则取消收藏，按钮恢复为未收藏状态。无需进入详情页即可完成操作。

**Acceptance Scenarios**:

1. **Given** 用户已登录并打开标讯列表页，**When** 用户点击某条未收藏标讯旁的收藏按钮，**Then** 该标讯状态变为已收藏，按钮图标切换为已收藏状态
2. **Given** 用户已登录并打开标讯列表页，**When** 用户点击某条已收藏标讯旁的收藏按钮，**Then** 该标讯状态变为未收藏，按钮图标恢复为未收藏状态
3. **Given** 用户未登录，**When** 用户点击收藏按钮，**Then** 系统提示用户先登录

---

### User Story 2 - 标讯详情页收藏/取消收藏 (Priority: P1)

用户进入标讯详情页后，可以通过收藏按钮对当前标讯进行收藏或取消收藏操作。详情页的收藏状态与列表页同步。

**Why this priority**: 详情页是用户深入了解标讯的页面，在此处收藏符合用户"先看详情再决定收藏"的自然行为路径。

**Independent Test**: 用户登录后打开某标讯的详情页，点击收藏按钮，然后返回列表页，该标讯在列表页中也显示为已收藏状态。

**Acceptance Scenarios**:

1. **Given** 用户打开某标讯的详情页且该标讯未收藏，**When** 用户点击收藏按钮，**Then** 按钮变为已收藏状态，并给出成功反馈
2. **Given** 用户打开某标讯的详情页且该标讯已收藏，**When** 用户点击取消收藏按钮，**Then** 按钮变为未收藏状态，并给出成功反馈
3. **Given** 用户在详情页收藏/取消收藏后，**When** 用户返回标讯列表页，**Then** 列表页显示的收藏状态与详情页一致

---

### User Story 3 - 收藏列表页管理收藏 (Priority: P2)

用户可以通过一个专门的"我的收藏"页面查看所有已收藏的标讯。在该页面中可以取消收藏，并支持按收藏时间排序。

**Why this priority**: 收藏列表为用户提供了一个集中管理收藏标的入口，是收藏功能的价值放大器。排序功能帮助用户快速找到近期收藏的标讯。

**Independent Test**: 用户登录后进入"我的收藏"页面，可以看到所有已收藏的标讯列表。点击取消收藏按钮，该标讯从列表中移除。支持按收藏时间倒序排列。

**Acceptance Scenarios**:

1. **Given** 用户已收藏多条标讯，**When** 用户进入"我的收藏"页面，**Then** 显示所有已收藏的标讯列表，每项展示标讯标题和收藏时间
2. **Given** 用户在"我的收藏"页面，**When** 用户点击某标讯旁的取消收藏按钮，**Then** 该标讯从收藏列表中移除
3. **Given** 用户在"我的收藏"页面，**When** 用户切换排序方式为"按收藏时间倒序"，**Then** 列表按收藏时间从新到旧排列
4. **Given** 用户暂无收藏，**When** 用户进入"我的收藏"页面，**Then** 显示空状态提示，引导用户去标讯列表添加收藏

---

### Edge Cases

- 网络异常时收藏/取消收藏操作的处理：显示加载状态，操作失败时给出友好的错误提示
- 用户重复点击收藏按钮时的防抖处理：防止短时间内多次调用接口
- 跨页面收藏状态同步：同一标讯在多个列表页/详情页中同时打开时，任一页面的收藏状态变更应能在其他页面刷新后同步
- 标讯被删除后，用户收藏列表中该条目的展示：应显示"标讯已下架"等提示，保留收藏记录但不跳转到详情页
- 收藏数量上限：如需要，系统可设置单人收藏上限（初始版本建议不设限）

## Requirements

### Functional Requirements

- **FR-001**: System MUST allow authenticated users to add a tender to their favorites
- **FR-002**: System MUST allow authenticated users to remove a tender from their favorites
- **FR-003**: System MUST display the current favorite status (favorited/unfavorited) in the tender list view
- **FR-004**: System MUST display the current favorite status (favorited/unfavorited) in the tender detail view
- **FR-005**: System MUST provide a dedicated page listing all tenders favorited by the current user
- **FR-006**: System MUST support sorting the favorites list by favorite creation time (descending by default, ascending optional)
- **FR-007**: System MUST scope favorites per user -- each user sees only their own favorites
- **FR-008**: System MUST persist favorite status across sessions (survive page refresh and re-login)
- **FR-009**: System MUST prevent duplicate favorites (same user + same tender can only be favorited once)
- **FR-010**: System MUST provide visual feedback on successful add/remove operations
- **FR-011**: System MUST gracefully handle unauthenticated access -- redirect to login or prompt authentication

### Key Entities

- **TenderFavorite**: 用户对标讯的收藏记录，包含用户 ID、标讯 ID、收藏时间。每个用户对同一标讯只能有一条收藏记录。
- **Tender (标讯)**: 现有的标讯实体，收藏功能需要在其详情和列表展示中增加收藏状态字段（只读，由收藏服务提供）。

## Success Criteria

### Measurable Outcomes

- **SC-001**: Users can add/remove a tender to/from favorites with a single click, and see the result in under 1 second
- **SC-002**: Users can view all their favorited tenders in the dedicated favorites page
- **SC-003**: Favorite status is consistent across the list page, detail page, and favorites page (after page reload)
- **SC-004**: System correctly scopes favorites per user -- user A's favorites are never visible to user B
- **SC-005**: The favorites page loads and displays results within 2 seconds for up to 200 favorited tenders

## Assumptions

- 用户已登录是收藏功能的前提条件；未登录用户点击收藏时提示登录
- 收藏功能对所有登录角色开放，无需额外的角色权限控制
- 收藏的标讯在被删除后，收藏记录保留但标记为已失效
- 收藏功能使用现有的用户认证体系（JWT Token）
- 收藏数量在 MVP 阶段不设上限
- 前端使用 Element Plus 的图标组件（如 Star 图标）表示收藏状态
