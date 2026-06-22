# 泊冉系统权限接口差距分析

> 分析时间：2026-06-22
> 分析范围：泊冉系统获取用户权限接口顺序文档 vs 西域投标平台当前实现
> 详细程度：字段级验证

---

## 一、问题理解与假设

### 1.1 文档要求

泊冉系统定义了 5 个接口的调用顺序：

| 顺序 | 接口名称 | 接口路径 | YApi 地址 | 说明 |
|------|---------|---------|----------|------|
| 1 | 登录鉴权 | `/oauth/login` | https://yapi.ehsy.com/project/406/interface/api/23352 | 获取 token 并缓存 |
| 2 | 获取员工信息 | `/oauth/getUserInfo` | https://yapi.ehsy.com/project/406/interface/api/23358 | 根据 token 获取员工信息 |
| 3 | 获取菜单树 | `/oauth/getUserPermission` | https://yapi.ehsy.com/project/406/interface/api/23484 | 获取用户菜单树 |
| 4 | 获取用户角色 | `/oss/admin-web/v1/output/data/getUserJobListByJobNumberList` | https://yapi.ehsy.com/project/406/interface/api/26325 | 根据工号获取角色 |
| 5 | 登出 | `/oauth/logout` | https://yapi.ehsy.com/project/406/interface/api/23370 | 删除缓存 |

**缓存策略**：菜单权限或用户角色在登录时写入缓存，退出登录删除缓存，重新登录获取最新权限数据。

### 1.2 假设

1. 泊冉系统是西域 OSS 系统的权限模块
2. 这些接口是 OSS 系统对外提供的标准权限接口
3. 西域投标平台需要对接这些接口以获取用户权限信息
4. 泊冉文档描述的是标准调用顺序，实际可能支持多种调用方式

---

## 二、当前实现分析

### 2.1 接口 1：登录鉴权 `/oauth/login`

**状态**：✅ 已实现

**YApi**：https://yapi.ehsy.com/project/406/interface/api/23352

**实现位置**：

| 文件 | 行号 | 方法 | 说明 |
|------|------|------|------|
| `OssDelegationService.java` | 30-52 | `authenticate()` | 组织架构用户登录委托 |
| `CrmAuthService.java` | 85-118 | `applyOssToken()` | CRM 系统获取 OSS token |

**代码引用**：

```java
// OssDelegationService.java:30-52
public boolean authenticate(User user, String rawPassword) {
    String baseUrl = crmProperties.getEffectiveAuthBaseUrl();
    String path = crmProperties.getAuth().getOauthLoginPath();  // /oauth/login
    // ...
}

// CrmAuthService.java:85-118
private CrmToken applyOssToken() {
    String path = properties.getAuth().getOauthLoginPath();  // /oauth/login
    // ...
}
```

**配置**：

```yaml
# application.yml:96
app.crm.auth.oauth-login-path: ${XIYU_CRM_AUTH_OAUTH_LOGIN_PATH:/oauth/login}
```

**缓存机制**：

| 缓存类 | 位置 | 说明 |
|-------|------|------|
| `CrmTokenCache` | `CrmAuthService.java:32` | OSS token 缓存 |

**差距**：无

---

### 2.2 接口 2：根据 token 获取员工信息 `/oauth/getUserInfo`

**状态**：❌ **未按文档实现**（多项关键差异）

**YApi**：https://yapi.ehsy.com/project/406/interface/api/23358

---

#### 泊冉文档要求

| 项目 | 值 |
|------|-----|
| HTTP 方法 | `GET` |
| 接口路径 | `/oauth/getUserInfo` |
| Content-Type | `application/x-www-form-urlencoded`（虽然是 GET） |
| Authorization | `Bearer <token>`（作为 Header） |
| 请求参数 | token 在 Authorization Header 中 |
| 响应字段 | id, userId, username, nickName, phone, email, deptId, deptName, deptPath, jobId, jobName, isManager, createAt, updateAt, status |

---

#### 当前实现

**调用链**：

```
CrmController.getEmployee(@PathVariable String token)
    └─► CrmEmployeeService.getEmployeeByToken(String employeeToken)
            └─► CrmHttpClient.post(baseUrl, path, token, Map.of("token", employeeToken))
                    └─► POST /employee/info (JSON body)
```

**代码引用**：

