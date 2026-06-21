---
title: 标讯集成接口（外部系统对接）
space: engineering
category: integration
tags: [integration, api, tender, crm, callback, x-api-key]
sources:
  - docs/integration/标讯集成接口文档-v3.8.md
  - docs/integration/外部业务系统对接技术设计与接口规范说明.md
  - docs/integration/西域CRM商机对接接口.md
  - docs/integration/CO-262-crm-chance-fields-spec.md
  - docs/integration/crm-migration-guide.md
backlinks:
  - _index
  - api-openapi
  - integration-oa-crm
created: 2026-06-21
updated: 2026-06-21
health_checked: 2026-06-21
---
# 标讯集成接口（外部系统对接）

> 本页覆盖"外部系统 → 投标系统"的标讯集成接口规范，是 OA / CRM / ERP / 标讯平台等外部系统对接本平台的核心入口。
> 完整接口字段与示例见事实源：`docs/integration/标讯集成接口文档-v3.8.md`（v3.8, 2026-06-17）。

## 1. 接口概览

本平台面向外部系统开放 **4 个标讯接口** + **2 个出站回调**，统一通过 `X-API-Key` 认证：

| # | 接口名称 | 方法 | 路径 | 权限 | 说明 |
|---|---------|------|------|------|------|
| 1 | 标讯列表查询 | GET | `/api/integration/tenders` | `tender:read` | 模糊搜索 + 多维精准筛选，分页返回 |
| 2 | 标讯创建（幂等） | POST | `/api/integration/tenders/push` | `tender:write` | 按 (sourceSystem, sourceId) 幂等去重 |
| 3 | 标讯修改 | PUT | `/api/integration/tenders/{sourceSystem}/{sourceId}` | `tender:write` | 支持 tenderId 或 (sourceSystem, sourceId) 定位 |
| 4 | 标讯详情 | GET | `/api/integration/tenders/{sourceSystem}/{sourceId}` | `tender:read` | 返回完整标讯 + 评估数据 + 附件 |
| 5 | 标讯状态变更回调 | POST | 外部系统 webhook | — | 本平台 → 外部系统（标讯提交投标/弃标时） |
| 6 | 项目结果确认回调 | POST | 外部系统 webhook | — | 本平台 → CRM（结果登记完成后异步推送） |

## 2. 认证与通用约定

### 2.1 API Key 认证（入站接口 1-4）

- Header：`X-API-Key: <your-api-key>`
- 权限范围：
  - `tender:read` — 只读，适用于接口 1、4
  - `tender:write` — 读写，适用于接口 1、2、3、4
- 生产环境必须启用 HTTPS

### 2.2 JWT Bearer Token 认证（出站回调 5-6）

- 本平台调用外部系统时，通过 CRM 鉴权接口 `/common/inner/generateToken` 获取 JWT
- Header：`Authorization: Bearer <jwt-token>`
- 超时 30s，失败重试 1min / 5min / 15min 共 3 次

### 2.3 统一响应格式

```json
{
  "success": true,
  "code": 200,
  "msg": "查询成功",
  "data": {}
}
```

追踪 ID 通过响应头 `X-Trace-Id` 返回（不在 JSON body 中）。

### 2.4 时间格式（v3.2+）

| 类型 | 格式 | 示例 |
|------|------|------|
| 日期 | `yyyy-MM-dd` | "2026-06-15" |
| 日期时间 | `yyyy-MM-dd HH:mm`（空格分隔，到分） | "2026-06-15 10:00" |

入参同时兼容 `yyyy-MM-dd HH:mm`、`yyyy-MM-ddTHH:mm`、`yyyy-MM-ddTHH:mm:ss` 三种格式，服务端自动归一化。

## 3. 幂等性保障

- **幂等键**：`(sourceSystem, sourceId)` 组合，通过 `externalId` 字段（格式 `{sourceSystem}:{sourceId}`）建立 UNIQUE 唯一约束
- **重复推送行为**：
  - 不传 `forceUpdate`（默认 false）：返回 HTTP 409，data 中携带已有 `tenderId`
  - 传 `forceUpdate=true`：覆盖更新，返回 HTTP 200，`status=UPDATED`
- **接口 3/4 定位逻辑**：支持 `tenderId` 或 `(sourceSystem, sourceId)` 二选一定位（路径用 `_` 占位），找不到返回 404

## 4. 关键字段说明

| 字段 | 说明 |
|------|------|
| `externalId` | 本平台内部唯一标识，格式 `{sourceSystem}:{sourceId}` |
| `status` | 标讯业务状态，枚举见 §5.1 |
| `sourceType` | 来源类型，`EXTERNAL_PLATFORM` 表示外部推送 |
| `contactInfo` | v3.1+ 联系人数组，每项含 name/phone/tel/mail；同时保留 contactName 等扁平字段向下兼容 |
| `evaluation` | v3.1+ 项目评估嵌套对象，含基础信息段、客户信息矩阵、投标负责人建议三段 |
| `attachments` | v3.2+ 标讯附件数组，每项含 fileName/fileType/fileUrl |
| `tenderInfo` / `projectManagerName` / `department` / `creatorName` / `createDate` | v3.7+ 标讯创建接口新增 5 个字段 |

