# CRM Interface Contracts

**Date**: 2026-07-16
**Feature**: 037-crm-link-compensation
**Status**: Phase 1 Design

本文件描述本 spec 涉及的 CRM 接口契约。

---

## 1. `POST /common/inner/generateToken` — 生成 CRM JWT

### 当前实现（修改前）

```http
POST /common/inner/generateToken
Authorization: Bearer {OSS access_token}
Content-Type: application/json

{
  "nickName": "王旭州",
  "salesNo": "04503"
}
```

**响应**（`code:0` 成功）:
```json
{
  "code": 0,
  "msg": "success",
  "data": "eyJhbGciOiJIUzI1NiJ9...(CRM JWT)"
}
```

### 修改后实现（本 spec）

```http
POST /common/inner/generateToken
Content-Type: application/json
(无 Authorization header)

{
  "nickName": "王旭州",
  "salesNo": "04503"
}
```

**响应**: 同上。

### 验证证据

- **测试环境**（2026-07-16）：不带 Authorization，仅传 `{"nickName":"王旭州","salesNo":"04503"}`，返回 `code:0` + 有效 JWT
- **生产环境**（2026-07-16）：同上，用返回的 JWT 调 `/customer-chance/page-list` 成功查到商机详情

### 调用方变更

| 调用方 | 修改前 | 修改后 |
|---|---|---|
| `CrmAuthService.applyCrmTokenWithOssToken(ossToken, nickName, salesNo)` | `httpClient.postWithAuth(baseUrl, path, ossToken, body)` | `httpClient.postJson(baseUrl, path, body)` |
| 方法名 | `applyCrmTokenWithOssToken` | `applyCrmToken`（重命名） |

### 失败场景

| 场景 | CRM 返回 | 本地行为 |
|---|---|---|
| `nickName` 或 `salesNo` 在 CRM 中不存在 | `code:1, msg:"user not found"` | 抛 `TokenUnavailableException` |
| CRM 服务不可用 | HTTP 5xx / 超时 | 抛 `TokenUnavailableException`，调用方降级 |
| CRM JWT 过期（使用阶段） | 业务接口返回 401 | `handleUnauthorizedForUser` 清缓存 + 重试一次 |

---

## 2. `POST /customer-chance/page-list` — 按条件查商机

### 当前实现（`findByCode` 用）

```http
POST /customer-chance/page-list
Authorization: Bearer {CRM JWT}
Content-Type: application/json

{
  "pageNum": 1,
  "pageSize": 1,
  "code": "CC2026071568"
}
```

**响应**:
```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "totalCount": 1,
    "dataList": [
      {
        "id": 6,
        "code": "CC2026071568",
        "name": "中国旅游集团 2026年-2029年电子超市（内地）集中采购",
        "bidId": 7,
        "projectLeaderNo": "04503",
        "projectLeaderName": "王旭州"
      }
    ]
  }
}
```

### 新增查询方式（本 spec `findByBidId`）

```http
POST /customer-chance/page-list
Authorization: Bearer {CRM JWT}
Content-Type: application/json

{
  "pageNum": 1,
  "pageSize": 10,
  "bidId": 7
}
```

**响应**: 同上结构，`dataList` 返回该 bidId 关联的商机（通常 1 条）。

### 验证计划

- 实现阶段第一个任务：用 curl 实测 `{"bidId": "7"}` 是否被支持
- 若不支持，fallback 为 `{"projectLeaderNo": "04503"}` 查全部 + 本地按 bidId 过滤

### 调用方变更

| 调用方 | 修改前 | 修改后 |
|---|---|---|
| `CrmTenderLinkService.linkByChanceIdIfPresent` | 调 `findProjectLeaderByChanceId(chanceId, username)` → detail 接口 | 改调 `findProjectLeaderByBidId(bidId, username)` → page-list 接口 |
| 方法名 | `linkByChanceIdIfPresent` | `linkByBidIdIfPresent`（重命名，语义清晰） |

---

## 3. `POST /customer-chance/detail` — 按商机 ID 查详情（保留，本 spec 不再使用）

### 当前实现

```http
POST /customer-chance/detail?id={chanceId}
Authorization: Bearer {CRM JWT}
```

