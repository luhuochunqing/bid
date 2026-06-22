# 泊冉系统权限接口开发计划

> 版本：v1.0
> 日期：2026-06-22
> 状态：待确认

---

## 一、背景与目标

### 1.1 背景

泊冉系统定义了 5 个权限接口的标准调用顺序，用于实现统一的用户认证和权限管理。当前西城投标平台已实现部分接口，但存在路径不一致、调用场景缺失等问题。

### 1.2 目标

1. **接口路径对齐**：确保所有接口路径符合泊冉文档要求
2. **调用流程对齐**：在登录/登出流程中按顺序调用 OSS 接口
3. **缓存策略对齐**：实现泊冉文档要求的缓存策略
4. **向后兼容**：确保现有功能不受影响

---

## 二、差距汇总

| 接口 | 名称 | 当前状态 | 关键差距 | 优先级 |
|------|------|---------|---------|--------|
| 1 | 登录鉴权 | ✅ 已实现 | 无 | - |
| 2 | 获取员工信息 | ❌ 未实现 | 路径/方法/请求格式完全不同 | P0 |
| 3 | 获取菜单树 | ❌ 未实现 | 路径/响应格式完全不同 | P1 |
| 4 | 获取用户角色 | ✅ 已实现 | 仅调用场景不同 | P2 |
| 5 | 登出 | ⚠️ 部分实现 | 路径不一致 + 本地登出不调用 | P0 |

---

## 三、前置条件

### 3.1 需要确认的问题

在开始开发前，必须与 OSS 系统负责人确认以下问题：

| # | 问题 | 重要性 | 确认人 | 确认时间 |
|---|------|--------|--------|---------|
| Q1 | OSS 系统是否提供 `/oauth/getUserInfo` 接口？ | 🔴 高 | | |
| Q2 | OSS 系统是否提供 `/oauth/getUserPermission` 接口？ | 🔴 高 | | |
| Q3 | OSS 系统是否提供 `/oauth/logout` 接口？ | 🔴 高 | | |
| Q4 | 这些接口的响应格式是否与泊冉文档一致？ | 🔴 高 | | |
| Q5 | 是否需要在登录时按顺序调用所有接口？ | 🟡 中 | | |
| Q6 | 是否需要实现用户级别的权限缓存？ | 🟡 中 | | |

### 3.2 测试环境准备

- [ ] 获取 OSS 测试环境地址
- [ ] 获取测试账号和密码
- [ ] 获取接口文档和 Mock 数据
- [ ] 配置本地开发环境

---

## 四、开发计划

### 阶段 0：确认与准备（1 天）

**目标**：确认 OSS 接口可用性，准备测试环境

**任务清单**：

| # | 任务 | 负责人 | 预计时间 | 状态 |
|---|------|--------|---------|------|
| 0.1 | 与 OSS 负责人确认接口可用性 | | 2h | 待开始 |
| 0.2 | 获取 OSS 测试环境地址和账号 | | 1h | 待开始 |
| 0.3 | 验证接口响应格式 | | 2h | 待开始 |
| 0.4 | 更新开发计划（根据确认结果） | | 1h | 待开始 |

**产出物**：
- OSS 接口确认文档
- 测试环境配置清单

---

### 阶段 1：接口路径配置化（1-2 天）

**目标**：将接口路径改为可配置，支持切换到泊冉文档要求的路径

**前提**：OSS 系统提供泊冉文档中的接口

**修改文件清单**：

| 文件 | 修改类型 | 说明 |
|------|---------|------|
| `application.yml` | 修改 | 修改默认配置路径 |
| `application-dev.yml` | 修改 | 开发环境配置 |
| `application-test.yml` | 修改 | 测试环境配置 |
| `CrmProperties.java` | 修改 | 默认值改为泊冉文档路径 |
| `OrganizationIntegrationProperties.java` | 修改 | 默认值改为泊冉文档路径 |

**配置变更**：

```yaml
# application.yml
app:
  crm:
    auth:
      employee-path: ${XIYU_CRM_AUTH_EMPLOYEE_PATH:/oauth/getUserInfo}
      logout-path: ${XIYU_CRM_AUTH_LOGOUT_PATH:/oauth/logout}

xiyu:
  integrations:
    organization:
      directory:
        user-menu-tree-path: ${XIYU_ORG_USER_MENU_TREE_PATH:/oauth/getUserPermission}
```

