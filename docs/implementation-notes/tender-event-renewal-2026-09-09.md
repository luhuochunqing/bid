# 标讯事件注册续约联调记录（2026-09-09）

## 真实测试证据

- 测试环境创建标讯 `4170`、`4171` 后，投标系统分别产生失败流水 `1880`、`1881`。
- 应用通过客户 SDK `SystemInteractiveService.sendEvent` 发送，以及使用相同四字段结构直接请求事件总线，均得到 `result=false`。
- 手工调用 `/eventbus/register` 注册 `serviceName=bid` 后，再发送相同四字段请求得到 `result=true`。
- 实际拓扑是 Java 进程监听 `18080`，Nginx 对外监听 `8080` 并反向代理到 `127.0.0.1:18080`。事件库跨网段无法直达 Java 的 `18080`；宣告该端口时，`register`、`renew` 请求均约需 128 秒。改为事件库可达的对外代理端口 `8080` 后，两次请求分别约 66 ms、56 ms。该对照证明 advertised host/port 会直接影响注册续约可用性，不能再描述为“不影响事件路由”。
- 使用 `8080` 地址并按不超过 3 秒的节奏连续续约 24 秒后，`getActiveService` 显示 `bid` 的 `8080` 实例为 `UP/ACTIVE`；随后标准四字段 publish 返回 `result=true`。
- 在同一手工注册有效时间窗口内，通过真实 `POST /api/tenders` 创建标讯 `4172`；应用 `TenderEventSdkProducer` 返回 `result=true`，并写入流水 `1882`、状态 `SENT`。该事件的 `traceId=t8k9399gtu15a4mybv`、`spanId=sbbh1mkc5ch3vzc2tr` 均为 18 位，`parentId=0`。
- 本轮请求使用的 `traceId`、`spanId` 均不超过 18 位，排除了链路 ID 超长作为这两次失败的解释。
- `4172` 证明真实创建请求已走通“应用 → SDK → 网关受理”并形成成功流水；尚未取得同一 trace 的 CRM 消费日志，不能写成 CRM 已收到。

由上述对照可知，四字段发布结构本身能够被网关受理，真实应用创建链路也能在注册有效时获得 `result=true`；宣告跨网段不可达的 `18080` 后，服务在注册完成的短暂窗口也可能 publish 返回 `true`，但约 128 秒的续约阻塞无法维持 3 秒节奏，不能稳定保持在线。`4170`、`4171` 失败与 `bid` 服务在发送时未处于有效注册状态一致。完整根因是错误的 advertised 地址与错误的续约参数共同导致服务不能稳定在线，并非只调整续约单位即可。该结论还需要部署修复后，通过自动注册、持续续约、事件库入库、Kafka 发布和 CRM 消费完成最终闭环。

## 续约参数根因

客户给出的正式参数为：

| 参数 | 客户口径 | 旧实现 | 问题 |
|---|---:|---:|---|
| `initialDelay` | 3 秒 | 10 秒 | 首次续约过晚 |
| `period` | 3 秒 | 30 秒 | 续约频率低于正式配置 |
| `renewalDuration` | 3000 毫秒 | 60 | SDK 将数值原样写入请求；旧实现按“秒”理解，单位错误且有效期过短 |

SDK 调度器只把 `initialDelay` 和 `period` 按 `TimeUnit.SECONDS` 解释；`renewalDuration` 不参与本地时间换算，而是原样写入 `/eventbus/renew`。因此不能根据三个字段都是数字，就假设它们使用同一单位。

## 修复决策

- 继续使用客户要求的 `SystemInteractiveService.sendEvent` 四参数接口，不恢复自定义 HTTP Sender。
- 注册成功条件保持为响应 `code="00000"`，注册成功后启动 SDK 续约组件。
- 续约配置对齐客户正式值：`initialDelay=3`、`period=3`、`renewalDuration=3000`。
- 注册和续约使用可配置的 advertised host/port，避免继续向事件总线宣告跨网段不可达的 Java 监听端口 `18080`，改为宣告事件库可达、由 Nginx 反代至 Java 的对外代理端口 `8080`。标讯专用配置优先；未提供时回退组织事件 SDK 的 advertised host/port 配置。
- 常量名称显式携带单位，防止再次把毫秒字段按秒解释。
- trace/span 继续遵守总长度不超过 18 位的约束。

## 风险与部署后验证

- SDK 注册失败会无退避地循环请求；网关不可用或配置错误时可能形成高频请求与日志。
- SDK 续约方法不检查响应业务码，也没有成功响应日志；仅看到“续约线程启动”不能证明服务持续在线。
- 部署后必须确认 `/eventbus/register` 宣告的是事件库实际可达的 host 与 Nginx 对外代理端口 `8080`，返回 HTTP 200 且 `code=00000`；随后每约 3 秒出现一次 `/eventbus/renew`，请求中的 `renewalDuration=3000`，并通过 `getActiveService` 确认实例保持 `UP/ACTIVE`。
- 新建一个未使用过的人工标讯，确认应用响应不是 `result=false`，并使用同一 trace 核对事件库入库、Kafka 发布和 CRM 消费。只有完整链路均有证据时，才能判定功能最终打通。


## 部署后验证补充（2026-09-10）

PR !2601 合并并部署后，测试环境仍未自动形成有效注册。部署版本为 `452dae98b`，应用日志在
07:03:05.096 记录“开始服务注册”、07:03:05.478 记录“注册流程已触发”，而 Spring Boot
直到 07:03:05.965 才记录 `Started XiyuBidApplication`。同一秒的 Nginx access log 显示
EventBus 回调 `POST /api/task/startTask` 返回 502。注册请求虽然得到 `code=00000`，EventBus
实例却保持 `DOWN/WAIT`，后续 renew 也不能把这个无效实例激活。

应用完全就绪后手工调用一次相同 `/eventbus/register` 作为对照，回调不再是 502，实例立即变为
`UP/ACTIVE`。停止所有手工续约后继续观察 24 秒，SDK 自身 `lastRenewalTime` 仍约每 3 秒递增。
该实验分别证明：

- `http://172.16.38.78:8080` advertised 地址正确且 EventBus 可回连；
- `initialDelay=3`、`period=3`、`renewalDuration=3000` 已生效；
- 自动启动失败只剩注册时机：Bean `initMethod` 早于 Web 服务可接收同步回调。

最终决策：移除 `@Bean(initMethod = "register")`，由 `TenderEventRegistrar` 监听
`ApplicationReadyEvent` 并以最低优先级触发注册。部署后验收必须在不执行手工注册的前提下，
确认启动回调不再出现 502、实例持续 `UP/ACTIVE`、续约时间约每 3 秒递增，再创建新标讯核对
SDK `result`、本地流水和客户侧事件链路。