```java
// CrmController.java:71-78
@GetMapping("/employees/{token}")
public ResponseEntity<ApiResponse<Object>> getEmployee(@PathVariable String token) {
    var response = employeeService.getEmployeeByToken(token);
    // ...
}

// CrmEmployeeService.java:24-30
public CrmResponseHandler.CrmApiResponse getEmployeeByToken(String employeeToken) {
    String token = authService.getValidOssToken();           // 先获取 OSS token
    String baseUrl = properties.getEffectiveAuthBaseUrl();
    String path = properties.getAuth().getEmployeePath();    // = "/employee/info"
    return httpClient.post(baseUrl, path, token,
            Map.of("token", employeeToken));                // POST JSON body
}

// CrmHttpClient.java:86-89 (executePost)
headers.setContentType(MediaType.APPLICATION_JSON);          // ⚠️ JSON
headers.setBearerAuth(accessToken);                          // 先获取的 OSS token
```

**配置**：

```yaml
# application.yml:99
app.crm.auth.employee-path: ${XIYU_CRM_AUTH_EMPLOYEE_PATH:/employee/info}
```

---

#### 差距分析（关键）

| # | 项目 | 泊冉文档要求 | 当前实现 | 影响 | 严重度 |
|---|------|-------------|---------|------|--------|
| 1 | HTTP 方法 | `GET` | `POST` | ⚠️ 方法不一致 | 🔴 高 |
| 2 | 接口路径 | `/oauth/getUserInfo` | `/employee/info` | 🔴 完全不同 | 🔴 高 |
| 3 | Content-Type | `application/x-www-form-urlencoded` | `application/json` | ⚠️ 格式差异 | 🟡 中 |
| 4 | 请求方式 | Bearer token 在 Header | token 在 JSON body | 🔴 完全差异 | 🔴 高 |
| 5 | 响应字段 | 14 个字段（见上） | 未解析（直接返回 JsonNode） | ❌ 未使用 | 🟡 中 |
| 6 | 字段对应 | — | 部分字段有对应 | ❌ 不完整 | 🟡 中 |

**字段对应关系**：

| 泊冉字段 | OrganizationUserSnapshot 字段 | 说明 |
|---------|------------------------------|------|
| id | — | ❌ 无对应 |
| userId | externalUserId | ⚠️ 名称不同 |
| username | username | ✅ 对应 |
| nickName | fullName | ⚠️ 名称不同 |
| phone | phone | ✅ 对应 |
| email | email | ✅ 对应 |
| deptId | departmentCode | ⚠️ 名称不同 |
| deptName | departmentName | ✅ 对应 |
| deptPath | — | ❌ 无对应 |
| jobId | jobId | ✅ 对应 |
| jobName | — | ❌ 无对应（OssUserJobAndRoleDto 有） |
| isManager | — | ❌ 无对应 |
| status | enabled | ⚠️ 名称不同，类型不同 |

---

#### 调用场景分析

| 场景 | 当前实现 | 是否按文档实现 |
|------|---------|--------------|
| 登录后获取员工信息 | ❌ 不在登录流程中 | 否 |
| 根据 token 获取员工信息 | ⚠️ 调用 `/employee/info`（POST）而非 `/oauth/getUserInfo`（GET） | 否 |
| 组织架构同步 | 使用 `/subscription/msg/user`（POST form-urlencoded） | 否 |

**当前 `CrmEmployeeService.getEmployeeByToken()` 的实际用途**：
- 被 `CrmController.getEmployee()` 暴露给前端
- 前端可以根据某个 token 获取员工信息
- **不是登录流程的一部分**

---

#### 最小修改方案

**前提**：OSS 系统提供 `/oauth/getUserInfo` 接口且功能相同

**方案 A：仅修改配置路径（如果两个接口兼容）**

```yaml
# application.yml
app:
  crm:
    auth:
      employee-path: ${XIYU_CRM_AUTH_EMPLOYEE_PATH:/oauth/getUserInfo}
```

**方案 B：改造 `CrmEmployeeService`（如果接口不兼容）**

