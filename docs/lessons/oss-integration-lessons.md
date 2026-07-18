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

---

## 4. OSS 密码登录返回 501 被误报为"密码错误"

### 背景

OSS 用户走密码登录流程时，前端收到"用户名或密码错误"提示，但用户实际密码正确——后端日志显示 OSS 接口返回 HTTP 501（Not Implemented），但响应体中包含具体业务错误信息（如"system 参数不支持"），被 `CrmHttpClient` 丢弃后只把 501 状态码翻译成"密码错误"。

### 根因

两层问题叠加：

#### 层 1：`CrmHttpClient` 丢弃非 2xx 响应体

```java
// ❌ 错误：非 2xx 直接抛"密码错误"，丢弃响应体
if (!response.getStatusCode().is2xxSuccessful()) {
    throw new BadCredentialsException("用户名或密码错误");
}
```

OSS 密码登录接口在异常情况下返回 501 + JSON 响应体，其中 `message` 字段说明真正失败原因（如 `system` 参数不匹配、用户被禁用、组织未关联等）。`CrmHttpClient` 只看状态码就抛"密码错误"，丢失了真实错误信息。

#### 层 2：OSS 密码登录必须传 `system=bid-platform`

OSS 密码登录接口要求请求体包含 `system` 字段，标识业务方。本项目必须传 `system=bid-platform`：

```java
// ✅ 正确：OSS 密码登录必须带 system 参数
LoginRequest request = LoginRequest.builder()
    .username(username)
    .password(password)
    .system("bid-platform")  // ← 必填，缺失会被 OSS 拒绝并返回 501
    .build();
```

如果漏传 `system` 或传错值（如 `xiyu-bid`、`bid`），OSS 接口返回 501 + `{"message": "system 参数不支持"}`，但 `CrmHttpClient` 把它翻译成"密码错误"，让用户误以为密码错了。

### 修复

1. **`CrmHttpClient` 保留响应体**：非 2xx 时解析响应体中的 `message` 字段，作为异常信息向上抛：

```java
// ✅ 正确：解析响应体，保留真实错误信息
if (!response.getStatusCode().is2xxSuccessful()) {
    String serverMessage = extractMessageFromResponse(response);
    String hint = String.format("OSS 接口返回 %d: %s",
        response.getStatusCode().value(),
        serverMessage != null ? serverMessage : "(无响应体)");
    log.warn("OSS 接口调用失败: {}", hint);
    throw new BadCredentialsException(hint);
}

private String extractMessageFromResponse(ResponseEntity<String> response) {
    try {
        JsonNode root = objectMapper.readTree(response.getBody());
        JsonNode messageNode = root.get("message");
        return messageNode != null ? messageNode.asText() : null;
    } catch (Exception e) {
        return null;
    }
}
```

2. **OSS 密码登录请求强制带 `system=bid-platform`**：在 `CrmAuthService.loginWithPassword` 中显式构造请求体，禁止省略 `system` 字段。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| `CrmHttpClient` 丢弃响应体 | 非 2xx 响应必须解析响应体，保留真实错误信息 | 调用外部接口抛异常时必须携带服务端返回的 `message` |
| OSS 密码登录 501 被翻译为"密码错误" | 状态码与业务语义不能 1:1 映射 | 501 ≠ 密码错误，必须看响应体 `message` |
| 漏传 `system` 参数 | 强制必填参数必须显式构造 | 对接外部系统的必填参数统一在请求构造层显式声明，不依赖调用方传入 |
| 用户被误导修改密码 | 错误信息必须可定位根因 | 前端展示的错误必须包含服务端真实原因，避免"密码错误"等模糊提示 |

### 涉及文件

- `backend/src/main/java/com/xiyu/bid/crm/infrastructure/CrmHttpClient.java` — HTTP 客户端，非 2xx 响应体解析
- `backend/src/main/java/com/xiyu/bid/crm/application/CrmAuthService.java` — 密码登录请求构造，强制带 `system=bid-platform`
- `backend/src/main/java/com/xiyu/bid/crm/dto/LoginRequest.java` — 请求 DTO，`system` 字段必填

### 规范建议

1. **外部接口非 2xx 响应必须解析响应体**：禁止只看状态码就抛通用异常，必须把服务端返回的 `message` 透传给上层。
2. **错误提示必须可定位根因**：前端展示给用户的错误信息应包含服务端真实原因，避免"密码错误"等模糊提示误导用户。
3. **对接外部系统的必填参数必须显式声明**：在请求构造层统一硬编码（如 `system=bid-platform`），不依赖调用方传入，避免漏传。
4. **501 状态码不等于密码错误**：状态码与业务语义的映射必须基于响应体内容，不能基于 HTTP 状态码粗略翻译。
5. **HTTP 客户端日志必须包含响应体**：非 2xx 响应必须打 WARN 日志记录状态码 + 响应体，便于排查。
