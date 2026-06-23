# Feature Specification: 移除 staff 角色并统一用户启用状态为 OSS

**Feature Branch**: `003-remove-staff-unify-oss-enabled`

**Created**: 2026-06-23

**Status**: Draft

**Input**: User description: "本地不应该影响用户的启用，统一走 OSS；staff 是系统遗留的历史技术债，应该统一删除；系统只保留投标管理员、投标组长、投标系统管理员、投标专员、投标项目负责人、行政人员、跨部门协同人员 7 个角色。"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 普通员工无法登录系统 (Priority: P1)

一名在 OSS 侧角色为普通员工（未映射到系统业务角色）的员工尝试登录西域数智化投标管理平台，系统应明确拒绝其登录，并提示无权限。

**Why this priority**: 当前系统允许 staff 角色登录，与业务规则冲突，导致普通员工能看到不该看的菜单和数据，存在权限扩散风险。

**Independent Test**: 使用一个 OSS 返回角色为普通员工的账号登录，验证登录接口返回无权限错误，且不会生成 JWT。

**Acceptance Scenarios**:

1. **Given** 用户在 OSS 侧角色未映射到 7 个业务角色之一，**When** 该用户尝试登录，**Then** 系统拒绝登录并返回"当前账号无系统访问权限"类提示。
2. **Given** 用户已持有有效 JWT 但其账号在 OSS 侧被降级为普通员工，**When** 该用户访问受保护资源，**Then** 系统拒绝访问并要求重新登录。
3. **Given** 系统原有角色目录中存在 staff 角色定义，**When** 规格实施后，**Then** staff 不再作为可登录或可配置角色出现。

---

### User Story 2 - 用户启用状态完全由 OSS 决定 (Priority: P1)

系统管理员不再通过"系统设置-账户启用/停用"来控制用户能否被搜索或登录；用户的启用状态完全由 OSS 同步的在职状态决定。

**Why this priority**: 当前本地启用状态与 OSS 状态不同步，出现"能登录但搜不到"或"本地停用仍能登录"的矛盾，必须统一单一数据源。

**Independent Test**: 在 OSS 侧标记一名用户为离职/禁用，验证 5 分钟内该用户无法登录且不再出现在选人控件中；在 OSS 侧恢复在职，验证其恢复可登录和可被选人。

**Acceptance Scenarios**:

1. **Given** 一名用户在 OSS 侧为在职且映射到有效角色，**When** OSS 同步完成后，**Then** 该用户可以登录并出现在选人控件中。
2. **Given** 一名用户在 OSS 侧为离职，**When** OSS 同步完成后，**Then** 该用户无法登录且不出现在选人控件中，即使管理员曾手动"启用"该账号。
3. **Given** 系统设置中存在账户启用/停用开关，**When** 管理员查看 OSS 同步用户时，**Then** 该开关被移除或置灰，仅作为状态展示。

---

### User Story 3 - 选人控件统一返回启用用户 (Priority: P2)

在项目负责人转派、任务分配、评审人选择等场景中，选人控件统一返回所有由 OSS 判定为"启用"的用户，不再按角色做二次过滤。

**Why this priority**: 保持选人逻辑简单一致，避免同一用户在不同场景下出现/消失的不一致体验；角色是否适合某个岗位由业务规则或前端提示负责，不由选人接口强制拦截。

**Independent Test**: 在一个启用账号为 staff 的场景下（实施前遗留数据），验证该账号在选人控件中不出现；在有效角色启用账号场景下，验证其出现在所有选人控件中。

**Acceptance Scenarios**:

1. **Given** 用户 A 为启用状态且角色为投标专员，**When** 项目负责人转派弹窗加载候选人，**Then** 用户 A 出现在列表中。
2. **Given** 用户 B 为启用状态且角色为行政人员，**When** 任务分配弹窗加载候选人，**Then** 用户 B 出现在列表中。
3. **Given** 用户 C 为非启用状态，**When** 任何选人控件加载候选人，**Then** 用户 C 不出现在列表中。

---

### User Story 4 - 系统保留账号不受 OSS 同步影响 (Priority: P2)

本地初始化产生的系统管理员（如 DefaultAdminInitializer 创建的 admin）和本地开发测试账号，仍按原有机制管理，不依赖 OSS 在职状态。

**Why this priority**: 系统需要兜底账号用于运维、开发和应急登录，不能完全依赖外部 OSS 可用性。

**Independent Test**: 断开 OSS 模拟或仅在本地环境启动时，验证系统管理员账号仍可登录。

**Acceptance Scenarios**:

1. **Given** 一个通过本地初始化创建的系统管理员账号，**When** OSS 不可用时，**Then** 该账号仍可通过本地密码登录。
2. **Given** 该本地系统管理员账号，**When** OSS 同步运行时，**Then** 不会被错误地禁用或删除。