需要新增 GET 方法到 `CrmHttpClient`：

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
    return httpClient.get(baseUrl, path, employeeToken);    // Bearer token 直接用传入的 token
}
```

---

#### 结论

**接口 2 未按泊冉文档实现**：
- 接口路径不同
- HTTP 方法不同（GET vs POST）
- 请求格式不同（Header vs JSON body）
- 不在登录流程中调用

**是否需要修改**：
- 如果 OSS 系统同时提供 `/employee/info` 和 `/oauth/getUserInfo`，且功能相同 → 仅修改配置路径
- 如果需要严格按泊冉文档实现 → 方案 B

---

#### 需要确认的问题

| # | 问题 | 重要性 |
|---|------|--------|
| Q1 | OSS 系统是否同时提供 `/oauth/getUserInfo` 和 `/employee/info`？ | 🔴 高 |
| Q2 | 两个接口返回的数据格式是否相同？ | 🔴 高 |
| Q3 | 当前 `/employee/info` 接口是否已在前端使用？ | 🟡 中 |
| Q4 | 是否需要将此接口集成到登录流程中？ | 🟡 中 |

---

#### 测试用例建议

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

---

### 2.3 接口 3：获取用户菜单树 `/oauth/getUserPermission`

**状态**：❌ **未按文档实现**（接口路径和响应格式完全不同）

**YApi**：https://yapi.ehsy.com/project/406/interface/api/23484

---

#### 泊冉文档要求

| 项目 | 值 |
|------|-----|
| HTTP 方法 | `GET` |
| 接口路径 | `/oauth/getUserPermission` |
| Authorization | `Bearer <token>`（在 Header 中） |
| Query 参数 | `systemName`（可选） |
| 响应格式 | 扁平对象，键是系统名，值是菜单路径数组 |

**响应示例**：

```json
{
  "code": 0,
  "message": "success",
  "trace": null,
  "data": {
    "OPC": ["sale_log", "sale_order"],
    "SMS": ["sys_org", "sys_user"]
  }
}
```

---

#### 当前实现

**调用链 1：组织架构同步时获取菜单树**

```
OrganizationUserSyncWriter.syncUser()
    └─► OssRoleMenuPermissionAutoSync.mergeUserMenuPermissionsIntoRole(jobNumber, role)
            └─► OrganizationDirectoryHttpGateway.fetchUserMenuTree(jobNumber, context)
                    └─► GET /sysMenuUrl/getUserMenuTree?jobNumber=xxx&systemName=xiyu-bid-poc&menuRetrievalType=2
```

**调用链 2：前端直接调用**

```
CrmController.getMenuTree(@RequestParam String systemType)
    └─► CrmMenuService.getMenuTree(systemType)
            └─► POST /menu/tree (JSON body: {"systemType": "xxx"})
```

**代码引用**：

```java
// OrganizationDirectoryHttpGateway.java:179-196
@Override
public Optional<List<OssMenuTreeNode>> fetchUserMenuTree(
        String jobNumber,
        OrganizationDirectoryLookupContext context
) {
    String url = buildUrl(directory.getUserMenuTreePath());  // /sysMenuUrl/getUserMenuTree
    Map<String, String> params = new LinkedHashMap<>();
    params.put(jobNumberParamName, jobNumber);               // jobNumber 参数
    params.put("systemName", directory.getUserMenuTreeSystemName());  // xiyu-bid-poc
    params.put("menuRetrievalType", String.valueOf(directory.getUserMenuTreeRetrievalType()));  // 2
    return restClient.get(url, params, context).map(mapper::menuTree);
}

// CrmMenuService.java:28-34
public CrmResponseHandler.CrmApiResponse getMenuTree(String systemType) {
    String token = authService.getValidOssToken();
    String baseUrl = properties.getEffectiveAuthBaseUrl();
    String path = properties.getAuth().getMenuTreePath();  // /menu/tree
    return httpClient.post(baseUrl, path, token,
            Map.of("systemType", systemType));             // POST JSON
}
```

**配置**：

```yaml
# application.yml:241
xiyu.integrations.organization.directory.user-menu-tree-path: ${XIYU_ORG_USER_MENU_TREE_PATH:/sysMenuUrl/getUserMenuTree}
xiyu.integrations.organization.directory.user-menu-tree-system-name: xiyu-bid-poc
xiyu.integrations.organization.directory.user-menu-tree-retrieval-type: 2

