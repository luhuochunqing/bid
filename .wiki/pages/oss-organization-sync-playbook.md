---
title: OSS 组织架构同步实战手册
space: engineering
category: guide
tags: [OSS, 组织架构, 人员同步, skipUnmappedUsers, LoginRoleWhitelist, OssPermissionCache, Kafka]
sources:
  - backend/src/main/java/com/xiyu/bid/integration/organization/application/OrganizationUserSyncWriter.java
  - backend/src/main/java/com/xiyu/bid/integration/organization/application/OrganizationIntegrationProperties.java
  - backend/src/main/java/com/xiyu/bid/security/domain/LoginRoleWhitelist.java
  - backend/src/main/java/com/xiyu/bid/integration/organization/domain/OrganizationSyncPolicy.java
  - backend/src/main/java/com/xiyu/bid/auth/UserDetailsServiceImpl.java
  - backend/src/main/resources/application.yml
backlinks:
  - _index
  - deployment
  - integration-organization-event-sdk
  - roles-and-permissions
  - production-deployment-lessons
created: 2026-07-09
updated: 2026-07-09
health_checked: 2026-07-09
---
# OSS 组织架构同步实战手册

> 基于 2026-07-09 首次生产部署的实战经验，记录 OSS 组织架构人员同步的完整机制、陷阱和排障方法。

---

## 1. 同步机制总览

### 1.1 两种同步模式

| 模式 | 触发方式 | 用途 | 数据量 |
|------|---------|------|--------|
| **全量同步** | `POST /api/integrations/organization/sync-runs`（传时间窗口） | 首次部署 / 数据修复 | 1000~10000+ |
| **增量同步** | Kafka 事件 `BaseOssUser/BaseOssDept/BaseOssJob` | 日常实时同步 | 单条 |

### 1.2 全量同步流程

```
POST /api/integrations/organization/sync-runs
  ↓
OrganizationDirectoryGateway 调 OSS 接口 getUserByTimeWindow
  ↓
返回 JSON → OrganizationDirectoryJsonMapper 解析
  ↓
OrganizationSyncPolicy.planUserSync 计算同步计划
  ↓
JobRoleLookupResolver.resolve 解析角色（人员→部门→岗位 优先级）
  ↓
OrganizationUserSyncWriter.upsert 写入 users 表
  ↓
（可选）OssRoleMenuPermissionAutoSync 同步菜单权限
```

### 1.3 关键配置

```yaml
xiyu:
  integrations:
    organization:
      enabled: ${XIYU_ORG_SYNC_ENABLED:false}                    # 总开关
      skip-unmapped-users: ${XIYU_ORG_SYNC_SKIP_UNMAPPED_USERS:true}  # 白名单模式
      directory:
        base-url: ${XIYU_ORG_DIRECTORY_BASE_URL:https://base-oss-test.ehsy.com}
        user-window-path: /subscription/msg/getUserByTimeWindow
```

---

## 2. skipUnmappedUsers 配置陷阱（CO-441 修复）

### 2.1 配置含义

| 值 | 行为 | 适用场景 |
|----|------|---------|
| `true`（默认） | 白名单模式：未匹配角色的用户**不创建**，已存在则刷新在职状态 | 测试环境 / 精确角色控制 |
| `false` | 全量模式：**创建无角色用户**，登录时由 OssPermissionCache 决定角色 | 生产环境首次部署 |

### 2.2 陷阱：配置声明 ≠ 代码使用

**事故**：`OrganizationIntegrationProperties.skipUnmappedUsers` 字段声明了，但 `OrganizationUserSyncWriter.upsert()` 中硬编码了 `LoginRoleWhitelist.isAllowed()` 检查，**没有使用 `properties.isSkipUnmappedUsers()`**。

