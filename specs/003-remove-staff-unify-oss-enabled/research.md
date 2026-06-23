# Research Notes: 移除 staff 角色并统一用户启用状态为 OSS

## Decisions

### 1. 用户启用状态的数据源

**Decision**: 用户启用状态唯一来源为 OSS 同步结果（`UserEnabledDetector` 解析 `employeeStatus` / `status` / `del` 等字段）。本地 `User.enabled` 字段仅作为缓存/镜像，不再通过系统设置页修改。

**Rationale**: 
- 当前矛盾根因是本地启用状态与 OSS 状态不同步，且登录 gate 不看本地 enabled。
- 强制统一数据源可彻底消除"能登录但搜不到"、"本地停用仍能登录"等问题。
- OSS 是组织架构和人事状态的权威来源。

**Alternatives considered**:
- 本地启用优先 + OSS 同步覆盖：实现简单，但管理员手动启用会再次与 OSS 冲突。
- 双轨制（本地启用 AND OSS 在职）：逻辑复杂，容易产生交集/并集歧义。
- **Selected**: OSS 唯一源，本地只读展示。

### 2. staff 角色处理

**Decision**: 完全删除 `staff` 作为系统可识别角色。未映射到 7 个业务角色的 OSS 用户，在本地记录中角色置空或标记为未授权，且 `enabled=false`（如果同步创建了记录）。

**Rationale**:
- 用户明确 staff 是历史技术债，不应允许登录。
- 7 个业务角色已覆盖当前业务需求。
- 保留 staff 枚举会导致登录 gate、前端权限、测试数据多处需要特殊处理。

**Alternatives considered**:
- 保留 staff 但禁止登录：减少代码改动，但遗留角色继续污染角色目录和前端权限判断。
- 物理删除所有 staff 用户记录：过于激进，会丢失审计和操作历史。
- **Selected**: 删除角色定义，历史记录保留但禁用/标记为未授权。

### 3. 登录 gate 的校验时机

**Decision**: 
- 用户每次登录时，后端通过 `OssLoginFlowService` 实时（或刷新缓存）获取 OSS 角色和在职状态。
- 校验通过条件：OSS 认证成功 + 角色映射到 7 个业务角色之一 + OSS 在职。
- 登录成功后写回本地 `User.enabled`、`User.roleCode`、OSS 缓存。

**Rationale**:
- 避免本地缓存过期导致权限扩散或收缩。
- 与现有 `OssLoginFlowService.refreshPermissionCache` 机制一致，改动最小。

**Alternatives considered**:
- 仅依赖本地缓存角色：同步间隔内可能不一致。
- 每次请求都查 OSS：性能差，且对菜单权限缓存不友好。
- **Selected**: 登录时刷新，后续请求使用本地缓存。

### 4. 选人控件的角色过滤

**Decision**: 选人控件统一使用 `User.enabled=true` 作为唯一过滤条件，不额外按角色过滤。

**Rationale**:
- 用户明确要求统一列出所有启用用户。
- 角色是否适合某个岗位由业务流程/前端提示负责，不由通用选人接口拦截。

**Alternatives considered**:
- 每个 picker 声明允许角色集合：更精确，但会导致不同 picker 行为不一致。
- 按数据权限/部门过滤：可保留，但不替代角色过滤决策。
- **Selected**: 只按 enabled 过滤。

### 5. 本地账号兜底

**Decision**: `DefaultAdminInitializer`、`LocalDevAccountInitializer`、`E2eDemoDataInitializer` 创建的账号不走 OSS 校验，保持原有本地密码登录。E2E 中 `xiaowang` 角色从 `staff` 调整为 `admin_staff` 或其他有效角色。

**Rationale**:
- 系统需要不依赖外部 OSS 的兜底登录能力。
- 开发、测试、运维场景需要稳定账号。

## Open Questions Resolved

- **Q**: 本地设置页中的"启用/停用"开关怎么办？  
  **A**: 对 OSS 同步用户移除开关，仅展示 OSS 同步状态；本地初始化账号可保留（因为本地账号的启用不由 OSS 决定）。

- **Q**: 现有 staff 用户数据怎么处理？  
  **A**: 通过 Flyway 迁移脚本将 `role_code = 'staff'` 的用户更新为 `enabled = false` 并清空/重置 `role_code`，使其无法登录且不出现在选人控件中。

- **Q**: JWT 已发出后用户被 OSS 降级怎么办？  
  **A**: `UserDetailsServiceImpl.loadUserByUsername` 会检查 `enabled`；当 JWT 刷新或访问受保护资源时，若本地 `enabled=false` 则拒绝。由于登录时已刷新本地状态，首次访问即会触发拒绝。