# CrmProperties.java:186-187
private String menuTreePath = "/menu/tree";
```

---

#### 差距分析（关键）

| # | 项目 | 泊冉文档要求 | 当前实现 | 影响 | 严重度 |
|---|------|-------------|---------|------|--------|
| 1 | HTTP 方法 | `GET` | `GET` (fetchUserMenuTree) / `POST` (getMenuTree) | ⚠️ 不一致 | 🟡 中 |
| 2 | 接口路径 | `/oauth/getUserPermission` | `/sysMenuUrl/getUserMenuTree` 或 `/menu/tree` | 🔴 完全不同 | 🔴 高 |
| 3 | 请求参数 | Authorization Bearer + systemName | jobNumber + systemName + menuRetrievalType | 🔴 完全不同 | 🔴 高 |
| 4 | 响应格式 | 扁平对象 `{系统名: [菜单路径]}` | 树形结构 `[OssMenuTreeNode{...}]` | 🔴 完全不同 | 🔴 高 |
| 5 | 调用场景 | 登录后立即调用 | 组织架构同步时调用 | 🔴 流程差异 | 🔴 高 |

**响应格式差异详解**：

| 维度 | 泊冉文档 | 当前实现 |
|------|---------|---------|
| 结构 | 扁平对象 | 树形节点列表 |
| 数据 | `{系统名: [菜单路径字符串]}` | `[OssMenuTreeNode{id, menuCode, menuName, children, ...}]` |
| 用途 | 快速获取用户在各系统的菜单权限 | 获取完整的菜单树结构 |

**泊冉响应**：

```json
{
  "data": {
    "OPC": ["sale_log", "sale_order"],
    "SMS": ["sys_org", "sys_user"]
  }
}
```

**当前响应**：

```json
{
  "data": [
    {
      "id": 1,
      "menuCode": "dashboard",
      "menuName": "仪表盘",
      "menuType": "M",
      "children": [
        {"id": 2, "menuCode": "dashboard_analysis", "menuName": "分析页", ...}
      ]
    }
  ]
}
```

---

#### 调用场景分析

| 场景 | 当前实现 | 是否按文档实现 |
|------|---------|--------------|
| 登录后获取菜单权限 | ❌ 不在登录流程中 | 否 |
| 组织架构同步 | ✅ 调用 `fetchUserMenuTree` | 接口不同 |
| 前端直接获取菜单 | ⚠️ 调用 `getMenuTree` (POST `/menu/tree`) | 接口不同 |

**当前 `fetchUserMenuTree()` 的实际用途**：
- 在组织架构同步时，通过 `OssRoleMenuPermissionAutoSync.mergeUserMenuPermissionsIntoRole()` 调用
- 将 OSS 菜单权限合并到本地角色的 `menuPermissions` 字段
- **不是登录流程的一部分**

---

#### 最小修改方案

**前提**：OSS 系统提供 `/oauth/getUserPermission` 接口且响应格式相同

**方案 A：仅修改配置路径（如果响应格式兼容）**

⚠️ **不可行**：响应格式完全不同，无法直接替换

**方案 B：新增接口适配（如果需要按泊冉文档实现）**

需要新增：
1. `OssPermissionService` — 调用 `/oauth/getUserPermission`
2. `OssUserPermission` DTO — 解析扁平响应
3. 在登录流程中调用

```java
// 新增 DTO
public record OssUserPermission(
    Map<String, List<String>> systemPermissions  // {系统名: [菜单路径]}
) {}

// 新增服务
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

**方案 C：保持现状（如果当前实现已满足业务需求）**

- 当前通过 `/sysMenuUrl/getUserMenuTree` 获取树形菜单结构
- 在组织架构同步时合并到角色权限
- 如果业务不需要登录时获取菜单权限，可以保持现状

---

#### 结论

**接口 3 未按泊冉文档实现**：
- 接口路径不同
- 请求参数不同（jobNumber vs Bearer token）
- **响应格式完全不同**（扁平对象 vs 树形结构）
- 不在登录流程中调用

**是否需要修改**：
- 如果 OSS 系统同时提供两个接口，且用途不同 → 保持现状
- 如果需要按泊冉文档在登录时获取菜单权限 → 方案 B

---

#### 需要确认的问题

| # | 问题 | 重要性 |
|---|------|--------|
| Q1 | OSS 系统是否同时提供 `/oauth/getUserPermission` 和 `/sysMenuUrl/getUserMenuTree`？ | 🔴 高 |
| Q2 | 两个接口的响应格式是否相同？ | 🔴 高 |
| Q3 | 是否需要在登录时获取菜单权限？ | 🟡 中 |
| Q4 | 当前组织架构同步时的菜单权限获取是否满足业务需求？ | 🟡 中 |

---

#### 测试用例建议

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

---

