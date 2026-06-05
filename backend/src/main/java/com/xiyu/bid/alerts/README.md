# Alerts 模块

> 一旦我所属的文件夹有所变化，请更新我。

## 职责说明
告警模块负责规则配置、历史记录和自有规则执行，支撑项目关键事件的提醒与追踪。
规则与历史分层管理，`alerts` 只保留自己拥有的数据规则执行，不再承担跨模块扫描编排。
历史记录在未解决态下按 `ruleId + relatedId` 去重，避免同一对象重复刷提醒。
对外提供规则维护、历史查询和统计接口。
告警历史的列表、详情、未处理列表、确认和统计接口仅允许 ADMIN/MANAGER 访问；STAFF 不再具备告警历史读取或确认权限。创建与 resolve 继续维持既有 ADMIN/MANAGER 限制。

## 边界清单

| 文件 | 地位 | 功能 |
|------|------|------|
| `AlertRule.java` | Entity | 告警规则实体 |
| `AlertHistory.java` | Entity | 告警历史记录实体 |
| `AlertRuleRepository.java` | Repository | 告警规则数据访问边界 |
| `AlertHistoryRepository.java` | Repository | 告警历史数据访问边界 |
| `AlertRuleService.java` | Service | 告警规则业务逻辑 |
| `AlertHistoryService.java` | Service | 告警历史业务逻辑 |
| `AlertRuleExecutionService.java` | Service | `alerts` 自有规则执行边界 |
| `AlertRuleController.java` | Controller | 告警规则 API 边界 |
| `AlertHistoryController.java` | Controller | 告警历史 API 边界，历史读取/确认/统计仅限管理员和经理 |
| `AlertRuleCreateRequest.java` | DTO | 创建告警规则请求 |
| `AlertRuleUpdateRequest.java` | DTO | 更新告警规则请求 |
| `AlertHistoryCreateRequest.java` | DTO | 创建告警历史请求 |
| `AlertStatisticsResponse.java` | DTO | 告警统计响应 |

## 依赖约束

- `alerts` 允许依赖自身仓储、项目/标讯等稳定查询边界与历史写入能力。
- `alerts` 不再直接依赖 `businessqualification`、`resources` 的扫描应用服务。
- 跨模块提醒统一由 `alertdispatch` 包编排。
