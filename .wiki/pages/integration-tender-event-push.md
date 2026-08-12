---
title: 标讯创建事件推送 - 西域 CRM 事件总线
space: engineering
category: integration
tags: [integration, tender, event-bus, event-sdk, crm, push]
sources:
  - backend/src/main/java/com/xiyu/bid/integration/tenderevent/
  - backend/src/main/resources/db/migration-mysql/V1185__create_tender_event_logs.sql
  - backend/src/main/resources/application.yml
backlinks:
  - _index
created: 2026-08-12
updated: 2026-08-12
health_checked: 2026-08-12
---
# 标讯创建事件推送 - 西域 CRM 事件总线

## 结论

标讯创建后，平台向西域 CRM 事件总线推送一条"事件通知消息"，`data` 只放关键标识（`tenderId`/`externalId`），由 CRM 通过已有接口回查详情。**不是传输完整业务数据**。

## 触发时机

| 触发点 | 服务 | 说明 |
|---|---|---|
| 人工录入 / 批量导入 | `TenderCommandService.createTender` | 同步入口，异步推送 |
| 第三方平台创建 | `TenderIntegrationCommandService.createNewTender` | 同属外部 API Key 鉴权链 |

**排除来源**：`SourceType.CRM_OPPORTUNITY` 不推送（避免 CRM → 平台 → CRM 回发循环）。

**不覆盖**：存量补推、更新事件。仅创建时推送。

## 传输方式：直连 /eventbus/publishEvent

> **关键教训**：`TenderEventSdkProducer` 的 `sendEvent` 方法把 `data` 硬编码为 `EventTrackReq`，无法自定义。因此**绕过 SDK，直连 `POST {baseUrl}/eventbus/publishEvent`**，`data` 才能用自定义 `Map<String,Object>`。

### 请求结构

```json
{
  "serviceName": "bid",
  "eventTopic": "BidTenderChange",
  "eventSource": "bid",
  "data": { "tenderId": 123, "externalId": "ext-1" }
}
```

- `data` 只含 `tenderId`（必填）和 `externalId`（有值才序列化）
- 链路追踪 `traceId` / `spanId` / `parentId` 进 HTTP Header，不进 body

### 事件编码（`TenderEventCode`）

| 编码 | source | topic |
|---|---|---|
| `BID_TENDER_CHANGE` | `bid` | `BidTenderChange` |

## 流水表

`tender_event_logs`（V1185）记录每条事件：`tender_id`、`event_code`、`event_source`、`event_topic`、`trace_id`/`span_id`/`parent_id`、`event_content`（data JSON）、`status`（SENT/FAILED）、`error_message`、`created_at`。

## 配置

```yaml
xiyu:
  integrations:
    tender-event:
      sdk:
        enabled: ${XIYU_TENDER_EVENT_SDK_ENABLED:false}
        server-register-url: ${XIYU_TENDER_EVENT_SERVER_REGISTER_URL:}
        service-name: ${XIYU_TENDER_EVENT_SERVICE_NAME:bid}
```

> 默认 `disabled`（安全默认），生产环境用 `XIYU_TENDER_EVENT_SDK_ENABLED=true` 启用。

## 模块结构

```text
backend/src/main/java/com/xiyu/bid/integration/tenderevent/
├── domain/          # 纯核心：推送策略、事件编码/状态/消息体
├── application/     # 编排：端口(发布/流水)、payload mapper、发布服务
└── infrastructure/
    ├── persistence/ # 事件流水表实体/仓库
    └── sdk/         # 直连 HTTP 生产者 + 配置
```

## 关键设计

- **不阻塞主链路**：`TenderEventPublishService` 用独立线程池 `tenderEventExecutor` 异步发送。
- **开关优先**：`enabled=false` 时不推送、不产生流水。
- **策略前置**：`TenderEventPolicy.shouldPublish(sourceType)` 决定是否推送。
- **失败不阻断创建**：推送异常仅记录 `FAILED` 流水 + 告警日志，不影响标讯创建结果。