### 2.4 接口 4：获取用户角色 `/oss/admin-web/v1/output/data/getUserJobListByJobNumberList`

**状态**：✅ 已实现

**YApi**：https://yapi.ehsy.com/project/406/interface/api/26325

**代码引用**：

```java
// OrganizationIntegrationProperties.java:74
private String batchJobRoleLookupPath = "/oss/admin-web/v1/output/data/getUserJobListByJobNumberList";

// OrganizationDirectoryHttpGateway.java:128-133
@Override
public Map<String, OssUserJobAndRoleDto> getUserJobAndRoleListByJobNumbers(
        List<String> jobNumbers,
        OrganizationDirectoryLookupContext context
) {
    return batchClient.getUserJobAndRoleListByJobNumbers(jobNumbers, context);
}
```

**配置**：

```yaml
# application.yml:233
xiyu.integrations.organization.directory.batch-job-role-lookup-path: ${XIYU_ORG_DIRECTORY_BATCH_JOB_ROLE_LOOKUP_PATH:/oss/admin-web/v1/output/data/getUserJobListByJobNumberList}
```

**差距**：无

---

### 2.5 接口 5：登出接口 `/oauth/logout`

**状态**：⚠️ 路径不一致

**YApi**：https://yapi.ehsy.com/project/406/interface/api/23370

**文档要求**：调用 `/oauth/logout`

**当前实现**：
- 使用 `/auth/logout` 接口

**代码引用**：

```java
// CrmProperties.java:184-185
@Data
public static class CrmAuthPaths {
    private String logoutPath = "/auth/logout";  // ❌ 应该是 /oauth/logout
}

// CrmAuthService.java:61-75
public void logout() {
    CrmToken token = ossTokenCache.get().orElse(null);
    if (token != null) {
        String baseUrl = properties.getEffectiveAuthBaseUrl();
        String path = properties.getAuth().getLogoutPath();  // /auth/logout
        httpClient.post(baseUrl, path, token.accessToken(), null);
    }
    ossTokenCache.clear();
    crmTokenCache.clear();
}
```

**配置**：

```yaml
# application.yml:97
app.crm.auth.logout-path: ${XIYU_CRM_AUTH_LOGOUT_PATH:/auth/logout}
```

**差距分析**：

| 项目 | 文档要求 | 当前实现 | 影响 |
|------|---------|---------|------|
| 接口路径 | `/oauth/logout` | `/auth/logout` | 可能无法清除 OSS 缓存 |
| 清除缓存 | OSS token + 菜单权限 | OSS token + CRM token | 缺少菜单权限清除 |

**关键问题**：当前登出时只清除了 `ossTokenCache` 和 `crmTokenCache`，但没有清除菜单权限缓存。

---

## 三、缓存策略分析

### 3.1 Token 缓存

**状态**：✅ 已实现

**实现**：

```java
// CrmAuthService.java:31-34
/** OSS token 缓存（用于 OSS 组织架构接口） */
private final CrmTokenCache ossTokenCache = new CrmTokenCache();
/** CRM JWT token 缓存（用于商机/客户/消息接口） */
private final CrmTokenCache crmTokenCache = new CrmTokenCache();
```

**缓存配置**：

| 配置项 | 默认值 | 说明 |
|-------|-------|------|
| `tokenRenewBeforeExpiryRatio` | 10 | TTL 剩余 10% 时续期 |
| `tokenCoolDownRetries` | 3 | 连续失败 3 次后冷却 |
| `tokenCoolDownMs` | 60000 | 冷却时间 60 秒 |

### 3.2 菜单权限缓存

**状态**：⚠️ 部分实现

**当前实现**：
- 菜单权限存储在数据库 `RoleProfile.menu_permissions` 字段
- 组织架构同步时通过 `OssRoleMenuPermissionAutoSync` 合并到角色

**问题**：
1. 没有用户级别的菜单权限缓存
2. 没有登录时获取菜单权限的流程
3. 登出时没有清除菜单权限缓存

### 3.3 用户角色缓存

**状态**：❌ 未实现

**问题**：
- 没有用户级别的角色缓存
- 每次登录都从数据库获取角色信息

---

## 四、差距汇总