**测试用例**：

```java
@Test
void shouldUseConfiguredEmployeePath() {
    // Given: 配置 employee-path = /oauth/getUserInfo
    // When: 调用获取员工信息接口
    // Then: 请求发送到 /oauth/getUserInfo
}

@Test
void shouldUseConfiguredLogoutPath() {
    // Given: 配置 logout-path = /oauth/logout
    // When: 用户登出
    // Then: 请求发送到 /oauth/logout
}
```

**验证步骤**：
1. 修改配置后启动应用
2. 调用登录接口，检查日志确认调用了正确的接口路径
3. 调用登出接口，检查日志确认调用了正确的接口路径

---

### 阶段 2：接口 2 改造（2-3 天）

**目标**：改造获取员工信息接口，符合泊冉文档要求

**前提**：OSS 系统提供 `/oauth/getUserInfo` 接口

**修改文件清单**：

| 文件 | 修改类型 | 说明 |
|------|---------|------|
| `CrmHttpClient.java` | 修改 | 新增 GET 方法 |
| `CrmEmployeeService.java` | 修改 | 改用 GET 方法调用 |
| `CrmController.java` | 修改 | 调整接口参数 |
| `CrmEmployeeServiceTest.java` | 修改 | 更新测试用例 |

**代码变更**：

```java
// CrmHttpClient.java 新增
public CrmResponseHandler.CrmApiResponse get(String baseUrl, String path, String accessToken) {
    String url = baseUrl + path;
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);
    TraceHeaderInjector.inject(headers);
    HttpEntity<Void> request = new HttpEntity<>(headers);
    try {
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);
        log.info("CRM GET {} -> {}", url, response.getStatusCode());
        return CrmResponseHandler.parse(response.getBody());
    } catch (RuntimeException e) {
        log.error("CRM GET failed: {}", e.getMessage());
        return CrmResponseHandler.CrmApiResponse.parseError(e.getMessage());
    }
}

// CrmEmployeeService.java 修改
public CrmResponseHandler.CrmApiResponse getEmployeeByToken(String employeeToken) {
    String baseUrl = properties.getEffectiveAuthBaseUrl();
    String path = properties.getAuth().getEmployeePath();  // /oauth/getUserInfo
    return httpClient.get(baseUrl, path, employeeToken);    // GET 请求，Bearer token 在 Header
}
```

**测试用例**：

```java
@Test
void shouldCallGetEndpointWithBearerToken() {
    // Given
    when(httpClient.get(any(), eq("/oauth/getUserInfo"), eq("user-token")))
        .thenReturn(mockResponse);

    // When
    CrmResponseHandler.CrmApiResponse response = employeeService.getEmployeeByToken("user-token");

    // Then
    verify(httpClient).get(baseUrl, "/oauth/getUserInfo", "user-token");
}
```

**验证步骤**：
1. 调用 `/api/crm/employees/{token}` 接口
2. 检查日志确认发送了 GET 请求到 `/oauth/getUserInfo`
3. 验证返回的员工信息正确

---

### 阶段 3：接口 3 改造（3-5 天）

**目标**：改造获取菜单树接口，符合泊冉文档要求

**前提**：OSS 系统提供 `/oauth/getUserPermission` 接口

**决策点**：
- 如果 OSS 系统同时提供 `/oauth/getUserPermission` 和 `/sysMenuUrl/getUserMenuTree`，且响应格式不同 → 需要新增接口适配
- 如果 OSS 系统只提供 `/oauth/getUserPermission` → 需要改造现有实现

**方案 A：仅修改配置路径（如果响应格式兼容）**

```yaml
xiyu.integrations.organization.directory.user-menu-tree-path: /oauth/getUserPermission
```

**方案 B：新增接口适配（如果响应格式不同）**

**修改文件清单**：

| 文件 | 修改类型 | 说明 |
|------|---------|------|
| `OssPermissionService.java` | 新增 | OSS 权限服务 |
| `OssUserPermission.java` | 新增 | 权限 DTO |
| `AuthService.java` | 修改 | 登录时调用新服务 |
| `OssPermissionServiceTest.java` | 新增 | 测试用例 |

