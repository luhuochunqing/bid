# Data Model: CRM 商机关联补偿与认证解耦

**Date**: 2026-07-16
**Feature**: 037-crm-link-compensation
**Status**: Phase 1 Design

本文件描述涉及的实体、字段、关系和状态转换。**本 spec 不涉及 schema 变更**，仅利用已有列。

---

## 1. 实体：Tender（标讯）

**Table**: `tenders`
**Entity**: `com.xiyu.bid.entity.Tender`

### 关键字段（本 spec 涉及）

| 字段 | 类型 | 说明 | 本 spec 是否修改 |
|---|---|---|---|
| `id` | BIGINT PK | 标讯主键 | 否 |
| `external_id` | VARCHAR | 格式 `{sourceSystem}:{sourceId}`，如 `CRM:7`；7 是 CRM 标讯 ID（bidId） | 否 |
| `source_type` | ENUM | `CRM_OPPORTUNITY` / `MANUAL` / 等 | 否（`applyCrmFallback` 调用方设置） |
| `crm_opportunity_id` | VARCHAR | CRM 商机编号 code（CC 格式，如 `CC2026071568`）；NULL 表示未关联 | 是（由 `linkByBidIdIfPresent` 写入） |
| `crm_opportunity_name` | VARCHAR | CRM 商机名称 | 是（由 `linkByBidIdIfPresent` 写入） |
| `project_manager_id` | BIGINT FK→users.id | 项目负责人本地用户 ID | 是（由 `applyLeaderAndStatus` 写入） |
| `project_manager_name` | VARCHAR | 项目负责人姓名 | 是（由 `applyLeaderAndStatus` 写入） |
| `status` | ENUM | `PENDING_ASSIGNMENT` / `EVALUATED` / `TRACKING` / 等 | 是（关联成功时设为 `EVALUATED`） |

### external_id 格式契约

```
external_id = "{sourceSystem}:{sourceId}"

示例:
  "CRM:7"       → sourceSystem="CRM", sourceId="7"（7 是 CRM 标讯 ID bidId）
  "MANUAL:56"   → sourceSystem="MANUAL", sourceId="56"（本地标讯 ID）
```

**关键澄清**：`sourceId` 在 CRM 场景下是**标讯 ID（bidId）**，不是商机 ID（chanceId）。CRM 商机 id=6, bidId=7, code=CC2026071568。

### 状态转换（CRM 推送路径）

```
[新建] → PENDING_ASSIGNMENT
         │
         ├─ 关联成功（linkByBidIdIfPresent 命中商机）→ EVALUATED
         │
         └─ 关联失败（CRM 接口异常 / 商机不存在）→ PENDING_ASSIGNMENT（保持）
              log.warn("linkByBidIdIfPresent: no opportunity found for bidId={}")
```

---

## 2. 实体：User（用户）

**Table**: `users`
**Entity**: `com.xiyu.bid.entity.User`

### 关键字段（本 spec 涉及）

| 字段 | 类型 | 说明 | 本 spec 是否修改 |
|---|---|---|---|
| `id` | BIGINT PK | 用户主键 | 否 |
| `username` | VARCHAR | OSS 工号（如 `04503`） | 否 |
| `employee_number` | VARCHAR | 员工工号（CO-441 后与 username 一致） | 否 |
| `crm_sales_no` | VARCHAR | CRM 工号（= OSS 工号）；当前全表 NULL | 是（OSS 同步时填充） |
| `full_name` | VARCHAR | 用户姓名（作为 generateToken 的 nickName） | 否 |
| `external_org_source_app` | VARCHAR | `oss` 表示 OSS 同步用户 | 否 |

### `crm_sales_no` 填充规则

```
OSS 同步事件（snapshot.username = "04503"）
  → OrganizationUserSyncWriter.upsert
    → user.setCrmSalesNo(snapshot.username())  // 新增这一行
    → userRepository.save(user)

结果: users.crm_sales_no = "04503"（与 username / employee_number 一致）
```

### 三个字段的等价性

| 字段 | 来源 | 值（以王旭州为例） |
|---|---|---|
| `username` | OSS 同步 | `04503` |
| `employee_number` | CO-441 同步 | `04503` |
| `crm_sales_no` | 本 spec 新增填充 | `04503` |

三者等值（对 OSS 同步用户），但语义不同：`username` 是登录标识，`employee_number` 是 HR 工号，`crm_sales_no` 是 CRM 系统工号。

---

## 3. 外部实体：CRM 商机

**外部接口**: `POST /customer-chance/page-list` / `POST /customer-chance/detail`
**DTO**: `CrmProjectLeaderService.ProjectLeaderResult`（record）

### CRM 商机字段

| 字段 | 类型 | 说明 | 示例 |
|---|---|---|---|
| `id` | Long | 商机主键（chanceId） | `6` |
| `code` | String | 商机编号（CC 格式） | `CC2026071568` |
| `name` | String | 商机名称 | `中国旅游集团 2026年-2029年电子超市（内地）集中采购` |
| `bidId` | Long | 关联的标讯 ID | `7` |
| `projectLeaderNo` | String | 项目负责人工号 | `04503` |
| `projectLeaderName` | String | 项目负责人姓名 | `王旭州` |

