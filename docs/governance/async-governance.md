# P1 异步治理约定

## 目标
统一西域数智化投标管理平台所有异步链路的失败分类、默认动作、持久化重试/死信、可观测性与最小测试门禁，避免出现“代码里做了判断，但运行时只是打日志吞掉”的伪治理。

## 适用范围
- `@TransactionalEventListener(AFTER_COMMIT)` 派生任务
- `@Scheduled` worker / replay worker
- 外部系统回调、Webhook、通知发送、组织架构同步、文档转换等副作用链路

## 核心分层
1. `AsyncFailureClassifier`：把异常/失败结果归类到统一 failure kind。
2. `AsyncDecisionResolver`：把 failure kind 解析成 `DROP / SUCCEED_WITH_LOG / RETRY / DEAD_LETTER / FAIL_MAIN_TRANSACTION`。
3. 持久化载体：可重试链路必须有 task / inbox / dlq 中至少一种稳定载体。
4. `AsyncObservabilityRecorder`：所有最终动作都必须打到 `xiyu_async_total`。

## failure → action 对照表
| Failure kind | 含义 | 默认动作 | alertRequired | 备注 |
| --- | --- | --- | --- | --- |
| `BUSINESS_REJECT` | 业务明确拒绝且重试无意义 | `DROP` | 否 | 必须保留 reasonCode |
| `IDEMPOTENT_DUPLICATE` | 幂等重复消息 | `SUCCEED_WITH_LOG` | 否 | 记成功语义，不进入重试 |
| `SIDE_EFFECT_OPTIONAL` | 可选副作用失败，不影响主流程 | `DROP` | 否 | 如提醒类可降级链路 |
| `TRANSIENT_DEPENDENCY` | 瞬时依赖失败，如 timeout/429/短暂 5xx | `RETRY`，耗尽后 `DEAD_LETTER` | 耗尽后是 | 必须持久化重试 |
| `PERSISTENT_DEPENDENCY` | 持续外部依赖故障，如配置错误/长期不可达 | `DEAD_LETTER` | 是 | 不应盲重试 |
| `CONTRACT_INVALID` | 请求/响应契约错误、模板错误、参数错误 | `DEAD_LETTER` | 是 | 需要人工修复 |
| `DATA_CORRUPTION` | 数据损坏、关键字段不一致 | `DEAD_LETTER` | 是 | 高优先级告警 |
| `BUG` | 代码缺陷、未预期异常 | `DEAD_LETTER` | 是 | 必须尽快修复 |
| `MAIN_TRANSACTION_REQUIRED` | 主事务必须感知该失败 | `FAIL_MAIN_TRANSACTION` | 是 | 不允许 after-commit 吞掉；适用：支付回调签名验证失败、关键业务校验不通过必须中止主流程等 |

## reasonCode 约定
- reasonCode 必须稳定、可聚合、偏语义，不要直接写原始异常文本。
- 推荐优先使用统一 reasonCode：
  - `TRANSIENT_DEPENDENCY`
  - `TRANSIENT_DEPENDENCY_EXHAUSTED`
  - `PERSISTENT_DEPENDENCY`
  - `CONTRACT_INVALID`
  - `DATA_CORRUPTION`
  - `BUG`
  - `SIDE_EFFECT_OPTIONAL`
  - `IDEMPOTENT_DUPLICATE`
- 业务链路可以追加域内 reasonCode，但不能破坏统一顶层语义，例如：
  - organization: `DIRECTORY_GATEWAY_RETRYABLE`
  - notification: `CONTRACT_INVALID`
  - webhook: `TRANSIENT_DEPENDENCY_EXHAUSTED`

## observability 约定
统一指标：`xiyu_async_total`

标签：
- `result`: `success | retry | dead_letter | drop | drop_alert`
- `async_type`: 如 `notification` / `organization` / `webhook`
- `event_type`: 事件名或任务名
- `reason_code`: 稳定 reasonCode；成功写 `NONE`

规则：
- 首次失败进入待重试：记 `retry`
- 重试耗尽进入死信：记 `dead_letter`
- 可忽略丢弃：记 `drop`
- 必须人工关注的丢弃：记 `drop_alert`
- 成功完成：记 `success`
- 不允许把所有非成功都粗暴记成 `dead_letter`

## 最小告警闭环
以下组合必须接告警：
- `result=dead_letter`
- `result=drop_alert`
- `reason_code=BUG`
- `reason_code=DATA_CORRUPTION`
- `reason_code=TRANSIENT_DEPENDENCY_EXHAUSTED`
- `reason_code=CONTRACT_INVALID`
- `reason_code=PERSISTENT_DEPENDENCY`

建议阈值：
- 任意 5 分钟窗口内出现 `dead_letter > 0` 即告警
- 同一 `(async_type,event_type,reason_code)` 10 分钟内 `retry >= 5` 告警
- `BUG` / `DATA_CORRUPTION` 单次即告警

## 新异步���路接入 checklist
新增链路前必须同时满足：
1. 有 `AsyncFailureClassifier`
2. 有 `AsyncDecisionResolver` 决策接入
3. 有稳定持久化载体或明确定义为何不需要重试
4. 有 `AsyncObservabilityRecorder` 打点
5. 有至少一组测试覆盖：
   - 瞬时失败 -> `RETRY`
   - 不可恢复失败 -> `DEAD_LETTER`
   - 可忽略失败 -> `DROP` 或 `SUCCEED_WITH_LOG`

## 当前已接入链路
- webhook：任务表 + DLQ + worker + observability
- notification：任务表 + DLQ + worker + observability
- organization：inbox/retry/replay 状态机 + 统一 decision + observability

## 本轮明确不做
- 不强行抽象成一个超级通用 task 框架
- 不统一所有业务表结构
- 不把所有旧链路一次性重写