**代码变更**：

```java
// OssUserPermission.java 新增
public record OssUserPermission(
    Map<String, List<String>> systemPermissions  // {系统名: [菜单路径]}
) {}

// OssPermissionService.java 新增
@Service
public class OssPermissionService {
    private final CrmHttpClient httpClient;
    private final CrmAuthService authService;
    private final CrmProperties properties;

    public OssUserPermission getUserPermission(String token, String systemName) {
        String baseUrl = properties.getEffectiveAuthBaseUrl();
        String path = "/oauth/getUserPermission";
        // GET 请求，Bearer token 在 Header
        // Query 参数: systemName
        // 解析响应为 Map<String, List<String>>
    }
}
```

**测试用例**：

```java
@Test
void shouldCallGetUserPermissionWithBearerToken() {
    // Given
    String token = "user-access-token";
    String systemName = "xiyu-bid-poc";

    // When
    OssUserPermission permission = ossPermissionService.getUserPermission(token, systemName);

    // Then
    verify(httpClient).get(baseUrl, "/oauth/getUserPermission?systemName=" + systemName, token);
    assertThat(permission.systemPermissions()).containsKey("xiyu-bid-poc");
}
```

**验证步骤**：
1. 调用登录接口
2. 检查日志确认调用了 `/oauth/getUserPermission`
3. 验证返回的菜单权限正确

---

### 阶段 4：接口 5 改造（1-2 天）

**目标**：改造登出接口，符合泊冉文档要求

**修改文件清单**：

| 文件 | 修改类型 | 说明 |
|------|---------|------|
| `AuthService.java` | 修改 | 登出时调用 OSS 登出接口 |
| `CrmAuthService.java` | 修改 | 修改 Content-Type |
| `AuthServiceTest.java` | 修改 | 更新测试用例 |

**代码变更**：

```java
// AuthService.java 修改
@Transactional
public void logout(String accessToken, String refreshToken) {
    // 1. 调用 OSS 登出接口
    try {
        crmAuthService.logout();
    } catch (RuntimeException e) {
        log.warn("OSS logout failed (non-fatal): {}", e.getMessage());
    }

    // 2. 本地 JWT token 失效处理
    revokeAccessToken(accessToken);
    if (refreshToken != null && !refreshToken.isBlank()) {
        refreshSessionRepository.findByTokenHash(hashToken(refreshToken))
                .filter(session -> session.getRevokedAt() == null)
                .ifPresent(session -> {
                    session.setRevokedAt(LocalDateTime.now());
                    refreshSessionRepository.save(session);
                });
    }
}

// CrmAuthService.java 修改
public void logout() {
    CrmToken token = ossTokenCache.get().orElse(null);
    if (token != null) {
        try {
            String baseUrl = properties.getEffectiveAuthBaseUrl();
            String path = properties.getAuth().getLogoutPath();  // /oauth/logout
            // 使用 form-urlencoded 而不是 JSON
            httpClient.postForm(baseUrl, path, token.accessToken(), null);
        } catch (RuntimeException e) {
            log.warn("CRM logout request failed (non-fatal): {}", e.getMessage());
        }
    }
    ossTokenCache.clear();
    crmTokenCache.clear();
}
```

**测试用例**：

```java
@Test
void shouldCallOssLogoutOnLocalLogout() {
    // Given
    String accessToken = "valid-jwt-token";
    String refreshToken = "valid-refresh-token";

    // When
    authService.logout(accessToken, refreshToken);

    // Then
    verify(crmAuthService).logout();  // 验证调用了 OSS 登出
}
```

**验证步骤**：
1. 调用 `/api/auth/logout` 接口
2. 检查日志确认调用了 OSS 登出接口
3. 验证 OSS token 缓存已清除

---

### 阶段 5：登录流程集成（2-3 天）

**目标**：在登录流程中按顺序调用 OSS 接口

**前提**：泊冉文档要求登录时必须按顺序调用接口

**修改文件清单**：

| 文件 | 修改类型 | 说明 |
|------|---------|------|
| `OssLoginFlowService.java` | 新增 | OSS 登录流程服务 |
| `AuthService.java` | 修改 | 调用新服务 |
| `OssLoginFlowServiceTest.java` | 新增 | 测试用例 |

