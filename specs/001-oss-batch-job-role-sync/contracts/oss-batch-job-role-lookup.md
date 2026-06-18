# Contract: OSS Batch Job/Role Lookup

**Endpoint**: `POST /oss/admin-web/v1/output/data/getUserJobListByJobNumberList`

**Base URL**: configured via `xiyu.integrations.organization.directory.base-url`

**Content-Type**: `application/json`

## Request

### Body

```json
{
  "data": ["08402", "08640"]
}
```

### Schema

| Field | Type | Required | Description |
|---|---|---|---|
| `data` | `string[]` | Yes | 员工工号列表，单次建议不超过 50 个 |

## Response

### Success (HTTP 200)

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "jobNumber": "08402",
      "jobName": "项目经理",
      "sysRoleList": ["投标项目负责人"],
      "employeeStatus": "在职",
      "status": "启用",
      "username": "张三"
    },
    {
      "jobNumber": "08640",
      "jobName": "项目总监",
      "sysRoleList": ["投标项目负责人", "管理员"],
      "employeeStatus": "在职",
      "status": "启用",
      "username": "李四"
    }
  ]
}
```

### Response Schema

| Field | Type | Required | Description |
|---|---|---|---|
| `code` | `int` | Yes | 业务状态码，200 表示成功 |
| `message` | `string` | Yes | 业务提示信息 |
| `data` | `object[]` | No | 用户岗位/角色列表 |

#### `data` item schema

| Field | Type | Required | Description |
|---|---|---|---|
| `jobNumber` | `string` | Yes | 员工工号 |
| `jobName` | `string` | No | 岗位名称 |
| `sysRoleList` | `string[]` | No | 系统角色名称列表 |
| `employeeStatus` | `string` | No | 在职状态，如"在职"、"离职" |
| `status` | `string` | No | 账号状态，如"启用"、"禁用" |
| `username` | `string` | No | 用户姓名 |

## Error Responses

| HTTP Status | Meaning | Handling |
|---|---|---|
| 400 | 请求参数错误 | 记录错误，降级为空结果 |
| 500 | OSS 内部错误 | 记录错误，降级为空结果 |
| timeout | 连接/读取超时 | 记录错误，降级为空结果 |

## Consumer Contract

- 调用方 MUST 将工号列表按配置的 `batch-query-size` 分片。
- 调用方 MUST 对返回结果按 `jobNumber` 建立索引。
- 调用方 MUST 处理 `data` 为空或部分工号缺失的情况。
- 调用方 MUST 记录每次调用的请求规模、响应数、耗时与错误信息。
