# CRM Outbound Client 模块

> 一旦我所属的文件夹有所变化，请更新我。

## 职责
- 管理 CRM Token 生命周期（获取/缓存/单飞/续约/登出）。
- 封装 7 个出向业务接口（客户查询、负责人、菜单树、员工、消息发送）。
- 统一重试/超时/脱敏/监控行为。

## 边界清单
| 文件 | 地位 | 功能 |
|------|------|------|
| `config/CrmProperties.java` | Config | CRM 连接与策略配置参数 |
| `domain/CrmToken.java` | Domain | Token 值对象（含过期/续约判断） |
| `domain/CrmTokenCache.java` | Domain | 内存缓存 + 单飞锁机制 |
| `domain/CrmMessageRouter.java` | Domain | 消息批量拆分策略 |
| `application/CrmAuthService.java` | Service | Token 申请/续约/登出/401 清理 |
| `application/CrmCustomerService.java` | Service | 客户搜索 + 负责人批量查询 |
| `application/CrmMenuService.java` | Service | 菜单树查询 |
| `application/CrmEmployeeService.java` | Service | 员工信息查询 |
| `application/CrmMessageService.java` | Service | 单条/批量消息发送 |
| `infrastructure/CrmController.java` | Controller | CRM REST 入口 |
| `infrastructure/CrmHttpClient.java` | Client | 统一 HTTP 客户端（重试/超时） |
| `infrastructure/CrmResponseHandler.java` | Handler | CRM 响应解析（code-msg-data-success） |
