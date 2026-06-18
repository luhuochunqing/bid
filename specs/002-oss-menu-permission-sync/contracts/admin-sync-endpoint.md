# Contract: Admin OSS Menu Sync Endpoint

## Endpoint

`POST /api/admin/roles/{id}/sync-oss-menu-permissions`

## Request

### Path

| Parameter | Type | Description |
|---|---|---|
| id | Long | Internal role id |

### Body

```json
{
  "jobNumber": "08402"
}
```

| Field | Type | Description |
|---|---|---|
| jobNumber | String | Representative OSS user's job number |

## Response

### 200 OK

```json
{
  "code": 200,
  "message": "Role menu permissions synchronized from OSS",
  "data": {
    "id": 42,
    "code": "bid_project_manager",
    "name": "投标项目负责人",
    "menuPermissions": ["project.manager", "bidding", "dashboard"]
  }
}
```

### 400 Bad Request

- Role not found
- jobNumber missing or blank
- OSS menu tree empty and policy requires at least one permission

### 502 Bad Gateway

- OSS interface returned non-2xx or timeout
