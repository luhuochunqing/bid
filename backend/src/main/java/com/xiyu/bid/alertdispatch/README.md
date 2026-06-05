一旦我所属的文件夹有所变化，请更新我。

# alertdispatch

跨模块告警编排层。

## 文件清单

| 文件 | 功能 |
|------|------|
| `service/AlertSchedulerService.java` | 读取启用规则并触发统一分发入口 |
| `service/AlertRuleDispatchService.java` | 按规则类型把执行请求分发到 alerts 核心或跨模块扫描器 |
| `service/BudgetAlertDispatchService.java` | 编排项目、标讯、费用与告警历史，生成预算告警 |

## 边界说明

- 本模块负责跨模块告警编排，承接不属于 `alerts` 核心自有规则的数据聚合与调度。
- `BUDGET`、`QUALIFICATION_EXPIRY`、`DEPOSIT_RETURN` 统一在这里编排。
- `alerts` 模块只保留规则配置、规则执行核心与历史记录能力，不再反向依赖业务模块。

## 依赖方向

- 本模块可以依赖 `alerts`、`resources`、`businessqualification` 等业务模块。
- `alerts` 模块不得反向依赖 `alertdispatch` 之外的业务扫描实现。
