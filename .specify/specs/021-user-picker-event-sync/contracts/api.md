# API Contract: 统一候选人端点

**Date**: 2026-06-24 | **Feature**: 021-user-picker-event-sync

## 新增端点

### GET /api/users/assignable-candidates

获取可指派候选人列表，按当前用户数据权限过滤。

**Authorization**: `@PreAuthorize("isAuthenticated()")`

**Query Parameters**:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `context` | String | 是 | 业务场景：`task`（任务指派）或 `tender`（标讯分配） |
| `deptCode` | String | 否 | 按部门码过滤 |
| `roleCode` | String | 否 | 按角色码过滤 |

**Response 200**:

```json
{
  "success": true,
  "data": [
    {
      "userId": 1,
      "name": "张三",
      "employeeNumber": "EMP001",
      "roleCode": "bid_admin",
      "roleName": "投标管理员",
      "deptCode": "BID_DEPT",
      "deptName": "投标管理部",
      "enabled": true
    }
  ]
}
```

**Response 400**（无效 context）:

```json
{
  "success": false,
  "error": "Invalid context parameter. Valid values: task, tender"
}
```

**权限过滤逻辑**:
1. 查询本地 users 表所有 `enabled=true` 用户
2. 通过 `RoleProfileService.hasGlobalAccess(currentUser)` 判断是否全局权限
3. 非全局权限时，通过 `ProjectAccessScopeService.getAllowedDepartmentCodes(currentUser)` 获取可见部门列表
4. 仅返回当前用户可见部门内的候选人
5. 可选按 `deptCode` / `roleCode` 参数进一步过滤
6. 排序：departmentCode → roleName → fullName（nullsLast, CASE_INSENSITIVE）

---

## 废弃端点（保持向后兼容）

### GET /api/tasks/assignment-candidates

**状态**: `@Deprecated`，内部委托 `AssignmentCandidateAppService`

**行为变更**: 权限过滤逻辑不变（已有），返回结构从 `TaskAssignmentCandidateDTO` 改为 `AssignmentCandidateDTO`（字段兼容，新增字段）。

### GET /api/tenders/assignment-candidates

**状态**: `@Deprecated`，内部委托 `AssignmentCandidateAppService`

**行为变更**: 新增权限过滤（修复当前无过滤安全隐患），返回结构从 `TenderAssignmentCandidateResponse` 改为 `AssignmentCandidateDTO`。

**⚠️ 破坏性变更**: 此端点之前返回所有启用用户（无权限过滤），统一后将按当前用户权限过滤。前端需确认所有调用方有权限看到预期候选人。

---

## 保持不变端点

### GET /api/users/search

用户模糊搜索，保持不变。

**Query Parameters**:

| 参数 | 类型 | 必填 | 默认 | 说明 |
|------|------|------|------|------|
| `q` | String | 否 | "" | 搜索关键字（姓名或用户名模糊匹配） |
| `limit` | Integer | 否 | 10 | 返回条数上限（max 50） |

**Response 200**:

```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "name": "张三",
      "employeeNumber": "EMP001",
      "role": "MANAGER",
      "departmentName": "投标管理部",
      "roleCode": "bid_admin"
    }
  ]
}
```

**数据源**: 本地 users 表（`enabled=true` AND `full_name LIKE '%q%' OR username LIKE '%q%'`）

---

## 前端 API 封装

### src/api/modules/users.js（新增方法）

```javascript
// 新增：统一候选人 API
async getAssignableCandidates(params = {}) {
  const { context, deptCode, roleCode } = params
  const response = await httpClient.get('/api/users/assignable-candidates', {
    params: { context, deptCode, roleCode }
  })
  return response?.data || []
}

// 保持不变：用户搜索
async search(query, limit = 10) {
  const { data } = await httpClient.get('/api/users/search', { params: { q: query, limit } })
  return data
}
```

### 废弃方法（标记 @deprecated，保持兼容）

```javascript
// @deprecated 使用 getAssignableCandidates({ context: 'task' }) 替代
async getTaskAssignmentCandidates(params = undefined) { ... }

// @deprecated 使用 getAssignableCandidates({ context: 'tender' }) 替代
// 注：此方法在 batch.js 中，不在 users.js
```
