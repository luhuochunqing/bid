# Demo 融合模块（e2e/H2）

> 一旦我所属的文件夹有所变化，请更新我。

## 职责
`demo` 模块负责 e2e 环境下的 API Demo 融合能力：在不改前端路由和核心响应结构的前提下，把“真实数据 + 内存 Demo 数据”在后端统一拼接输出。

## 启用条件
- 仅当 `SPRING_PROFILES_ACTIVE` 包含 `e2e` 时启用。
- 非 `e2e` 环境不注入 Demo，不影响真实链路。

## 关键约束
- Demo 记录统一使用负数 ID，避免与真实数据库主键冲突。
- Demo 数据只读：对 Demo ID 的写操作必须返回受控失败，不做隐式落库。
- API 契约稳定优先：不新增前端调用主路由，不破坏既有字段语义。

## 边界清单
| 文件 | 地位 | 功能 |
|------|------|------|
| `service/DemoModeService.java` | Service | 判断当前是否处于 e2e Demo 融合模式 |
| `service/DemoDataProvider.java` | Provider | 提供按领域划分的内存 Demo DTO 数据 |
| `service/DemoFusionService.java` | Service | 封装合并、去重、排序、分页等通用拼接策略 |

## 维护约定（v1）
- 新增字段时同步更新对应测试：`DemoDataProviderTest`、`DemoFusionServiceTest` 及相关业务测试。
- 允许后续把 Demo 来源迁移到专用 provider，但保持 API 契约和 ID 规则不变。
