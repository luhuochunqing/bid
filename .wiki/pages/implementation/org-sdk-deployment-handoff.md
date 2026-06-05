---
title: 组织架构 SDK 集成 — 部署 Handoff
space: implementation
category: guide
tags: [handoff, 部署, 组织架构, integration, org-sync]
created: 2026-05-28
updated: 2026-05-28
health_checked: 2026-06-05
sources:
  - specs/005-org-sdk-integration/plan.md
---
# 组织架构 SDK 集成 — 部署 Handoff

> 适用对象：执行部署的 Agent / 运维人员
> PR #461 已合入 main，代码已就绪

## 一句话

组织架构事件同步的代码已完成并合入 main。下次部署时只需在服务器上加 5 行环境变量并重启，即可自动运行。

## 部署前确认

1. **PR #461 已在 main 上**（已合并，构建验证通过）
2. **ClientSDK jar 已在仓库内**：`backend/libs/ClientSDK-release_0.0.2.jar`（无需外网私服）
3. **consumerGroup 已硬编码为 `bms`**（西域指定值）

## 服务器操作（唯一额外步骤）

编辑 `/etc/xiyu-bid/backend.env`，追加以下 5 行：

```
XIYU_ORG_SYNC_ENABLED=true
XIYU_ORG_EVENT_SDK_ENABLED=true
XIYU_ORG_EVENT_SERVER_REGISTER_URL=http://event-busserver-test.ehsy.com
XIYU_ORG_EVENT_SERVICE_NAME=BidSystemOrgConsumer
XIYU_ORG_DIRECTORY_BASE_URL=https://base-oss-test.ehsy.com
```

重启服务：

```bash
sudo systemctl restart xiyu-bid-backend
```

## 验证方式

部署后检查健康状态和日志：

```bash
# 健康检查
curl -fsS http://127.0.0.1:18080/actuator/health

# 确认 SDK consumer adapter 已加载
sudo journalctl -u xiyu-bid-backend --since "1 min ago" --no-pager | grep -i "AcceptEvent\|SDK\|org.*sync"
```

## 需要注意

- **不需要 username/password**：YAPI 接口基于内网 IP 白名单安全，无 Bearer Token
- **不需要 clientId/clientSecret**：`OrganizationTokenService` 已删除
- 事件是**全自动的**：服务启动 → SDK 注册到事件总线 → 西域变更推过来 → 自动回查 YAPI → upsert 本地库

## 网络要求（服务器需能访问）

| 目标 | 用途 |
|---|---|
| `event-busserver-test.ehsy.com:80` | SDK 注册 + 接收事件推送 |
| `base-oss-test.ehsy.com:443` | YAPI 组织架构接口回查 |

## 如果部署后遇到问题

1. 确认 `/etc/xiyu-bid/backend.env` 中 5 个变量存在且值正确
2. 确认 `systemctl status xiyu-bid-backend` 显示 active
3. 确认日志中有 `@AcceptEvent` 相关输出
4. 检查服务器到 `event-busserver-test.ehsy.com` 和 `base-oss-test.ehsy.com` 的网络连通性

## 相关文档

- 部署手册：`docs/release/LIVE_SERVER_DEPLOYMENT_RUNBOOK.md`（已补充 §6 期望环境变量清单）
- 集成方案：`.wiki/pages/integration-organization-event-sdk.md`
- 待确认项：`.wiki/pages/implementation/xiyu-pending-confirmations.md`
