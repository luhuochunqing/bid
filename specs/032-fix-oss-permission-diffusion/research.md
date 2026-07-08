# Research: 修复 OSS 用户权限扩散导致越权看所有菜单

**Date**: 2026-07-08
**Feature**: specs/032-fix-oss-permission-diffusion

## 研究问题清单

### Q1: OSS 用户标识如何从前端获取？

**Decision**: 在 `AuthResponse` DTO 中新增 `isOssUser` 字段，由 `AuthService.buildAuthResponse` 根据 `user.getExternalOrgSourceApp()` 填充。

**Rationale**: 
- 当前 `AuthResponse` 无 `isOssUser` 字段，前端无法识别 OSS 用户。
- 后端 `UserDetailsServiceImpl` 已用 `user.getExternalOrgSourceApp() != null && !user.getExternalOrgSourceApp().isBlank()` 判断 OSS 用户，逻辑成熟可靠。
- 前端 `hasPermission` 需要该标识做 `all` 短路守卫（User Story 3）。
- 新增字段比复用 `roleCode` 推断更显式、更安全（不依赖 roleCode 字符串匹配）。

**Alternatives considered**:
- 复用 `roleCode === 'admin'` 推断 OSS 用户：不安全，本地 admin 也是 roleCode=admin。
- 前端自行调用 `/api/auth/me` 二次获取：增加网络开销，且当前 `/api/auth/me` 也不返回该字段。
- 在 JWT token 中塞入 `isOssUser` claim：改动面更大，需修改 JWT 生成逻辑。

### Q2: 修改点 1（UserDetailsServiceImpl）的精确改动？

**Decision**: 在 L120 扩散分支和 L143 admin fallback 前加 `!isOssUser` 守卫。

**Rationale**:
- L120 扩散分支：`if (menuPermissions.contains("all") || "admin".equalsIgnoreCase(roleCode) || User.Role.ADMIN == legacyRole)` → 改为 `if (!isOssUser && (menuPermissions.contains("all") || "admin".equalsIgnoreCase(roleCode) || User.Role.ADMIN == legacyRole))`
- L143 admin fallback：`if (User.Role.ADMIN == legacyRole || "admin".equalsIgnoreCase(roleCode))` → 改为 `if (!isOssUser && (User.Role.ADMIN == legacyRole || "admin".equalsIgnoreCase(roleCode)))`
- L130-139 catalog 基线权限补充：对 OSS admin 用户会合并 admin seed `["all"]`，也需加守卫。实际上 `usingOssCachedPermissions=true` 时该分支已会执行，需改为 `if (!isOssUser && roleCode != null && ...)` 或对 OSS 用户跳过 admin seed 合并。

**Alternatives considered**:
- 删除扩散逻辑：破坏本地 admin 体验，不可行。
- 把扩散逻辑移到单独方法：过度重构，违反 Constitution VIII（Boring Proven Patterns）。
- 新增 `OssUserPermissionPolicy` 纯核心类：第一层最小修复不需要新抽象，守卫即可。

### Q3: 修改点 2（DataScopeConfigService.getRoleMenuPermissions）的精确改动？

**Decision**: 在 L143-150 合并 catalog seed 时，对 OSS 用户跳过 admin seed 合并，或改为只合并非 admin 角色的 seed。

**Rationale**:
- 当前 L144-149：`RoleProfileCatalog.definitionForCode(cachedRoleCode.get())` 对 admin 返回 seed `menuPermissions=["all"]`，合并后前端拿到 `["all"]`。
- 改动：对 OSS 用户，如果 `cachedRoleCode` 是 admin，不合并 admin seed（因为 admin seed 只有 `["all"]`，OSS 用户不应有 `all`）。
- 更通用的守卫：OSS 用户只合并非 `["all"]` 的权限键，或直接不合并 catalog seed（OSS 权限已包含菜单码）。

**Alternatives considered**:
- 完全不合并 catalog seed：可能丢失 `performance.manage`/`warehouse.manage` 等管理权限点（CO-438 注释说明 OSS menuCode 不含这些）。
- 只过滤 `"all"`：保留其他 catalog seed 权限，最小改动。

**最终选择**: 只过滤 `"all"` —— OSS 用户合并 catalog seed 时，跳过 `"all"` 权限键。这样既保留 CO-438 的管理权限点合并，又避免 `all` 扩散。

### Q4: 修改点 3（前端 hasPermission）的精确改动？

**Decision**: 在 `src/stores/user.js` 的 `hasPermission` getter 中，对 OSS 用户不短路放行 `all`。

**Rationale**:
- 当前实现：`if (perms.includes('all')) return true`
- 改动：`if (perms.includes('all') && !state.currentUser?.isOssUser) return true`
- 需要先完成 Q1（AuthResponse 暴露 isOssUser 字段）。

**Alternatives considered**:
- 完全删除 `all` 短路逻辑：破坏本地 admin 体验。
- 在后端过滤掉 OSS 用户的 `all`：已在修改点 2 处理，前端是 defense-in-depth。

### Q5: 测试策略？

**Decision**: 
- 后端单元测试：`UserDetailsServiceImplTest` 新增 3 个用例（OSS admin 不扩散 / 本地 admin 不变 / OSS 非 admin 用户不受影响）
- 后端单元测试：`DataScopeConfigServiceTest` 新增 2 个用例（OSS admin 不合并 `all` / 本地 admin 不变）
- 前端单元测试：`src/stores/user.spec.js` 新增 2 个用例（OSS 用户 hasPermission 不短路 / 本地 admin 短路不变）
- E2E：P2 优先级，可选

**Rationale**: 遵循 TDD（Constitution III），先写测试再实现。单元测试覆盖核心逻辑，E2E 验证端到端行为。

## 决策汇总

| 问题 | 决策 | 风险 |
|---|---|---|
| Q1 OSS 用户标识 | AuthResponse 新增 isOssUser 字段 | 低（新增字段，向后兼容） |
| Q2 UserDetailsServiceImpl | L120/L130/L143 加 !isOssUser 守卫 | 低（只加条件，不改逻辑） |
| Q3 DataScopeConfigService | OSS 用户合并 catalog seed 时跳过 "all" | 低（只过滤一个权限键） |
| Q4 前端 hasPermission | 对 OSS 用户不短路 all | 低（只加条件） |
| Q5 测试策略 | 后端 5 个用例 + 前端 2 个用例 | 无风险 |