**代码变更**：

```java
// OssLoginFlowService.java 新增
@Service
public class OssLoginFlowService {

    private final CrmAuthService crmAuthService;
    private final CrmEmployeeService employeeService;
    private final OssPermissionService permissionService;
    private final OrganizationDirectoryGateway directoryGateway;

    /**
     * 执行 OSS 登录流程。
     *
     * 顺序：
     * 1. /oauth/login - 获取 token
     * 2. /oauth/getUserInfo - 获取员工信息
     * 3. /oauth/getUserPermission - 获取菜单树
     * 4. /oss/.../getUserJobListByJobNumberList - 获取用户角色
     */
    public OssLoginResult login(String username, String password) {
        // 1. 登录获取 token（已在 CrmAuthService 中实现）
        String token = crmAuthService.getValidOssToken();

        // 2. 获取员工信息
        CrmResponseHandler.CrmApiResponse userInfo = employeeService.getEmployeeByToken(token);

        // 3. 获取菜单权限
        OssUserPermission menuPermissions = permissionService.getUserPermission(token, "xiyu-bid-poc");

        // 4. 获取用户角色（已在组织架构同步中实现）

        return new OssLoginResult(token, userInfo, menuPermissions);
    }
}
```

**测试用例**：

```java
@Test
void shouldCallOssInterfacesInOrder() {
    // Given
    String username = "test-user";
    String password = "test-password";

    // When
    OssLoginResult result = ossLoginFlowService.login(username, password);

    // Then - 验证按顺序调用
    InOrder inOrder = inOrder(crmAuthService, employeeService, permissionService);
    inOrder.verify(crmAuthService).getValidOssToken();
    inOrder.verify(employeeService).getEmployeeByToken(any());
    inOrder.verify(permissionService).getUserPermission(any(), eq("xiyu-bid-poc"));
}
```

**验证步骤**：
1. 调用登录接口
2. 检查日志确认按顺序调用了所有 OSS 接口
3. 验证返回的用户信息和权限正确

---

### 阶段 6：缓存策略实现（2-3 天）

**目标**：实现泊冉文档要求的缓存策略

**前提**：需要在登录时缓存权限数据

**修改文件清单**：

| 文件 | 修改类型 | 说明 |
|------|---------|------|
| `OssUserInfoCache.java` | 新增 | 用户信息缓存 |
| `OssPermissionCache.java` | 新增 | 权限缓存 |
| `CrmAuthService.java` | 修改 | 登出时清除缓存 |
| `OssUserInfoCacheTest.java` | 新增 | 测试用例 |
| `OssPermissionCacheTest.java` | 新增 | 测试用例 |

**代码变更**：

```java
// OssUserInfoCache.java 新增
@Component
public class OssUserInfoCache {

    private final Cache<String, OssUserInfo> cache;

    public OssUserInfoCache() {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(30))
                .maximumSize(10_000)
                .build();
    }

    public void put(String token, OssUserInfo userInfo) {
        cache.put(token, userInfo);
    }

    public Optional<OssUserInfo> get(String token) {
        return Optional.ofNullable(cache.getIfPresent(token));
    }

    public void invalidate(String token) {
        cache.invalidate(token);
    }

    public void clear() {
        cache.invalidateAll();
    }
}

// CrmAuthService.java 修改
public void logout() {
    CrmToken token = ossTokenCache.get().orElse(null);
    if (token != null) {
        try {
            String baseUrl = properties.getEffectiveAuthBaseUrl();
            String path = properties.getAuth().getLogoutPath();
            httpClient.postForm(baseUrl, path, token.accessToken(), null);
        } catch (RuntimeException e) {
            log.warn("CRM logout request failed (non-fatal): {}", e.getMessage());
        }
    }
    ossTokenCache.clear();
    crmTokenCache.clear();
    userInfoCache.clear();      // 清除用户信息缓存
    permissionCache.clear();    // 清除权限缓存
    log.info("All OSS caches cleared (logout)");
}
```

**测试用例**：

```java
@Test
void shouldClearAllCachesOnLogout() {
    // Given
    ossTokenCache.put(new CrmToken("token", 3600, Instant.now()));
    userInfoCache.put("token", userInfo);
    permissionCache.put("token", permission);

    // When
    crmAuthService.logout();

    // Then
    assertThat(ossTokenCache.get()).isEmpty();
    assertThat(userInfoCache.get("token")).isEmpty();
    assertThat(permissionCache.get("token")).isEmpty();
}
```