| 接口 | 文档要求路径 | 当前实现路径 | 状态 | 代码位置 | 配置位置 |
|------|-------------|-------------|------|---------|---------|
| 1. 登录鉴权 | `/oauth/login` | `/oauth/login` | ✅ 已实现 | `CrmAuthService:85` | `application.yml:96` |
| 2. 获取员工信息 | `/oauth/getUserInfo` | `/employee/info` | ❌ 路径不一致 | `CrmProperties:189` | `application.yml:99` |
| 3. 获取菜单树 | `/oauth/getUserPermission` | `/sysMenuUrl/getUserMenuTree` | ❌ 路径不一致 | `OrganizationIntegrationProperties:80` | `application.yml:241` |
| 4. 获取用户角色 | `/oss/.../getUserJobListByJobNumberList` | 同左 | ✅ 已实现 | `OrganizationDirectoryHttpGateway:128` | `application.yml:233` |
| 5. 登出 | `/oauth/logout` | `/auth/logout` | ⚠️ 路径不一致 | `CrmProperties:185` | `application.yml:97` |

---

## 五、需要确认的问题清单

### 5.1 OSS 系统接口确认

| # | 问题 | 重要性 | 回答 | 备注 |
|---|------|--------|------|------|
| Q1 | OSS 系统是否同时提供 `/oauth/getUserInfo` 和 `/employee/info`？ | 🔴 高 | | |
| Q2 | 两个接口返回的数据格式是否相同？ | 🔴 高 | | |
| Q3 | OSS 系统是否同时提供 `/oauth/getUserPermission` 和 `/sysMenuUrl/getUserMenuTree`？ | 🔴 高 | | |
| Q4 | 两个菜单接口返回的数据格式是否相同？ | 🔴 高 | | |
| Q5 | OSS 系统是否同时提供 `/oauth/logout` 和 `/auth/logout`？ | 🟡 中 | | |
| Q6 | 登录流程是否必须按顺序调用接口？ | 🟡 中 | | |
| Q7 | 是否有接口调用频率限制？ | 🟡 中 | | |

### 5.2 缓存策略确认

| # | 问题 | 重要性 | 回答 | 备注 |
|---|------|--------|------|------|
| Q8 | OSS 系统是否要求在登录时缓存权限数据？ | 🔴 高 | | |
| Q9 | 缓存的有效期是多久？ | 🟡 中 | | |
| Q10 | 登出时是否必须调用 `/oauth/logout` 清除缓存？ | 🟡 中 | | |
| Q11 | 是否需要实现用户级别的菜单权限缓存？ | 🟡 中 | | |

---

## 六、详细实施计划

### 阶段 1：配置路径调整（最小改动）

**目标**：将接口路径改为可配置，支持切换到泊冉文档要求的路径

**修改文件清单**：

| 文件 | 修改类型 | 说明 |
|------|---------|------|
| `backend/src/main/resources/application.yml` | 修改 | 新增配置项 |
| `backend/src/main/resources/application-dev.yml` | 修改 | 开发环境配置 |
| `backend/src/main/resources/application-test.yml` | 新增 | 测试环境配置 |
| `backend/src/main/java/com/xiyu/bid/crm/config/CrmProperties.java` | 修改 | 默认值 |
| `backend/src/main/java/com/xiyu/bid/integration/organization/application/OrganizationIntegrationProperties.java` | 修改 | 默认值 |

**配置变更**：

```yaml
# application.yml
app:
  crm:
    auth:
      # 改为泊冉文档要求
      employee-path: ${XIYU_CRM_AUTH_EMPLOYEE_PATH:/oauth/getUserInfo}
      logout-path: ${XIYU_CRM_AUTH_LOGOUT_PATH:/oauth/logout}

xiyu:
  integrations:
    organization:
      directory:
        # 改为泊冉文档要求
        user-menu-tree-path: ${XIYU_ORG_USER_MENU_TREE_PATH:/oauth/getUserPermission}
```

**测试用例**：

```java
// CrmAuthServiceTest.java
@Test
void shouldCallConfiguredLogoutPath() {
    // Given: 配置 logout-path = /oauth/logout
    // When: 用户登出
    crmAuthService.logout();
    // Then: 请求发送到 /oauth/logout
    verify(crmHttpClient).post(eq("/oauth/logout"), any(), any(), any());
}
```

---

### 阶段 2：登录流程增强（可选）

**目标**：在登录流程中按顺序调用 OSS 接口

**前提**：OSS 系统要求在登录时按顺序调用接口

**修改文件清单**：

