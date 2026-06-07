# mention 模块

> 一旦我所属的文件夹有所变化，请更新我。

## 职责说明
@ 提及功能：解析富文本中的 `@[name](id)` token，写入 `mention` 表，并通过 `NotificationApplicationService` 触发 `MENTION` 类型通知。本模块不建立与通知并行的派发链路，只承担解析、过滤、审计持久化三件事。

## 边界清单

| 文件 | 地位 | 功能 |
|------|------|------|
| `core/MentionParsingPolicy.java` | Core Policy | 解析 `@[name](id)` token，纯函数，不依赖 Spring/IO |
| `entity/Mention.java` | Entity | 提及审计记录，映射 `mention` 表（V93） |
| `repository/MentionRepository.java` | Repository | 按被提及人、来源实体查询的数据访问接口 |
| `dto/UserSearchResult.java` | DTO | 用户搜索结果 record（仅 id/name/role，不泄漏敏感字段） |
| `dto/CreateMentionRequest.java` | DTO | 提交含 `@` 的内容请求 |
| `service/UserSearchService.java` | Service | 用户名/姓名前缀搜索（只读，安全上限 50） |
| `service/MentionApplicationService.java` | Service | 编排：解析 → 过滤自提及 → 调用通知派发 → 写入审计行 |
| `controller/UserSearchController.java` | Controller | `GET /api/users/search` 自动补全 |
| `controller/MentionController.java` | Controller | `POST /api/mentions` 提交提及 |

## API 端点
- `GET /api/users/search?q=&limit=` — 登录用户可访问，返回 `[{id, name, role}]`。`limit` 默认 10，上限 50；`q` 为空返回空数组，不查询数据库。
- `POST /api/mentions` — 登录用户可访问。请求体含 `content`（带 `@[name](id)` token）、`sourceEntityType`、`sourceEntityId`、可选 `title`。返回 `{success, data: {mentionCount, notificationId}}`。自提及自动忽略；解析上限 20。

## 架构约束
- `core/` 包遵循 FP-Java Profile：无 Spring、无 DB、无 IO、无日志、无 void 业务方法，使用 `record` + 静态工厂。
- `MentionApplicationService` 长度 <200 行、依赖数 <5，不含规则/数据访问/DTO 转换/状态写入 3 项以上。
- 通知派发只允许经由 `NotificationApplicationService.createNotification`，不建立并行路径。
