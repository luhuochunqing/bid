# Contract: Login Behavior After staff Removal

## Endpoint

`POST /api/auth/sessions`

## Request

```json
{
  "username": "00939",
  "password": "..."
}
```

## Success Response

**Status**: `200 OK`

**Condition**: OSS 认证成功、角色映射到 7 个有效角色之一、OSS 在职。

```json
{
  "token": "<jwt>",
  "user": {
    "id": 123,
    "username": "00939",
    "roleCode": "bid_specialist",
    "enabled": true
  }
}
```

## Error Responses

### Role Not Authorized

**Status**: `403 Forbidden`

**Condition**: OSS 认证成功，但角色未映射到 7 个有效角色（即普通员工/未映射）。

```json
{
  "code": "ROLE_NOT_AUTHORIZED",
  "message": "当前账号无系统访问权限"
}
```

### Account Disabled (OSS Inactive)

**Status**: `403 Forbidden`

**Condition**: OSS 认证成功，角色有效，但 OSS 返回离职/停用状态。

```json
{
  "code": "ACCOUNT_DISABLED",
  "message": "账号已停用"
}
```

### Authentication Failed

**Status**: `401 Unauthorized`

**Condition**: OSS 认证失败（密码错误、账号不存在于 OSS 等）。

```json
{
  "code": "AUTHENTICATION_FAILED",
  "message": "用户名或密码错误"
}
```

## Local Account Exception

`DefaultAdminInitializer` / `LocalDevAccountInitializer` 创建的本地账号不调用 OSS，仅校验本地密码和 `enabled` 字段。