**修复**（[OrganizationUserSyncWriter.java:68](file:///Users/user/xiyu/worktrees/trae/backend/src/main/java/com/xiyu/bid/integration/organization/application/OrganizationUserSyncWriter.java#L68)）：

```java
if (!LoginRoleWhitelist.isAllowed(resolvedRoleCode) && properties.isSkipUnmappedUsers()) {
    // 白名单模式：跳过；全量模式：继续创建无角色用户
    handleUserWithoutResolvedRole(sourceApp, eventKey, snapshot, existingUser);
    return Optional.empty();
}
```

### 2.3 生产环境配置

```bash
# /etc/xiyu-bid/backend.env
XIYU_ORG_SYNC_ENABLED=true
XIYU_ORG_SYNC_SKIP_UNMAPPED_USERS=false
```

---

## 3. 角色解析链（3 级优先级）

### 3.1 解析顺序

```
JobRoleLookupResolver.resolve(snapshot, jobRoleLookupMap)
  ↓
1. 人员精确匹配（person-identifier）→ 最高优先级
  ↓ 未命中
2. 部门正则匹配（department-pattern）
  ↓ 未命中
3. 岗位正则匹配（position-pattern）
  ↓ 未命中
4. 返回 null → 由 skipUnmappedUsers 决定行为
```

### 3.2 角色映射配置

```yaml
# 人员映射（15 个，工号/邮箱精确匹配）
person-to-role-mappings:
  - person-identifier: "03595"      # 张頔
    role-code: /bidAdmin
  - person-identifier: "11484"      # 袁思琪
    role-code: bid-TeamLeader

# 部门映射（2 个，正则匹配）
department-to-role-mappings:
  - department-pattern: ".*投标管理部.*"
    role-code: bid-Team
  - department-pattern: ".*行政部.*"
    role-code: bid-administration

# 岗位映射（4 个，正则匹配）
position-to-role-mappings:
  - position-pattern: ".*项目经理.*"
    role-code: bid-projectLeader
```

### 3.3 管理员提升规则

```java
boolean allowAdminElevation = resolvedRole.source() == RoleMappingSource.PERSON;
// 只有"人员精确匹配"命中 admin 角色码时，才允许提升为 admin
// 部门/岗位命中 admin 时不允许提升（防止误提）
```

---

## 4. LoginRoleWhitelist vs OssPermissionCache

### 4.1 两层角色控制

| 层 | 时机 | 逻辑 | 数据源 |
|----|------|------|--------|
| **同步时** | `OrganizationUserSyncWriter.upsert()` | `LoginRoleWhitelist.isAllowed(roleCode)` 决定是否创建用户 | DB `role_profile` 表 |
| **登录时** | `UserDetailsServiceImpl.loadUserByUsername()` | OSS 用户 cache miss 时 fail-closed，禁止 DB fallback | OSS 实时接口 |
| **登录时** | `OssPermissionCache` | 实时从 OSS 抓取角色+权限，不依赖本地 DB roleProfile | OSS `/menu/tree` 接口 |

### 4.2 关键约束

```java
// UserDetailsServiceImpl.java:64-67
if (isOssUser && !LoginRoleWhitelist.isAllowed(roleCode)) {
    log.warn("UserDetails denied for OSS user={}: roleCode={} not allowed", user.getUsername(), roleCode);
    throw new AuthenticationException("角色未授权，不允许访问") {};
}
```

**含义**：
- OSS 用户的 DB `roleCode` 必须在白名单中，否则登录被拒
- 但 OSS 用户的**实际权限**来自 `OssPermissionCache`（实时从 OSS 抓取），不依赖 DB `roleProfile`
- 因此 `skipUnmappedUsers=false` 创建的"无角色用户"需要后续补角色，或由 OSS 缓存动态决定权限

### 4.3 LoginRoleWhitelist 的单一真相源

```java
// LoginRoleWhitelist.java
public static boolean isAllowed(String roleCode) {
    return RoleProfileCatalog.isRegisteredCode(roleCode);
}
```

白名单由 `RoleProfileCatalog.DEFINITIONS` map 驱动，当前 7 个标准角色：

| RoleProfile code | 角色名称 | 配置规则 |
|---|---|---|
| `admin` | 管理员 | 按人员 |
| `/bidAdmin` | 投标管理员 | 按人员 |
| `bid-TeamLeader` | 投标组长 | 按人员 |
| `bid-projectLeader` | 投标项目负责人 | 按岗位 |
| `bid-Team` | 投标专员 | 按部门 |
| `bid-administration` | 行政人员 | 按部门 |
| `bid-otherDept` | 跨部门协同人员 | 按人员 |

---

## 5. OSS 接口返回数据格式

### 5.1 getUserByTimeWindow 实测返回

```json
{
  "name": "张三",
  "email": "zhangsan@ehsy.com",
  "mobilePhone": "13800138000",
  "deptId": 123,
  "jobNumber": "00123"
}
```

### 5.2 不返回的字段

| 字段 | 影响 | 处理方式 |
|------|------|---------|
| `deptName` | 无法直接填 `department_name` | `enrichDepartmentName` 用 `deptId` 反查 `organization_departments` 表 |
| `departmentCode` | 同上 | 用 `deptId` 作为 `departmentCode` |
| `positionName` | 无法按岗位映射角色 | 调 `batch-job-role-lookup-path` 接口批量查询 |

### 5.3 departmentCode 的实际含义

**重要**：`OrganizationDirectoryJsonMapper` 中 `firstText(node, "deptCode", "departmentCode", "deptId")` 会取到 `deptId`（整数）。

因此 `users.department_code` 实际存的是 **OSS 的 `external_dept_id`**，不是部门编码字符串。这会影响：
- `OrganizationDepartmentRepository.findBySourceAppAndExternalDeptIdIn()` 查询
- 列表场景下通过 `users.department_code` 反查部门名

---

## 6. 部门名称补充机制

### 6.1 enrichDepartmentName 方法

```java
private OrganizationUserSnapshot enrichDepartmentName(String sourceApp, OrganizationUserSnapshot snapshot) {
    String deptName = snapshot.departmentName();
    if ((deptName == null || deptName.isBlank())
            && snapshot.departmentCode() != null && !snapshot.departmentCode().isBlank()) {
        deptName = organizationDepartmentRepository
                .findBySourceAppAndExternalDeptId(sourceApp, snapshot.departmentCode())
                .map(OrganizationDepartmentEntity::getDepartmentName)
                .orElse(deptName);
    }
    return snapshot;
}
```

### 6.2 前置条件

- `organization_departments` 表必须有对应部门数据
- 部门同步必须先于人员同步执行（或至少在同一次 sync-run 中先执行部门阶段）

### 6.3 collation 陷阱

**事故**：`users` 表 = `utf8mb4_unicode_ci`，`organization_departments` 表 = `utf8mb4_0900_ai_ci`，JOIN 时报 `Illegal mix of collations`。

**修复**：V1092 迁移脚本将临时表显式指定 `COLLATE utf8mb4_unicode_ci`。

**教训**：临时表必须显式指定 `COLLATE` 与关联表对齐。

---

## 7. 全量同步排障 Checklist

### 7.1 同步前检查

```bash
# 1. 确认同步开关已开启
ssh server 'grep XIYU_ORG_SYNC /etc/xiyu-bid/backend.env'
# 期望：XIYU_ORG_SYNC_ENABLED=true, XIYU_ORG_SYNC_SKIP_UNMAPPED_USERS=false

# 2. 确认 OSS 接口可达
ssh server 'curl -s -o /dev/null -w "%{http_code}" ${OSS_BASE_URL}/subscription/msg/getUserByTimeWindow'
# 期望：200 或 405（405 表示接口存在但方法不对，也算可达）

# 3. 确认部门表有数据
ssh server 'mysql -e "SELECT COUNT(*) FROM organization_departments"'
# 期望：>0（部门同步必须先执行）
```

### 7.2 同步后验证

```bash
# 1. 用户数量交叉验证
ssh server 'mysql -e "SELECT COUNT(*) FROM users WHERE external_org_source_app=\"oss\""'
# 与同步报告的 successCount 对比

# 2. enabled 用户数量
ssh server 'mysql -e "SELECT COUNT(*) FROM users WHERE external_org_source_app=\"oss\" AND enabled=1"'
# 期望与 OSS 启用人员数量接近

# 3. 角色分布
ssh server 'mysql -e "SELECT role_code, COUNT(*) FROM users WHERE external_org_source_app=\"oss\" GROUP BY role_code"'
# 检查角色映射是否合理

# 4. NULL 角色用户数量
ssh server 'mysql -e "SELECT COUNT(*) FROM users WHERE external_org_source_app=\"oss\" AND role_code IS NULL"'
# skipUnmappedUsers=false 时会有 NULL 角色用户，这是正常的
```

### 7.3 常见问题

| 问题 | 根因 | 解决方案 |
|------|------|---------|
| 同步报告 successCount=11000 但 users 表只有 168 | skipUnmappedUsers=true 且代码未使用配置 | 设置 `XIYU_ORG_SYNC_SKIP_UNMAPPED_USERS=false` + 部署修复代码 |
| 86 条失败 `手机号不能为空` | OSS 数据质量问题 | 数据清洗，非代码 bug |
| `Illegal mix of collations` | 表 collation 不一致 | V1092 迁移修复 |
| `eventTopicConsumerMap is null` | event-busserver 未配置事件订阅 | 全量同步不依赖 Kafka，可忽略 |
| admin 登录 403 | 本地 HTTP_PROXY 干扰 curl | ssh 到服务器内部测试，或 `curl --noproxy '*'` |

---

## 8. Kafka 增量同步（事件库 SDK）

### 8.1 工作原理

```
Kafka topic: BaseOssUser / BaseOssDept / BaseOssJob
  ↓
OrganizationEventSdkConsumerAdapter 消费
  ↓
OrganizationDirectorySyncAppService.syncSingleUser / syncSingleDepartment
  ↓
OrganizationUserSyncWriter.upsert（同全量同步路径）
```

### 8.2 配置

```bash
# /etc/xiyu-bid/backend.env
XIYU_ORG_EVENT_SDK_ENABLED=true
XIYU_ORG_EVENT_CONSUMER_GROUP=bms
```

### 8.3 失败处理

**`eventTopicConsumerMap is null`**：

- **根因**：event-busserver 上未配置 `BidSystemOrgConsumer` 的事件订阅
- **影响**：Kafka 增量同步不工作，但不影响全量同步
- **解决**：协调客户运维在 event-busserver 配置事件订阅

### 8.4 首次部署策略

1. **首次部署**：通过全量同步 API 拉取人员数据（不依赖 Kafka）
2. **后续增量**：配置 Kafka 事件订阅后，自动实时同步人员变更
3. **Kafka 失败不阻塞首次部署**

---

## 9. 相关文档

- [[integration-organization-event-sdk]] — 事件库 SDK 方案设计
- [[roles-and-permissions]] — 角色与权限
- [[production-deployment-lessons]] — 生产部署实战教训
- [[deployment]] — 部署与上线
- `backend/src/main/resources/application.yml` §152-257 — 完整配置

---

## 10. 变更记录

| 日期 | 变更内容 |
|------|---------|
| 2026-07-09 | 首次创建，沉淀首次生产部署的 OSS 同步实战经验 |