### 关键关系

```
CRM 商机 (id=6, code=CC2026071568, bidId=7)
  ├─ 关联标讯: CRM 标讯 bidId=7
  ├─ 项目负责人: 04503 王旭州
  └─ 对应本地: tender.external_id="CRM:7", tender.crm_opportunity_id="CC2026071568"
```

### ProjectLeaderResult record

```java
public record ProjectLeaderResult(
    String projectLeaderName,   // "王旭州"
    String projectLeaderNo,     // "04503"
    String opportunityName,     // "中国旅游集团 2026年-2029年电子超市（内地）集中采购"
    String opportunityCode      // "CC2026071568"
) {}
```

---

## 4. 缓存实体：Redis

### Key 设计

| Key Pattern | TTL | 说明 | 本 spec 是否修改 |
|---|---|---|---|
| `oss:token:{username}` | OSS token 原始 TTL | 用户登录 OSS 时缓存的 access_token | 否（保留，但 generateToken 不再读取） |
| `crm:token:{username}` | CRM JWT TTL（从 JWT 解析） | generateToken 返回的 CRM JWT | 否（CrmUserTokenCache 逻辑不变） |
| `user:profile:{username}` | 5 分钟 | CachedUserProfile（含 crmSalesNo / fullName） | 否（profile 结构不变） |

### 缓存失效流程（401 联合清理）

```
CRM 接口返回 401
  → CrmAuthService.handleUnauthorizedForUser(username)
    → userTokenCache.invalidate(username)      // 清 CRM JWT
    → userProfileCache.invalidate(username)     // 清 profile
    → ossUserTokenCache.invalidate(username)    // 清 OSS token（保留，未来回退时需要）
```

本 spec 不修改此流程，仅修改 `fetchAndCacheUserToken` 不再读 OSS token。

---

## 5. 验证规则

### 5.1 `linkByBidIdIfPresent` 输入校验

| 输入 | 校验规则 | 失败行为 |
|---|---|---|
| `sourceSystem` | 必须为 `"CRM"` | 返回 false（no-op） |
| `sourceId` | 非空、非空白 | 返回 false |
| `sourceId` 数字解析 | 必须为合法 Long | 返回 false（log.warn） |
| `tender` | 非空 | NullPointerException（编程错误） |
| `username` | 可为 null（API Key 路径） | 降级：无法换 CRM JWT，返回 false |

### 5.2 `generateToken` 输入校验

| 输入 | 校验规则 | 失败行为 |
|---|---|---|
| `nickName` | 非空、非空白 | 抛 `TokenUnavailableException` |
| `salesNo` | 非空、非空白 | 抛 `TokenUnavailableException` |
| `username` | 非空、非空白（外层 `getValidTokenForUser` 校验） | 抛 `TokenUnavailableException` |
| CRM 返回 `code != 0` | — | 抛 `TokenUnavailableException` |

### 5.3 `OrganizationUserSyncWriter.upsert` 字段填充

| 字段 | 来源 | 校验 |
|---|---|---|
| `crm_sales_no` | `snapshot.username()` | 非空时填充；空时保持原值（不覆盖） |

---

## 6. 无 schema 变更声明

本 spec **不涉及任何 Flyway 迁移**：
- `tenders.crm_opportunity_id` / `crm_opportunity_name` 列已存在（V118 之前）
- `users.crm_sales_no` 列已存在（历史迁移）
- `tenders.external_id` 列已存在（B73 基线）
- 仅填充已有列的数据，不新增/修改/删除列

---

## 7. 实体关系图

```
┌─────────────────┐         ┌─────────────────┐
│   users (本地)   │         │  CRM 商机 (外部)  │
│─────────────────│         │─────────────────│
│ id (PK)         │◀────┐   │ id (chanceId)   │
│ username        │     │   │ code (CC...)    │
│ employee_number │     │   │ name            │
│ crm_sales_no ◀──┼─────┼───│ bidId           │
│ full_name       │     │   │ projectLeaderNo │
│ external_org_   │     │   │ projectLeaderName│
│   source_app    │     │   └─────────────────┘
└─────────────────┘     │
                        │
┌─────────────────┐     │
│  tenders (本地)  │     │
│─────────────────│     │
│ id (PK)         │     │
│ external_id ────┼─────┘  "CRM:7" → sourceId=7 = CRM bidId
│ source_type     │
│ crm_opportunity_id ◀── 写入 CRM 商机 code (CC...)
│ crm_opportunity_name ◀── 写入 CRM 商机 name
│ project_manager_id ◀── 关联 users.id (按 projectLeaderNo 匹配)
│ project_manager_name ◀── 关联 users.full_name
│ status          │
└─────────────────┘
```