| 文件 | 修改类型 | 说明 |
|------|---------|------|
| `backend/src/main/java/com/xiyu/bid/crm/application/OssLoginFlowService.java` | 新增 | OSS 登录流程服务 |
| `backend/src/main/java/com/xiyu/bid/crm/dto/OssUserInfo.java` | 新增 | 用户信息 DTO |
| `backend/src/main/java/com/xiyu/bid/crm/dto/OssPermission.java` | 新增 | 权限信息 DTO |
| `backend/src/main/java/com/xiyu/bid/service/AuthService.java` | 修改 | 调用新服务 |
| `backend/src/test/java/com/xiyu/bid/crm/application/OssLoginFlowServiceTest.java` | 新增 | 测试用例 |

---

### 阶段 3：缓存策略完善（可选）

**目标**：实现用户级别的菜单权限缓存

**修改文件清单**：

| 文件 | 修改类型 | 说明 |
|------|---------|------|
| `backend/src/main/java/com/xiyu/bid/crm/cache/OssUserInfoCache.java` | 新增 | 用户信息缓存 |
| `backend/src/main/java/com/xiyu/bid/crm/cache/OssPermissionCache.java` | 新增 | 权限缓存 |
| `backend/src/main/java/com/xiyu/bid/crm/application/CrmAuthService.java` | 修改 | 登出时清除缓存 |
| `backend/src/test/java/com/xiyu/bid/crm/cache/OssPermissionCacheTest.java` | 新增 | 测试用例 |

---

## 七、风险评估

### 7.1 高风险

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| OSS 接口路径不存在 | 登录功能失效 | 先在测试环境验证 |
| 接口响应格式不同 | 数据解析失败 | 编写适配器处理 |
| 登录流程变慢 | 用户体验下降 | 使用并行调用 |

### 7.2 中风险

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| 缓存策略不符合预期 | 权限不同步 | 配置化缓存策略 |
| 测试覆盖不足 | 回归风险 | 补充测试用例 |

---

## 八、结论

### 8.1 差距总结

| 类别 | 已实现 | 未实现 | 部分实现 |
|------|-------|-------|---------|
| 接口路径 | 2/5 | 2/5 | 1/5 |
| 缓存策略 | 1/3 | 1/3 | 1/3 |

### 8.2 推荐方案

**方案 A：配置优先（推荐先做）**
- 修改配置文件默认值
- 支持环境变量覆盖
- 先验证接口可用性

**方案 B：完整实现（按需）**
- 实现完整的登录流程
- 实现用户级权限缓存
- 需要更多测试验证

### 8.3 下一步行动

1. **立即行动**：
   - [ ] 与 OSS 系统负责人确认接口可用性
   - [ ] 获取接口文档和测试环境

2. **短期行动**：
   - [ ] 修改配置默认值
   - [ ] 在测试环境验证
   - [ ] 补充测试用例

3. **长期行动**：
   - [ ] 根据业务需求决定是否实现完整登录流程
   - [ ] 实现用户级权限缓存（如需要）
   - [ ] 添加监控指标

---

## 九、附录

### A. 相关文档

- [泊冉系统获取用户权限接口顺序（原始文档）](boran-permission-api-sequence.md)
- [泊冉系统权限接口实施计划](boran-permission-api-implementation-plan.md)

### B. 相关代码位置

| 模块 | 路径 |
|------|------|
| CRM 配置 | `com.xiyu.bid.crm.config.CrmProperties` |
| CRM 认证 | `com.xiyu.bid.crm.application.CrmAuthService` |
| OSS 委托 | `com.xiyu.bid.crm.application.OssDelegationService` |
| 组织架构集成 | `com.xiyu.bid.integration.organization` |
| 菜单权限同步 | `com.xiyu.bid.integration.organization.application.OssRoleMenuPermissionAutoSync` |

### C. 配置项清单

| 配置项 | 当前值 | 建议值 | 环境变量 |
|-------|-------|-------|---------|
| `app.crm.auth.employee-path` | `/employee/info` | `/oauth/getUserInfo` | `XIYU_CRM_AUTH_EMPLOYEE_PATH` |
| `app.crm.auth.logout-path` | `/auth/logout` | `/oauth/logout` | `XIYU_CRM_AUTH_LOGOUT_PATH` |
| `xiyu.integrations.organization.directory.user-menu-tree-path` | `/sysMenuUrl/getUserMenuTree` | `/oauth/getUserPermission` | `XIYU_ORG_USER_MENU_TREE_PATH` |
