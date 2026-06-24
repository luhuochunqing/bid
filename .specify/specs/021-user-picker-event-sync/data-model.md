# Data Model: 选人控件统一 + 事件库同步启用

**Date**: 2026-06-24 | **Feature**: 021-user-picker-event-sync

## 涉及的数据实体

### 1. users 表（已存在，补齐列）

**变更**: 新增 `employee_number` 列

| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `employee_number` | VARCHAR(32) | NULLABLE | 工号，OSS 同步时填充 |

**迁移脚本**: `V1093__add_users_employee_number.sql`
```sql
ALTER TABLE users ADD COLUMN employee_number VARCHAR(32) NULL;
CREATE INDEX idx_users_employee_number ON users(employee_number);
```

**回滚脚本**: `U1093__add_users_employee_number.sql`
```sql
DROP INDEX idx_users_employee_number ON users;
ALTER TABLE users DROP COLUMN employee_number;
```

**现有字段（无变更）**:

| 列名 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| username | VARCHAR(255) UNIQUE | 用户名 |
| password | VARCHAR(255) | BCrypt 哈希 |
| email | VARCHAR(255) UNIQUE | 邮箱 |
| phone | VARCHAR(32) | 电话 |
| full_name | VARCHAR(255) | 姓名 |
| role | ENUM('ADMIN','MANAGER') | 旧角色枚举（V1091 移除 STAFF） |
| role_id | BIGINT FK → roles.id | RoleProfile 关联 |
| enabled | BOOLEAN | 启用状态（事件库同步离职时设为 false） |
| department_code | VARCHAR(100) | 部门码 |
| department_name | VARCHAR(100) | 部门名 |
| external_org_user_id | VARCHAR(128) | OSS 外部用户 ID |
| external_org_source_app | VARCHAR(100) | OSS 来源应用 |
| last_org_event_key | VARCHAR(128) | 最后事件 key（幂等用） |
| last_org_synced_at | TIMESTAMP | 最后同步时间 |

**唯一约束**: `(external_org_source_app, external_org_user_id)` — V106 已建立

---

### 2. AssignmentCandidateDTO（新增）

统一候选人返回结构，替代当前两个分裂的 DTO。

```java
public record AssignmentCandidateDTO(
    Long userId,
    String name,              // fullName
    String employeeNumber,   // 工号
    String roleCode,          // RoleProfile.code
    String roleName,          // RoleProfile.displayName
    String deptCode,          // departmentCode
    String deptName,          // departmentName
    Boolean enabled           // 启用状态（统一为 true）
) {}
```

**替代**:
- `TaskAssignmentCandidateDTO`（8 字段，结构相似）
- `TenderAssignmentCandidateResponse`（5 字段，缺 employeeNumber/roleName/deptCode/enabled）

---

### 3. AssignmentContext（新增，Pure Core）

业务场景上下文，用于候选人过滤策略。

```java
public record AssignmentContext(
    String contextType,   // "task" | "tender"
    String deptCode,      // 可选：预过滤部门
    String roleCode       // 可选：预过滤角色
) {}
```

---

### 4. UserPickerOption（前端类型，新增）

统一选人组件的选项类型。

```typescript
interface UserPickerOption {
  id: number
  name: string
  employeeNumber: string | null
  roleCode: string
  roleName: string
  deptCode: string
  deptName: string
}
```

---

## 数据流

### 事件库同步数据流

```
OSS 系统用户变更
  → Kafka topic: BaseOssUser
  → OrganizationEventSdkConsumerAdapter.onUserChanged()
  → OrganizationEventAppService.receiveViaSdk()
  → OrganizationDirectorySyncAppService.receiveWebhook()
  → lookupAndWrite(USER_NOTICE)
  → OrganizationDirectoryHttpGateway.fetchUserByUserId()  [回查 OSS]
  → OrganizationUserSyncWriter.upsert()
  → 本地 users 表 upsert
```

### 选人控件数据流

```
用户输入关键字
  → UserPicker.vue (mode=search)
  → useUserPicker.search()
  → GET /api/users/search?q=&limit=
  → UserSearchService.search()
  → UserRepository.searchActiveUsers()  [查本地 users 表]
  → 返回 UserSearchResult 列表
  → UserPicker 显示下拉选项
```

```
用户打开候选人列表
  → UserPicker.vue (mode=candidates)
  → useUserPicker.loadCandidates()
  → GET /api/users/assignable-candidates?context=task
  → AssignmentCandidateAppService.getCandidates()
  → UserRepository.findByEnabledTrue()  [查本地 users 表]
  → ProjectAccessScopeService.getAllowedDepartmentCodes()  [权限过滤]
  → AssignmentCandidatePolicy.filter()  [纯核心过滤]
  → 返回 AssignmentCandidateDTO 列表
  → UserPicker 显示下拉选项
```

---

## 实体关系

```
users (本地用户表)
  ├── role_id → roles (RoleProfile)
  ├── department_code → organization_departments.department_code (逻辑关联，无 FK)
  └── external_org_user_id + external_org_source_app → OSS 用户 (外部关联)

organization_departments (本地部门表)
  ├── department_code (业务主键)
  └── parent_department_code → organization_departments.department_code (自关联)

organization_event_logs (事件日志表)
  └── 记录所有事件库消费的事件（幂等去重用）
```

---

## 验证规则

- `AssignmentCandidateDTO.enabled` 始终为 `true`（查询时已过滤 `enabled=true`）
- `UserPickerOption.id` 不可为 null（前端 v-model 绑定用）
- `AssignmentContext.contextType` 必须为 `"task"` 或 `"tender"`，否则返回 400
- 事件库同步时 `last_org_event_key` 用于幂等校验，相同 key 不重复处理