**响应**: 同 page-list 单条结构。

### 本 spec 处置

- **不删除** `CrmChanceDetailService` / `findProjectLeaderByChanceId`
- **不再调用**：`CrmTenderLinkService.linkByBidIdIfPresent` 改用 page-list
- 保留 detail 接口供其他场景使用（如未来按 chanceId 直查）

---

## 4. 本系统内部契约

### 4.1 `CrmTenderLinkService.linkByBidIdIfPresent`（重命名后）

**Signature**:
```java
public boolean linkByBidIdIfPresent(Tender tender, String sourceSystem, String sourceId, String username)
```

**Behavior**:
1. `sourceSystem != "CRM"` → return false
2. `sourceId` 非数字 → return false
3. 解析 sourceId 为 `Long bidId`
4. 调 `crmProjectLeaderService.findProjectLeaderByBidId(bidId, username)`
5. leader 为 null 或 opportunityCode 为空 → return false + log.warn
6. 调 `applyLeaderAndStatus(tender, leader)` → return true

**Caller**（不变）:
- `TenderIntegrationCommandSupport.applyCrmFallback` L113 — 传 `tender.external_id` 解析出的 sourceSystem / sourceId

### 4.2 `CrmAuthService.applyCrmToken`（重命名后）

**Signature**:
```java
String applyCrmToken(String nickName, String salesNo)
```

**Behavior**:
1. 构造 body `{"nickName":"...","salesNo":"..."}`
2. 调 `httpClient.postJson(baseUrl, generateTokenPath, body)`（无 Authorization）
3. response.success() && data.isTextual() → 返回 JWT
4. 否则抛 `TokenUnavailableException`

**Caller**:
- `CrmAuthService.fetchAndCacheUserToken` L102 — 改调 `applyCrmToken(nickName, salesNo)`（不再传 ossToken）

### 4.3 `OrganizationUserSyncWriter.upsert`（修改后）

**新增一行**（L102-106 附近，与 `setUsername` / `setEmployeeNumber` 同块）:
```java
user.setCrmSalesNo(snapshot.username());  // OSS 工号即 CRM 工号（已验证）
```

**Caller**（不变）:
- OSS 事件监听器 → `upsert(sourceApp, eventKey, snapshot, jobRoleLookupMap)`

---

## 5. 错误处理契约

### 5.1 关联失败降级（spec FR-008 / FR-010）

```
linkByBidIdIfPresent 失败
  ├─ sourceSystem != "CRM" → silent return false
  ├─ sourceId 非数字 → log.warn + return false
  ├─ CRM 接口异常 → log.error + return false（不抛异常）
  └─ 商机不存在 → log.warn + return false

调用方 applyCrmFallback
  └─ linked == false → 保持标讯原状态（PENDING_ASSIGNMENT），不阻塞主流程
```

### 5.2 generateToken 失败（spec FR-010）

```
applyCrmToken 失败
  └─ 抛 TokenUnavailableException

调用方 fetchAndCacheUserToken
  └─ 异常向上抛

调用方 getValidTokenForUser
  └─ 异常向上抛

调用方 linkByBidIdIfPresent
  └─ catch RuntimeException → log.error + return false（降级）
```

### 5.3 OSS 同步失败（不影响本 spec 新增逻辑）

`OrganizationUserSyncWriter.upsert` 已有 `@Transactional(REQUIRES_NEW)`，`setCrmSalesNo` 失败会随整个事务回滚。

---

## 6. 不变量

修改前后必须保持的不变量：

1. `tender.crm_opportunity_id` 只能是 CC 格式编号（非纯数字），由 `isCcFormatCode` 校验
2. `tender.crm_opportunity_id` 与 `tender.crm_opportunity_name` 同时设置或同时为 NULL（不允许"半关联"）
3. `linkByBidIdIfPresent` 不修改 `tender.status` 到 `EVALUATED`（由 `applyLeaderAndStatus` 设置）
4. `users.crm_sales_no` 填充后必须等于 `users.username`（OSS 同步用户）
5. `generateToken` 返回的 JWT 必须可用于 `page-list` / `detail` 接口（401 时触发联合清理）