## 5. 枚举值

### 5.1 标讯状态（status）

| 枚举值 | 含义 |
|--------|------|
| PENDING_ASSIGNMENT | 待分配 |
| ASSIGNED | 已分配 |
| TRACKING | 跟踪中 |
| EVALUATED | 已评估 |
| BIDDING | 投标中 |
| BID_SUBMITTED | 已投标 |
| WIN | 已中标 |
| LOST | 未中标 |
| ABANDONED | 已放弃 |

### 5.2 评估相关枚举

- **evaluationStatus**：`DRAFT` / `SUBMITTED`
- **bidRecommendation**：`RECOMMEND` / `NOT_RECOMMEND`
- **reviewStatus**：`PENDING` / `APPROVED` / `REJECTED`
- **bidResult**（回调）：`WON` / `LOST` / `FAILED` / `ABANDONED`

## 6. 出站回调

### 6.1 标讯状态变更回调

- **触发**：标讯提交投标（PENDING → BIDDING）或弃标（→ ABANDONED）
- **方向**：本平台 → 外部系统
- **认证**：JWT Bearer Token
- **请求体关键字段**：`event` / `tenderId` / `sourceId` / `oldStatus` / `newStatus` / `operatorId` / `operatorName` / `recommendation`
- **v3.5 变更**：`externalId` → `sourceId`，与标讯推送接口保持一致

### 6.2 项目结果确认回调

- **触发**：用户在结果确认页提交并持久化成功后，**异步**回调（失败不影响主流程）
- **方向**：本平台 → CRM
- **认证**：JWT Bearer Token
- **请求体关键字段**：`tenderId` / `projectId` / `sourceId` / `bidResult` / `evidenceFiles` / `competitors` / `operatorName` / `operatorEmployeeId` / `operatedAt`
- **competitors**：仅 `WON` / `LOST` 时有值，`FAILED` / `ABANDONED` 时为空数组
- **v3.5 变更**：`crmOpportunityId` → `sourceId`，与标讯推送接口保持一致

## 7. 限流

- 默认 200 次/分钟/API Key
- 超出返回 HTTP 429
- 更高配额联系本平台技术团队

## 8. 对接流程

```
外部系统（CRM/OA/标讯平台）
  ├─ 1. 申请 API Key（tender:read / tender:write）
  ├─ 2. POST /api/integration/tenders/push（幂等创建）
  ├─ 3. GET /api/integration/tenders（列表查询）
  ├─ 4. GET /api/integration/tenders/{sourceSystem}/{sourceId}（详情）
  ├─ 5. PUT /api/integration/tenders/{sourceSystem}/{sourceId}（更新）
  ├─ 6. 接收状态回调（本平台 → 外部系统）
  └─ 7. 接收结果确认回调（本平台 → CRM）
```

## 9. 相关文档与迁移指南

- **CRM 旧接口迁移**：`docs/integration/crm-migration-guide.md`（指导 CRM 从旧接口 `/api/external/tenders` 迁移到新接口 `/api/integration/tenders/push`）
- **CO-262 对接契约**：`docs/integration/CO-262-crm-chance-fields-spec.md`（CRM 商机关联-投标评估表字段映射）
- **OpenAPI 总入口**：[[api-openapi]] — Swagger UI / OpenAPI JSON 规范
- **CRM 查询接口**（投标系统 → CRM 查客户信息）：[[integration-oa-crm]]
- **架构层 API 集成**：[[architecture]] §5 API 集成层

## 10. 版本演进要点

| 版本 | 日期 | 关键变更 |
|------|------|---------|
| v3.1 | 2026-06-16 | contactInfo 数组化；新增 11 个基本信息字段；修改接口支持 tenderId 定位；新增 evaluation 嵌套对象 |
| v3.2 | 2026-06-17 | 时间分隔符 T → 空格；详情接口新增 attachments；新增第四章「回调」；附件上限 10 个 |
| v3.5 | 2026-06-17 | 状态回调 externalId → sourceId，认证改 JWT；结果回调 crmOpportunityId → sourceId；列表 contactInfo 改数组 |
| v3.7 | 2026-06-17 | 标讯创建接口新增 tenderInfo / projectManagerName / department / creatorName / createDate |
| v3.8 | 2026-06-17 | 当前版本（文档版本号同步） |

## 11. 待确认事项

| # | 事项 | 状态 |
|---|------|------|
| 1 | 测试环境 URL | 待对接时提供 |
| 2 | 生产环境 URL | 待上线时提供 |
| 3 | API Key 分配流程 | 由本平台管理员分配 |
| 4 | 更高限流配额 | 联系本平台技术团队 |
