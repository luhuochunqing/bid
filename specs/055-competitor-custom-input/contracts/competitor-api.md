# API 契约：竞品字典 (055)

Base path：`/api/ai/competition`（复用现有 `CompetitionIntelController`）

## 端点总览

| Method | Path | 权限 | 语义 |
|---|---|---|---|
| GET | `/competitors` | `isAuthenticated()` | 列表（默认仅 ACTIVE；`?includeDisabled=true` 返回全部，供治理页） |
| POST | `/competitors` | `hasAuthority('competitor.create')` | 幂等 upsert（按 name 查重，存在返回已有，不存在创建） |
| PUT | `/competitors/{id}` | `hasAuthority('competitor.govern')` | 改名 |
| POST | `/competitors/{id}/merge` | `hasAuthority('competitor.govern')` | 合并到目标竞品 |
| POST | `/competitors/{id}/disable` | `hasAuthority('competitor.govern')` | 软停用 |

## 请求/响应

### GET /competitors

Query：`includeDisabled`（bool，默认 false）

Response：`ApiResponse<List<CompetitorDTO>>`

```json
{ "ok": true, "data": [ { "id": 1, "name": "震坤行", "status": "ACTIVE" } ] }
```

### POST /competitors（upsert）

Body：`CompetitorUpsertRequest`

```json
{ "name": "震坤行" }
```

Response：`ApiResponse<CompetitorDTO>`（201 新建 / 200 已存在，语义上统一返回 CompetitorDTO）

### PUT /competitors/{id}

Body：`{ "name": "新名称" }`

Response：`ApiResponse<CompetitorDTO>`

### POST /competitors/{id}/merge

Body：`{ "targetId": 2 }`

Response：`ApiResponse<CompetitorDTO>`（返回 target 竞品）

### POST /competitors/{id}/disable

Body：无

Response：`ApiResponse<CompetitorDTO>`（返回 `status=DISABLED` 的竞品）

## 错误语义

- 名称重复（唯一约束冲突）→ 返回已有竞品（幂等），不报错。
- 合并目标不存在 / 源目标相同 → 400 参数错误。
- 非 `competitor.govern` 角色调用治理端点 → 403。
- 非 `competitor.create` 角色调用 upsert → 403。

## 前端归一化对齐

`normalizeCompetitor` 改为与后端 `CompetitorDTO` 一致：
`{ id, name, industry, strengths, weaknesses, marketShare, typicalBidRangeMin, typicalBidRangeMax, status, createdAt }`
（移除现有一批后端不返回的假字段 region/website/winRate 等）。
