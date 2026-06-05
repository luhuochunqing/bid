# subscription 模块

> 一旦我所属的文件夹有所变化，请更新我。

## 职责说明
多态订阅：用户关注任意实体（PROJECT/DOCUMENT/QUALIFICATION/TENDER/TASK）；被订阅实体有变更时触发通知。

## 边界清单

| 文件 | 地位 | 功能 |
|------|------|------|
| `SubscriptionPolicy.java` | Core Policy | 订阅校验纯核心 |
| `Subscription.java` | Entity | 订阅实体 |
| `SubscriptionRepository.java` | Repository | 订阅数据访问 |
| `SubscriptionRequest.java` | DTO | 订阅请求 record |
| `SubscriptionSummary.java` | DTO | 订阅摘要 record |
| `SubscriptionApplicationService.java` | Service | 订阅编排（幂等 subscribe + unsubscribe） |
| `SubscriptionController.java` | Controller | REST 端点 |

## API 端点
- `POST /api/subscriptions` — 订阅
- `DELETE /api/subscriptions` — 取消订阅
- `GET /api/subscriptions/me` — 我的订阅列表
- `GET /api/entities/{type}/{id}/subscription` — 查询订阅状态