**验证步骤**：
1. 登录后检查缓存是否写入
2. 登出后检查缓存是否清除
3. 验证缓存过期时间是否正确

---

## 五、时间估算

| 阶段 | 任务 | 预计时间 | 依赖 |
|------|------|---------|------|
| 阶段 0 | 确认与准备 | 1 天 | OSS 负责人配合 |
| 阶段 1 | 接口路径配置化 | 1-2 天 | 阶段 0 |
| 阶段 2 | 接口 2 改造 | 2-3 天 | 阶段 1 |
| 阶段 3 | 接口 3 改造 | 3-5 天 | 阶段 1 |
| 阶段 4 | 接口 5 改造 | 1-2 天 | 阶段 1 |
| 阶段 5 | 登录流程集成 | 2-3 天 | 阶段 2-4 |
| 阶段 6 | 缓存策略实现 | 2-3 天 | 阶段 5 |
| **总计** | | **12-19 天** | |

---

## 六、风险评估

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|------|---------|
| OSS 接口不存在 | 高 | 中 | 先确认接口可用性，准备备选方案 |
| 接口响应格式不同 | 中 | 中 | 编写适配器处理不同格式 |
| 登录流程变慢 | 中 | 低 | 使用并行调用减少延迟 |
| 缓存失效 | 中 | 低 | 设置合理的缓存过期时间 |
| 现有功能受影响 | 高 | 低 | 充分测试，分阶段上线 |

---

## 七、上线计划

### 7.1 环境配置

| 环境 | employee-path | logout-path | user-menu-tree-path |
|------|--------------|-------------|---------------------|
| 开发 | `/oauth/getUserInfo` | `/oauth/logout` | `/oauth/getUserPermission` |
| 测试 | `/oauth/getUserInfo` | `/oauth/logout` | `/oauth/getUserPermission` |
| 生产 | `/oauth/getUserInfo` | `/oauth/logout` | `/oauth/getUserPermission` |

### 7.2 上线步骤

1. **预部署**
   - 在测试环境配置新路径
   - 运行所有测试
   - 手动验证登录流程

2. **灰度发布**
   - 先发布到测试环境
   - 观察 1-2 天
   - 确认无问题后发布到生产

3. **监控**
   - 监控 OSS 接口调用成功率
   - 监控登录成功率
   - 监控缓存命中率

---

## 八、验收标准

### 8.1 功能验收

- [ ] 登录接口调用 `/oauth/login` 成功
- [ ] 获取员工信息接口调用 `/oauth/getUserInfo` 成功
- [ ] 获取菜单树接口调用 `/oauth/getUserPermission` 成功
- [ ] 获取用户角色接口调用成功
- [ ] 登出接口调用 `/oauth/logout` 成功
- [ ] 登录流程按顺序调用所有接口
- [ ] 缓存策略正确实现

### 8.2 测试验收

- [ ] 单元测试覆盖率 ≥ 80%
- [ ] 集成测试全部通过
- [ ] E2E 测试全部通过
- [ ] 性能测试通过（登录响应时间 < 3s）

### 8.3 文档验收

- [ ] API 文档更新
- [ ] 配置文档更新
- [ ] 运维文档更新

---

## 九、附录

### A. 相关文档

- [泊冉系统获取用户权限接口顺序](boran-permission-api-sequence.md)
- [泊冉系统权限接口差距分析](boran-permission-api-gap-analysis.md)

### B. 配置模板

```yaml
# application-prod.yml
app:
  crm:
    auth:
      employee-path: /oauth/getUserInfo
      logout-path: /oauth/logout

xiyu:
  integrations:
    organization:
      directory:
        user-menu-tree-path: /oauth/getUserPermission
```

### C. 环境变量模板

```bash
# .env.prod
XIYU_CRM_AUTH_EMPLOYEE_PATH=/oauth/getUserInfo
XIYU_CRM_AUTH_LOGOUT_PATH=/oauth/logout
XIYU_ORG_USER_MENU_TREE_PATH=/oauth/getUserPermission
```
