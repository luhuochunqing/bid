# Contract: User Picker Behavior

## Endpoints

- `GET /api/mentions/users?keyword={keyword}`
- `GET /api/tenders/assignment-candidates?...`
- `GET /api/tasks/{taskId}/assignment-candidates`

## Common Behavior

All user picker endpoints MUST return users where `User.enabled = true`.

They MUST NOT filter by `roleCode` unless explicitly required by a specific business rule (e.g., a picker specifically for "project leaders only").

## Response Shape

```json
{
  "data": [
    {
      "id": 123,
      "username": "00939",
      "displayName": "南自婷",
      "roleCode": "bid_specialist",
      "roleName": "投标专员",
      "departmentName": "投标管理部"
    }
  ]
}
```

## Invariants

- A user with `enabled = false` MUST NOT appear in any picker response.
- A user whose `roleCode` is null or not in the 7-role whitelist SHOULD NOT be queryable via picker (because such users will have `enabled = false`).
