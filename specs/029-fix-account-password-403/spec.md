# Feature Specification: 修复平台账号密码查看权限异常类型误用

**Feature Branch**: `agent/gemini/fix-account-password-403`

**Created**: 2026-07-05

**Status**: Draft

**Input**: Sentry issue XIYU-N（14 天 6 次）显示 `GET /api/platform/accounts/{id}/password` 触发 `IllegalStateException`，被 `GlobalExceptionHandler` 吞成 409 "系统状态冲突，请刷新后重试"，并触发 Sentry 5xx 诊断路径（ERROR 级日志 + payload dump + Sentry 上报）。实际是业务权限校验，应抛 `AccessDeniedException` 走 403 路径。

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 非授权用户查看密码收到精确权限错误 (Priority: P1)

非管理员、非账号绑定联系人的用户尝试查看某个平台账号的密码时，应收到 HTTP 403 权限错误，前端能根据 403 状态码做对应提示（如"权限不足"或跳转），而不是收到误导性的 409 "系统状态冲突，请刷新后重试"。

**Why this priority**: 这是 Sentry XIYU-N 的核心噪声源（5/6 次错误来自此路径），直接影响用户体验和 Sentry 噪声告警质量。

**Independent Test**: 用一个非特权非联系人用户调用 `GET /api/platform/accounts/{id}/password`，断言响应状态码为 403。

**Acceptance Scenarios**:

1. **Given** 当前用户为 `bid-Team` 角色但不是该账号绑定联系人，**When** 调用 `GET /api/platform/accounts/{id}/password`，**Then** 响应状态码为 403，message 包含权限相关说明。
2. **Given** 当前用户为非特权角色且非 `bid-Team`（如 `bid-administration`），**When** 调用 `GET /api/platform/accounts/{id}/password`，**Then** 响应状态码为 403。
3. **Given** 当前用户为 `admin` / `/bidAdmin` / `bid-TeamLeader` 特权角色，**When** 调用 `GET /api/platform/accounts/{id}/password`，**Then** 响应状态码为 200，body 返回解密后的密码字符串。
4. **Given** 当前用户为 `bid-Team` 角色且为该账号绑定联系人，**When** 调用 `GET /api/platform/accounts/{id}/password`，**Then** 响应状态码为 200，body 返回解密后的密码字符串。

---

### User Story 2 - 未认证用户调用密码查看接口收到 403 (Priority: P2)

当请求到达 Service 层时 `currentUser == null`（理论上 Spring Security filter 应先拦截，但作为防御性校验保留），系统应返回 4xx 错误而非 5xx "系统状态冲突"。

**Why this priority**: 防御性校验，生产环境正常路径不应触发，但代码层面需正确分类。

**Independent Test**: 模拟 `currentUser == null` 调用 `getPassword(id, null)`，断言抛出 `AccessDeniedException` 而非 `IllegalStateException`。

**Acceptance Scenarios**:

1. **Given** `currentUser == null`，**When** 调用 `PlatformAccountService.getPassword(id, null)`，**Then** 抛出 `AccessDeniedException`，全局 handler 返回 403。

---

### User Story 3 - Sentry 不再上报业务权限校验失败 (Priority: P2)

权限校验失败属于业务可恢复错误，不应触发 5xx 诊断路径（ERROR 级日志 + 完整 payload dump + Sentry 上报），应改为 WARN 级日志、不上报 Sentry。

**Why this priority**: 直接降低 Sentry 噪声，让 Sentry 集中关注真正的系统缺陷。

**Independent Test**: 触发权限校验失败后，断言 `Sentry.captureException` 未被调用（通过日志级别或 mock 验证），且日志级别为 WARN。

**Acceptance Scenarios**:

1. **Given** 调用 `GET /api/platform/accounts/{id}/password` 触发权限校验失败，**When** 全局异常处理器处理该异常，**Then** 走 `AccessDeniedException` handler（WARN 级日志，不上报 Sentry），不走 `IllegalStateException` handler（ERROR 级日志 + 上报 Sentry）。

---

### Edge Cases

- 账号 `id` 不存在时（`IllegalArgumentException` → 400），行为不变，不在本次修改范围。
- `@Auditable` 审计切面行为不变：审计日志仍会记录 `VIEW_PASSWORD` 操作（无论成功或失败），本次不修改切面。
- `passwordEncryptionUtil.decrypt` 失败时（解密异常），行为不变，不在本次修改范围。
- 当前用户角色码解析（`EffectiveRoleResolver.resolveRoleCode`）行为不变，仅替换异常类型。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: `PlatformAccountService.getPassword` 在 `currentUser == null` 时必须抛出 `AccessDeniedException`（替代 `IllegalStateException`），返回 403。
- **FR-002**: `PlatformAccountService.getPassword` 在非特权 `bid-Team` 角色但非账号绑定联系人时，必须抛出 `AccessDeniedException`，返回 403。
- **FR-003**: `PlatformAccountService.getPassword` 在非特权且非 `bid-Team` 角色时，必须抛出 `AccessDeniedException`，返回 403。
- **FR-004**: 异常类型变更不得影响权限判定逻辑：特权角色（admin / /bidAdmin / bid-TeamLeader）和账号绑定联系人（`bid-Team` 角色）的放行规则保持不变。
- **FR-005**: 异常类型变更不得影响 `@Auditable` 审计切面的行为：仍会记录 `VIEW_PASSWORD` 操作日志。
- **FR-006**: 异常类型变更不得影响 `GlobalExceptionHandler` 现有 `AccessDeniedException` handler 的行为：返回 403 + WARN 级日志 + 不上报 Sentry。
- **FR-007**: 异常消息内容保持原意（"Authentication required" / "Only administrators or the account's contact person can view the password" / "Only administrators can view account passwords"），仅替换异常载体类型。

### Key Entities *(include if feature involves data)*

- **PlatformAccount**: 平台账号实体，包含加密密码字段。
- **User**: 当前操作用户实体，包含 `username` 和角色信息。
- **RoleProfile**: 角色配置，决定用户是否为特权角色（admin / /bidAdmin / bid-TeamLeader）或投标专员（bid-Team）。
- **PlatformAccountViewerPolicy**: 纯静态策略类，封装账户查看权限判定规则（已存在的类，本次不修改其结构）。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Sentry 在密码查看权限校验失败时不再接收到 `IllegalStateException` 上报（XIYU-N issue 在本次部署后停止增长）。
- **SC-002**: 密码查看权限校验失败时，HTTP 响应状态码为 403（而非 409）。
- **SC-003**: 合法用户（特权角色 + 账号绑定联系人）查看密码的功能行为完全不变（成功返回解密密码）。
- **SC-004**: 现有 `PlatformAccountService` 相关单元测试与集成测试全部通过，无回归。

## Assumptions

- 现有 `GlobalExceptionHandler.handleAccessDeniedException` 已经返回 403 + WARN 级日志 + 不上报 Sentry，本次不修改 handler。
- 现有 `@Auditable` 切面在方法抛异常时仍会记录审计日志，本次不修改切面。
- `EffectiveRoleResolver.resolveRoleCode` 行为不变，仅替换异常类型。
- 本次不修改 `PlatformAccountViewerPolicy` 类（虽然 `checkCanReturnAccount` 也存在同类问题，但 Sentry 未上报，作为技术债单独处理）。
- 本次不做权限校验下沉到 Policy 的对称化重构（仅在 Service 层替换异常类型），保持改动最小。
- 前端只要识别 4xx 状态码即可，不依赖 message 字段具体内容做分支。
