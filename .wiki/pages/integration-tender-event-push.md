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
updated: 2026-08-25
health_checked: 2026-08-25
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
  "data": "{\"tenderId\":123,\"externalId\":\"ext-1\"}"
}
```

- `data` 是 **JSON 字符串**（网关 `EventContentReqDto.data` 声明为 String），由 `TenderEventHttpSender.serializeData` 把业务载荷 Map 序列化后上传；只含 `tenderId`（必填）和 `externalId`（有值才入 Map）
- 链路追踪 `traceId` / `spanId` / `parentId` 进 HTTP Header，不进 body
- ⚠️ **契约坑**：若 `data` 传原始对象而非 JSON 字符串，网关返回 `400 Bad Request`（2026-08-25 实战踩坑）

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
    └── sdk/         # 直连 HTTP 生产者 + 配置（TenderEventHttpSender 持有 wire 契约）
```

## Wire 契约单一源（2026-08-24 去重）

`TenderEventHttpSender`（`infrastructure/sdk/`）是事件总线 wire 契约的**唯一持有者**：body 组装
（`serviceName/eventTopic/eventSource/data`）、响应 `data.result` 解析、`traceId/spanId/parentId` HTTP Header、
RestClient 3s/5s 超时。`TenderEventSdkProducer` 与 `OpportunityPredictionEventPublisher` 都委托它发送，
各自只保留自己的载荷与上下文日志（公开构造/入口不变）。

> 新的事件发布方直接复用 `TenderEventHttpSender`，**不要**再复制 body/trace/超时逻辑（本页此前的重复即此类 slop）。
> 标讯事件应用层管线（`TenderEventPublishService`/Port/流水表）不感知该重构。

## 关键设计

- **不阻塞主链路**：`TenderEventPublishService` 用独立线程池 `tenderEventExecutor` 异步发送。
- **开关优先**：`enabled=false` 时不推送、不产生流水。
- **策略前置**：`TenderEventPolicy.shouldPublish(sourceType)` 决定是否推送。
- **失败不阻断创建**：推送异常仅记录 `FAILED` 流水 + 告警日志，不影响标讯创建结果。

## 根因与契约修复（2026-08-25 部署实战）

### 1. YAML 缩进错位 → 配置绑定失败（无报错，最难排查）

- **现象**：测试环境启用 `XIYU_TENDER_EVENT_SDK_ENABLED=true` 后仍不推送。
- **根因**：`application.yml` 中 `tender-event:` 块因 **6 空格缩进**被错误嵌套进 `organization:` 子树，Spring 无法绑定 `xiyu.integrations.tender-event.sdk.enabled`，开关读到 `null`→默认 `false`。
- **定位手段**：Arthas 探针在启动期读取实际绑定的 `enabled` 值，确认 `null`，再对照 YAML 树形结构发现缩进错位。
- **修复**：`tender-event:` 必须与 `organization:` 同级（**4 空格缩进**），作为 `integrations:` 的直接子节点。
- **教训**：YAML 缩进错位**不报错、静默回退默认值**。配置类属性绑定失败时，先用探针/日志确认 Spring 实际读到的值，再反查 yml 层级。

### 2. data 契约：必须是 JSON 字符串（见「请求结构」）

- 万不可传 Map/对象，否则网关 `400`。

### 3. 网关成功信号：外层 `code=="00000"`

- **现象**：网关已返回 `{"code":"00000","data":{"result":null},"msg":"响应成功"}`，但发送器误判失败（流水标 `FAILED`）。
- **修复**：`TenderEventHttpSender.isSuccessResponse` 判定 `code=="00000"` **或**（兼容历史约定）`data.result==true` 为成功。
- **教训**：外部网关的"成功"以**最外层状态码**为准，勿默认业务 payload 里的 `result`。