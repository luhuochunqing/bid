# tenderkeyword 模块

> 一旦我所属的文件夹有所变化，请更新我。

## 职责说明
标讯关键词订阅：用户可设置关键词组合（AND/OR 关系），系统每日自动匹配新增标讯，
匹配结果通过站内通知推送给用户。

## 边界清单

| 文件 | 地位 | 功能 |
|------|------|------|
| `domain/KeywordMatchPolicy.java` | Pure Core | 关键词匹配逻辑（AND/OR） |
| `domain/TenderKeywordSubscriptionPolicy.java` | Pure Core | 订阅校验 |
| `entity/TenderKeywordSubscription.java` | Entity | 订阅实体 |
| `entity/TenderKeywordSubscriptionKeyword.java` | Entity | 关键词实体 |
| `entity/TenderKeywordMatchLog.java` | Entity | 匹配日志实体 |
| `repository/TenderKeywordSubscriptionRepository.java` | Repository | 订阅数据访问 |
| `repository/TenderKeywordSubscriptionKeywordRepository.java` | Repository | 关键词数据访问 |
| `repository/TenderKeywordMatchLogRepository.java` | Repository | 匹配日志数据访问 |
| `dto/CreateSubscriptionRequest.java` | DTO | 创建请求 record |
| `dto/UpdateSubscriptionRequest.java` | DTO | 更新请求 record |
| `dto/SubscriptionDTO.java` | DTO | 订阅摘要 record |
| `dto/MatchResultDTO.java` | DTO | 匹配结果 record |
| `service/TenderKeywordSubscriptionService.java` | Service | 订阅编排 & 匹配执行 |
| `controller/TenderKeywordSubscriptionController.java` | Controller | REST 端点 |
| `job/TenderKeywordMatchJob.java` | Job | 每日定时匹配 |

## API 端点
- `POST /api/tender-keyword-subscriptions` — 创建订阅
- `GET /api/tender-keyword-subscriptions` — 我的订阅列表（分页）
- `GET /api/tender-keyword-subscriptions/{id}` — 订阅详情
- `PUT /api/tender-keyword-subscriptions/{id}` — 更新订阅
- `DELETE /api/tender-keyword-subscriptions/{id}` — 删除订阅
- `PATCH /api/tender-keyword-subscriptions/{id}/toggle` — 暂停/启用
- `GET /api/tender-keyword-subscriptions/match-results` — 我的匹配结果（分页）
- `GET /api/tender-keyword-subscriptions/{id}/match-results` — 订阅的匹配结果（分页）

## 架构边界
- `domain/*` 满足 FP-Java Profile：`record` + `static` 方法，返回不可变值
- Job 负责定时任务编排，Service 负责业务编排，Policy 仅做纯逻辑计算
- 通知通过 `NotificationApplicationService` 站内信推送
