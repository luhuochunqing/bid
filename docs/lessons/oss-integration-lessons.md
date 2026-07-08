# OSS 组织架构系统集成经验教训

> 本文档记录与 OSS（组织架构系统）对接过程中遇到的问题、权限映射模式和最佳实践。

---

## 1. OSS 菜单码到内部权限的 1:N 映射模式

### 背景

OSS 返回的菜单权限是数字编码（如 `100402`），而本系统内部使用字符串权限键：

- 前端路由权限键：`knowledge-qualification`
- 后端操作权限键：`qualification.manage`、`qualification.view`

一个 OSS 菜单码需要同时映射到**一个前端菜单 key** 和**一个或多个后端 `@PreAuthorize` 权限键**，形成 1:N 映射。

### 历史做法与问题

**早期做法**：`application.yml` 中用逗号分隔字符串

```yaml
# 不推荐：可读性差，容易漏权限键
100402: knowledge-qualification,qualification.manage,qualification.view
```

问题：
- 新增/删除权限键时容易漏改
- 无法直观区分“菜单 key”和“操作权限键”
- 解析代码需要手动 split + trim

**改进做法**：YAML 列表

```yaml
# 推荐：结构清晰，1:N 映射一目了然
directory:
  menu-code-to-permission-key-mappings:
    100402: [knowledge-qualification, qualification.manage, qualification.view]
    100403: [knowledge-personnel, personnel.manage, personnel.view]
    100405: [knowledge-brand-auth, brand-auth.view, brand-auth.create, brand-auth.edit, brand-auth.revoke]
```

### 实现要点

1. **配置类使用 `Map<String, List<String>>`**：
   ```java
   private Map<String, List<String>> menuCodeToPermissionKeyMappings = new HashMap<>();
   ```

2. **Mapper 声明为 Spring Bean**：
   - 避免多处 `new OssMenuPermissionMapper()` 导致配置不同步
   - 便于单测注入同一配置

3. **架构测试强制覆盖**：
   `OssMenuPermissionMappingCoverageTest` 扫描 `ExternalMenuService` 中使用的所有 OSS 菜单码，确保每个 code 在 `application.yml` 中都有定义，防止新增菜单时漏映射。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 逗号分隔字符串难以维护 | 权限映射配置用 YAML 列表 | `application.yml` 中 1:N 映射统一用 `List<String>` |
| Mapper 多处实例化 | 配置依赖类必须声明为 Spring Bean | 禁止在 Service 中直接 `new` 配置映射类 |
| 新增 OSS 菜单码后漏映射 | 用架构测试强制覆盖 | 每个被 `ExternalMenuService` 引用的菜单码必须在映射表中存在 |

### 相关文件

- `backend/src/main/resources/application.yml` — 映射配置
- `backend/src/main/java/com/xiyu/bid/integration/organization/domain/policy/OssMenuPermissionMapper.java` — 映射器
- `backend/src/main/java/com/xiyu/bid/integration/organization/dto/OssMenuTreeNode.java` — 菜单树展平辅助
- `backend/src/test/java/com/xiyu/bid/architecture/OssMenuPermissionMappingCoverageTest.java` — 覆盖测试
- PR !1892 — 1:N 映射落地

---

## 2. OSS 用户权限必须严格隔离于本地 RoleProfileCatalog

### 背景

OSS 用户和本地用户是两套权限体系：

- **本地用户**：`roleCode` → `RoleProfileCatalog` seed → 内部权限（含 `all` 短路键）
- **OSS 用户**：OSS 菜单 codes → 映射 → 内部权限（应严格等于 OSS 返回值）

### 危险交叉点

| 位置 | 风险行为 | 后果 |
|------|---------|------|
| `UserDetailsServiceImpl` | OSS 用户 roleCode=admin 时触发 seed 权限扩散 | OSS admin 看到所有菜单 |
| `DataScopeConfigService` | OSS 缓存菜单未过滤 `all` | 拿到 `all` 权限键 |
| 前端 `hasPermission` | 含 `all` 时直接短路放行 | 后端即使漏过，前端也放行 |

### 三层防御

1. **后端**：OSS 用户只取映射后的权限，不触发本地 seed 扩散；过滤 `all` / `system.admin` / `warehouse.manage`
2. **后端缓存**：`OssPermissionCache` 持久化 OSS 菜单，后端重启后 JWT 不失效
3. **前端**：`hasPermission` 对 OSS 用户禁用 `all` 短路，必须精确匹配

### 经验教训

详见 `docs/lessons/lessons-learned.md` §47。

---

## 3. OSS token 与 CRM token 的缓存隔离

### 背景

OSS 接口需要全局共享的 OSS token，CRM 接口需要按用户隔离的 CRM JWT token。两者都走 `CrmAuthService`，但生命周期和作用域不同。

### 关键决策

- **OSS token**：全局缓存，所有 OSS 组织架构接口共用
- **CRM JWT token**：按用户缓存，`crmSalesNo` 有值时用专属 token，否则回退共享 token
- **用户 profile 缓存**：`username → fullName/crmSalesNo`，避免每次 CRM 接口调用都查 DB

### 经验教训

| 问题 | 教训 |
|------|------|
| 401 时清掉全局 token 影响所有用户 | 用户级 401 只清该用户缓存，`handleUnauthorizedForUser(username)` |
| token TTL 写死 24h | 从 JWT exp claim 解析真实 TTL，`JwtTtlResolver.resolveTtlSeconds(token)` |

### 相关文件

- `backend/src/main/java/com/xiyu/bid/crm/application/CrmAuthService.java`
- `backend/src/main/java/com/xiyu/bid/crm/application/CrmUserTokenCache.java`
- `backend/src/main/java/com/xiyu/bid/crm/application/JwtTtlResolver.java`
