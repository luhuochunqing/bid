---
title: 日志系统查 Bug 手册
space: engineering
category: guide
tags: [日志, 可观测性, 排查, traceId, MDC]
sources:
  - docs/operations/logging-bug-investigation-guide.md
backlinks:
  - _index
created: 2026-06-27
updated: 2026-06-27
health_checked: 2026-06-27
---
# 日志系统查 Bug 手册

> 本手册说明如何使用「西域数智化投标管理平台」的后端日志系统定位线上/线下问题。
> 配套实现见 PR [!1240](https://gitee.com/allinai888/bid/pulls/1240)。

## 核心设计

每个 HTTP 请求都有唯一的 `traceId`，后端通过 `TraceFilter` 把以下字段写入 SLF4J MDC：

| 字段 | MDC key | 来源 | 未认证时 |
|---|---|---|---|
| 链路 ID | `traceId` | 请求头 `X-Trace-Id`，无则生成 UUID | 新生成 |
| 用户 ID | `userId` | `CurrentUserResolver` | `anonymous` |
| 角色 code | `roleCode` | `CurrentUserResolver` | `anonymous` |

`logback-spring.xml` 会把这些字段输出到每条日志，便于按 `traceId` 或 `userId` 检索完整链路。

## 日志输出示例

### 文本格式（开发环境）

```text
2026-06-27 21:11:36.281 [http-nio-18089-exec-1] INFO  c.x.b.aspect.OperationLogAspect
  [trace=a1b2c3, user=42, role=admin] op_start class=ProjectDraftingController method=submitBid args=[{"projectId":123,"password":"***"}]
```

### JSON 格式（生产环境）

```json
{
  "timestamp": "2026-06-27T21:11:36.281+08:00",
  "level": "INFO",
  "logger": "com.xiyu.bid.aspect.OperationLogAspect",
  "message": "op_start class=ProjectDraftingController method=submitBid args=[...]",
  "traceId": "a1b2c3",
  "userId": "42",
  "roleCode": "admin"
}
```

## 常用排查场景

### 场景 1：用户报某个操作失败

1. 从前端响应头或错误上报平台拿到 `X-Trace-Id`。
2. 按 `traceId` 查后端日志：

   ```bash
   grep 'traceId=a1b2c3' logs/app.log
   # JSON 日志
   cat logs/app.log | jq 'select(.traceId=="a1b2c3")'
   ```

3. 查看日志时间线，定位 `op_error` 或异常堆栈。

### 场景 2：不知道 traceId，只知道用户和时间

```bash
# 文本日志
 grep 'userId=42' logs/app.log | grep '2026-06-27T10:'

# JSON 日志
 cat logs/app.log | jq 'select(.userId=="42" and .timestamp >= "2026-06-27T10:00:00")'
```

找到报错那条日志后，再用其 `traceId` 细查。

### 场景 3：接口慢 / 偶发卡顿

`op_end` 日志自带耗时：

```text
op_end class=BidDocService method=generatePdf elapsed=3245ms result="..."
```

排查步骤：

1. 找到慢请求的 `traceId`。
2. 把同一 `traceId` 下的所有 `op_end` 按时间排序。
3. 看哪个方法耗时占比最高。
4. 如果关键方法没有 `@LogOperation`，临时加上再复现。

### 场景 4：权限/角色相关问题

日志里直接带了 `roleCode`，可用于核对当时用户的实际角色：

```text
userId=42 roleCode=project_member
```

如果业务权限判断和实际角色不一致，先确认 MDC 里的角色是否预期。

### 场景 5：直接搜所有报错

```bash
grep 'op_error' logs/app.log | tail -20
```

或按异常类型：

```bash
grep 'exception=NullPointerException' logs/app.log
```

## 命令速查表

| 目的 | 命令 |
|---|---|
| 按 traceId 查 | `grep 'traceId=xxx' logs/app.log` |
| 按用户查 | `grep 'userId=42' logs/app.log` |
| 按角色查 | `grep 'roleCode=admin' logs/app.log` |
| 查所有报错 | `grep 'op_error' logs/app.log` |
| 查慢请求 | `grep 'op_end' logs/app.log \| grep -E 'elapsed=[0-9]{4,}ms'` |
| 按接口路径查 | `grep 'POST /api/projects' logs/app.log` |
| JSON 按 traceId 查 | `jq 'select(.traceId=="xxx")' logs/app.log` |

## 扩展：把业务实体 ID 也放进 MDC

当前已实现 `userId`/`roleCode`。如果业务需要把 `projectId`、`bidId` 等也挂到链路日志，可在业务入口手动写入：

```java
MDC.put("projectId", String.valueOf(projectId));
try {
    // 业务逻辑
} finally {
    MDC.remove("projectId");
}
```

然后同步更新 `logback-spring.xml` 输出该字段。

## 维护声明

- 本手册随日志系统实现一起维护。
- 新增 MDC 字段或调整操作日志格式时，请同步更新本文档。
