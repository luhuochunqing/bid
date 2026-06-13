# Quickstart: 西域事件库 SDK 订阅与 CRM 出向客户端

**Last Updated**: 2026-05-16

## Prerequisites

- Java 21 + Maven 3.9+
- MySQL 8.0 本地实例
- Redis 7 本地实例
- 西域 ClientSDK jar (customer delivery, pending — see §0 below)

## §0: Customer Deliverables (Blocking)

| # | Item | Status | Owner |
|---|------|--------|-------|
| 0.1 | ClientSDK jar (com.ehsy.eventlibrary:ClientSDK) | Pending | 西域/客户 |
| 0.2 | SDK appId + appSecret | Pending | 西域/客户 |
| 0.3 | CRM clientId + clientSecret | Pending | 西域/客户 |
| 0.4 | SDK 集成文档（consumerGroup、重连策略） | Pending | 西域/客户 |
| 0.5 | CRM API 文档（base URL、接口路径确认） | Pending | 西域/客户 |

## Environment Variables

```bash
# 组织架构事件订阅
export XIYU_ORG_EVENT_SDK_APP_ID="<from customer>"
export XIYU_ORG_EVENT_SDK_APP_SECRET="<from customer>"
export XIYU_ORG_EVENT_SDK_CONSUMER_GROUP="xiyu-bid-${WORKTREE:-main}"

# CRM 出向客户端
export XIYU_CRM_CLIENT_ID="<from customer>"
export XIYU_CRM_CLIENT_SECRET="<from customer>"
export XIYU_CRM_BASE_URL="https://crm.xiyu.example.com/api"
```

## Development Without SDK

SDK jar 未交付时，系统自动退化为 HTTP-only 模式：
- `POST /api/xiyu/org-events/fallback` 接收事件
- SDK 相关 bean 不加载（`@ConditionalOnClass` 条件不满足）

## Running Locally

```bash
# 1. Start dependencies
cd /Users/user/xiyu/xiyu-bid-poc
export XIYU_DEV_CONFIRMED=1
./scripts/dev-services.sh start

# 2. Set env vars (see above) + start backend
cd backend
XIYU_DEV_CONFIRMED=1 ./start.sh

# 3. Verify
curl http://127.0.0.1:18080/actuator/health
curl http://127.0.0.1:18080/actuator/metrics/xiyu.org.event.sync.latency
```

## Key Endpoints (Local)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/xiyu/org-events/fallback` | POST | HTTP 灾备事件接收 |
| `/api/xiyu/org-events/init` | POST | 触发全量初始化 |
| `/api/xiyu/org-events/reconcile` | POST | 触发日常对账 |
| `/api/xiyu/crm/customers?keyword={kw}` | GET | 客户模糊查询 |
| `/api/xiyu/crm/customers/{id}/contacts` | GET | 客户负责人 |
| `/api/xiyu/crm/menus?systemType={t}` | GET | 菜单树 |
| `/api/xiyu/crm/employees/{token}` | GET | 员工信息 |
| `/api/xiyu/crm/messages` | POST | 发送消息 |

## Testing

```bash
# Unit + integration tests
cd backend
mvn test -Dtest="*OrganizationEvent*,*Crm*"

# Architecture tests (must stay green)
mvn test -Dtest=ArchitectureTest

# Full test suite
mvn test
```