---

### Edge Cases

- 一个用户同时存在于多个 OSS 组织或岗位时，如何聚合角色？（按当前映射规则取最高优先级或合并去重）
- OSS 同步失败或返回空角色时，系统如何兜底？（保留上一次有效角色还是拒绝登录？）
- 现有 staff 用户的迁移：是自动禁用、删除本地记录，还是保留记录但标记为无效？
- 本地开发环境没有 OSS 时，如何验证登录和选人逻辑？（依赖 LocalDevAccountInitializer 和 E2E 测试数据）
- 用户从业务角色被 OSS 调整为普通员工后，已发出的 JWT 和正在进行的会话如何处理？（下次请求时拒绝）

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系统 MUST 仅允许以下 7 个内部角色对应的用户登录：`bid_admin`、`bid_lead`、`admin`、`bid_specialist`、`sales`、`admin_staff`、`bid_other_dept`。
- **FR-002**: 系统 MUST 删除 `staff` 角色在角色目录、权限种子、前端路由权限判断中的全部定义。
- **FR-003**: 用户的启用状态 MUST 唯一来源于 OSS 同步结果；本地管理界面不再提供修改 OSS 用户启用状态的入口。
- **FR-004**: 对于 OSS 同步用户，系统 MUST 在每次登录或令牌刷新时校验其 OSS 角色是否仍映射到 7 个有效角色之一。
- **FR-005**: 对于未映射到任何有效角色的 OSS 用户，系统 MUST 拒绝其登录，且不创建新的本地用户记录（或标记为不可登录状态）。
- **FR-006**: 选人控件（项目负责人、任务分配、评审人、 mentions 搜索等）MUST 统一返回本地 `enabled=true` 的用户，不额外按角色过滤。
- **FR-007**: 本地初始化账号（系统管理员、开发账号、E2E 测试账号）MUST 保持可登录，不受 OSS 在职状态影响；E2E 测试中不再使用 staff 角色。
- **FR-008**: 系统 SHOULD 在登录失败时给出明确提示：角色未授权 vs 账号已停用 vs 认证失败。
- **FR-009**: 前端系统设置页中的"启用/停用账户"开关 SHOULD 被移除或仅作为只读状态展示。

### Key Entities

- **User（用户）**: 代表系统中的用户账号，包含 `enabled`（启用状态，由 OSS 同步维护）、`roleCode`（内部角色码，来自 OSS 角色映射）、`externalOrgSourceApp`（OSS 来源标识）等属性。
- **RoleProfileCatalog（角色目录）**: 定义系统支持的角色集合、可访问菜单和权限；实施后将只保留 7 个业务角色，删除 staff。
- **OSS Role Mapping（OSS 角色映射）**: 定义 OSS 返回的角色码如何映射到内部角色码；当前映射关系为：
  - `/bidAdmin` → `bid_admin`
  - `bid-TeamLeader` → `bid_lead`
  - `bid-SystemAdmin` → `admin`
  - `bid-Team` → `bid_specialist`
  - `bid-projectLeader` → `sales`
  - `bid-administration` → `admin_staff`
  - `bid-otherDept` → `bid_other_dept`

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% 的 staff 角色相关代码、测试、前端权限判断在实施后被移除或替换，仓库中不再存在 `User.Role.STAFF`、`ROLE_STAFF`、`STAFF_CODE` 等有效引用。
- **SC-002**: 100% 的 OSS 同步用户登录请求都经过有效角色校验；使用未映射/普通员工 OSS 角色登录的请求 100% 被拒绝。
- **SC-003**: 用户的启用状态与 OSS 在职状态保持一致；启用状态变更在 OSS 同步后 5 分钟内反映到登录和选人控件。
- **SC-004**: 所有选人控件返回结果一致：只要用户为启用状态即出现，不论其具体业务角色；非启用用户均不出现。
- **SC-005**: 本地系统管理员和 E2E 测试账号在 OSS 不可用时仍可正常登录，登录成功率保持 100%。
- **SC-006**: 因角色/启用状态不一致导致的用户可见性 bug 报告降低至 0。

## Assumptions

- OSS 侧的角色码和在职状态字段保持稳定，不会在本规格实施期间发生破坏性变更。
- 当前 `User.enabled` 字段将继续存在，但其写入来源仅限 OSS 同步和本地初始化器，本地管理员不再修改。
- staff 用户的历史数据选择"保留本地记录但禁用登录"而非物理删除，以便审计和追溯。
- 本规格不修改业务上"谁能被指派为什么岗位"的规则，该规则由后续业务模块自行在前端或流程校验中实现。
- 7 个业务角色与 OSS 角色码的映射关系已经由当前 `JobRoleLookupResolver` 覆盖，本规格不再新增映射。
