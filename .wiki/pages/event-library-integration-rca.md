---
title: 事件库两次集成反复失败根因复盘
space: engineering
category: lessons
tags: [integration, event-bus, event-sdk, tender, organization, root-cause]
created: 2026-09-10
updated: 2026-09-18
health_checked: 2026-10-25
archive: true
sources:
  - .wiki/sources/customer/事件库SDK接入说明方案.doc
  - .wiki/sources/technical/西域投标系统对接技术规格书.md
  - .wiki/pages/integration-tender-event-push.md
  - .wiki/pages/integration-organization-event-sdk.md
  - .wiki/pages/tender-event-pr2598-contract-review.md
  - docs/implementation-notes/tender-event-push-handoff-2026-08-27.md
  - docs/implementation-notes/tender-event-renewal-2026-09-09.md
  - docs/lessons/lessons-learned.md
  - docs/lessons/lessons-archive.md
backlinks:
  - _index
  - integration-organization-event-sdk
  - integration-tender-event-push
---
# 事件库两次集成反复失败根因复盘

## 结论

标讯事件难点不在 `sendEvent` 四个参数，而在团队把一个**带控制面的、有状态分布式协议**当成了普通无状态 HTTP 接口。真正的发送条件同时包括：SDK 装配、服务注册、反向回调、租约续约、可达地址、topic 路由/订阅、消息序列化和最终消费。早期每轮只修其中一个表象，又用该层的成功替代端到端成功，导致错误假设连续进入 PR、Wiki 和下一轮实现。

责任判断：

- **主因是我方理解和验证方法**：客户原始 SDK 文档第三步明确要求配置注册/续约，第四步才是 `SystemInteractiveService.sendEvent`。首版却基于未经字节码验证的“SDK 无法发送业务 data”假设主动绕开高层 SDK，并长期把注册理解成消费方专属能力。
- **客户文档和 SDK 是重要诱因**：文档没有区分公共事件模型、SDK Java API 与真实 HTTP wire；没写反向回调、advertised address、`UP/ACTIVE` 验收、失败码和 `result=null` 语义。SDK 还存在全局配置绑定、错误吞没、无退避重试、隐藏线程/回调等行为，使错误配置表现为“HTTP 成功但消息没有”。
- **共同放大器是观测权分裂**：我方只能看到应用和网关响应，事件库/Kafka/CRM 日志主要在客户侧；没有用同一 traceId 建立双方共享的最小成功基准，导致双方围绕请求字段反复猜测。

## 证据规模与时间线

- 可见代码落地周期：2026-08-12 至 2026-09-10，共 29 天；若计入前期需求沟通则更长。
- Gitee 记录中匹配到 17 个代码/集成相关 PR（16 个合并、`!2598` 关闭），另有 2 个部署报告 PR。
- `integration/tenderevent` 生产和测试目录累计被 32 个提交触碰。

| 阶段 | PR/证据 | 当时处理 | 为什么没有闭环 |
|---|---|---|---|
| 初版 | `!2284` | 直连 `/eventbus/publishEvent`，刻意绕开 SDK | “SDK 硬编码 data”是假设；开关未启用，流水 0 行，链路甚至未执行 |
| 契约补丁 | `!2403` | 修 YAML 缩进、data 字符串、`code=00000` | 只证明请求发出/网关受理，没有事件库入库证据 |
| 回归 SDK | `!2416`、`!2418` | 引入 `SystemInteractiveServiceImpl` | SDK 自动配置与我方 Bean 冲突，部署 crash-loop；说明 SDK 是运行时子系统，不是普通工具类 |
| 手工拼包振荡 | `!2422`、`!2433`、`!2456` | 4/8 字段来回切换，补 key/time/trace，处理 serviceName | 把事件库归一化后的 9 字段消息误当发送 wire；Spring 又把 `bid` 覆盖成组织消费方 `BidSystemOrgConsumer` |
| 测试生命周期 | `!2503` | 修 SDK 无休眠注册死循环和线程不退出 | MockWebServer 只证明自建桩返回成功，不能证明真实 EventBus 状态 |
| 需求/ID变化 | `!2560`、`!2566`、`!2595` | data 扩为全量字段，修 19/18 位 ID | 同一阶段继续改变 payload 和 trace，增加变量，但控制面仍未稳定 |
| 再次偏离 SDK | `!2598` | 认为缺 key/time，调用 SDK 底层 HTTP 手工拼 6 字段 | 客户明确否定；公共消息字段不能直接推导为 SDK 请求字段，PR 被关闭 |
| 发送 API 收敛 | `!2599` | 发布器直接调用客户 `SystemInteractiveService.sendEvent` | data plane 正确，但 `bid` 服务仍是 `DOWN/WAIT`，因此 `result=false` |
| 控制面修复一 | `!2601` | advertised `18080→8080`，续约 `10/30/60→3/3/3000` | Bean 初始化阶段过早注册，EventBus 回调时 Web 端口未就绪，返回 502 |
| 控制面修复二 | `!2603` | `ApplicationReadyEvent` 后自动注册 | 无手工注册下 `UP/ACTIVE`、3 秒续约、真实 4173 SDK=true、双方流水同 trace，首次形成事件库入库闭环 |

## 被混在一起的五层契约

| 层 | 实际职责 | 历史误判 |
|---|---|---|
| Java API | `sendEvent(topic, source, eventContent, trackReq)` | 认为文档公共消息的全部字段都必须成为方法参数/body |
| HTTP wire | SDK 发送 `serviceName/eventTopic/eventSource/data`，trace 在 Header | 把事件库管理页展示的 9 字段归一化消息复制成 8 字段或 6 字段请求 |
| 控制面 | register、同步回调、renew、`UP/ACTIVE`、advertised address | 认为生产方只 POST 即可，注册/续约仅属于消费者 |
| 投递面 | EventBus 入库、Kafka、consumerGroup/订阅 | 把 HTTP 200 或 `code=00000` 当作客户已收到 |
| 业务面 | CRM 解析 data 或组织事件触发主数据回查 | 把消息到达和业务处理成功混为一个结果 |

## 五个为什么

1. **为什么标讯发不进事件库？** `bid` 在真实发送时没有稳定处于 `UP/ACTIVE`。
2. **为什么没有稳定在线？** 宣告了事件库跨网段不可达的内部端口 `18080`，续约单位/频率错误，后续又在 Web 就绪前注册，使同步回调得到 502。
3. **为什么会配置成这样？** 团队把 `sendEvent` 当成一次 HTTP 调用，没有为生产方建立完整控制面模型。
4. **为什么多轮测试没有发现？** 功能开关长期关闭；Mock 响应由测试自己定义；验收止于单测、HTTP 200、`code=00000` 或本地 `SENT`，没有强制检查远端 `UP/ACTIVE` 和事件库同 trace 入库。
5. **为什么错误方向持续进入下一轮？** 每次把相关性写成“根因”，同时改多个变量；缺少已知成功的最小 SDK 程序和证伪矩阵，Wiki 中“4→8→4→6→4 字段”等阶段性推断又成为后续 Agent 的输入。

## 与组织架构事件同步的共同根因

组织架构是“客户推给我们”，标讯是“我们推给客户”，方向相反但故障机制相同：

| 组织事件历史问题 | 标讯事件对应问题 | 共同机制 |
|---|---|---|
| `ServerConfig`/advertised host 需要覆盖 | `18080` 对事件库不可达，应宣告 Nginx `8080` | 注册信息包含反向可达性，不是本机监听信息 |
| `ApplicationReadyEvent` 中 SDK 初始化曾阻塞 readiness 4–5 分钟 | Bean init 注册过早，EventBus 回调得到 502 | SDK 启动含同步网络调用，生命周期必须显式设计 |
| SDK 扫描 `@RequestScope` Bean 导致启动失败 | SDK `@ConfigurationProperties`/`@Autowired` 覆盖隔离配置并导致 Bean 冲突 | SDK 深度介入 Spring 容器，不能按普通 jar 装配 |
| Kafka 版本、consumerGroup、客户订阅缺一不可 | topic 路由依赖 serviceName、注册状态和客户侧订阅 | “连接成功”不等于消息会被路由 |
| 事件到达后还要回查组织 API，接口实际 POST/form 与早期假设不符 | 事件到达后 CRM 还要解析全量/标识 data | 事件传输成功和业务消费成功是两个验收点 |

第一次的经验没有变成第二次的门禁。组织事件的问题被分散记录成 Spring、Kafka、部署、订阅等局部教训；标讯方案又因“这里只生产、不消费”而主动避开 `SDKClientConfiguration`，连同必要的注册/续约一起跳过。直到 `!2601` 才复用组织事件已有的 advertised host/port 配置。

## 客户文档评价

### 文档已经明确写出的内容

- 原始《事件库SDK接入说明方案》第三步要求所有业务系统配置 `client.register` 与 `client.renewal`，第四步才介绍 `SystemInteractiveService.sendEvent`。
- 示例明确要求 `eventContent` 是 String、追踪信息使用 `EventTrackReq`。
- 技术规格书给出正式注册名、EventBus 地址和 `3/3/3000` 配置。

因此“文档完全没说注册”不成立；首版绕开 SDK、后续把低层 `HttpClientUtil` 称作“使用 SDK”，属于我方实现和契约纪律问题。

### 文档没有说明或存在歧义的内容

- SDK 文档仍写 `release_0.0.1`，项目实际使用 `release_0.0.2`，没有版本变更说明和兼容矩阵。
- 没有区分“公共事件模型”“Java 方法参数”“SDK HTTP body”“事件库落库后的归一化消息”。
- `data` 在公共模型中写 Object，在 Java API 中写 String；`key` 在样例中既出现字段名又出现业务值。
- 没说明生产方也必须保持注册在线，未给 `/eventbus/getActiveService` 与 `UP/ACTIVE` 的验收步骤。
- 没说明 `serviceAddress` 来自何处、EventBus 会反向调用 `/api/task/startTask`、地址必须从 EventBus 网段可达。
- 旧说明没有明确 `initialDelay/period` 是秒而 `renewalDuration` 是毫秒。
- 文档称 `result=true` 成功、`false` 失败，真实网关还出现 `result=null` 受理；也没说明 HTTP 200/`code=00000`、网关受理、事件库入库、Kafka、最终消费者之间的边界。
- 没记录 SDK 会吞掉非 200/IO 的原始错误、注册失败无退避循环、配置类会被 Spring 二次绑定等行为。

结论：这份材料适合作为“接入说明”，不足以作为可执行、可验收的接口规范。它增加了歧义，但真正让问题拖长的是我方没有通过最小程序、字节码和真实环境把歧义逐项消掉。

## 为什么 4173 能证明本轮根因已闭环

本轮保持同一 SDK、topic/source、data 结构和测试环境，只修控制面后得到连续证据：

1. Spring Boot 在 08:18:32 完成启动，08:20:43 才自动注册；未手工调用 register/renew。
2. EventBus 回调不再 502；`bid:8080` 持续 `UP/ACTIVE`，`lastRenewalTime` 约每 3 秒更新。
3. 真实创建 `tenderId=4173`，SDK 返回 `result=true`。
4. 我方流水 `1883` 为 `SENT`，trace/span 均为 18 位。
5. 客户事件库流水 `97776` 使用同一 `traceId=tb7u2qaaihsgx0u7s8`，data 含 `tenderId=4173`。

这证明“应用创建 → 官方 SDK → EventBus 入库”已经打通，也反证主要失败点是注册/续约/回调控制面，不是继续增删请求字段。

## 后续统一验收门禁

所有事件库生产或消费接入必须按顺序完成，任一步未通过不得宣布“已打通”：

1. 固定 SDK 版本和 JAR 哈希，记录 serviceName、topic、source、consumerGroup、环境。
2. 用最小官方 SDK 程序建立 known-good 基准，禁止先封装、先手写 HTTP。
3. 启用与目标环境相同的开关/配置启动完整 Spring Context，检查 Bean 冲突和线程生命周期。
4. 从 EventBus 所在网络验证 advertised address，记录同步回调 HTTP 状态。
5. 只读检查注册节点为 `UP/ACTIVE`，至少观察两个租约窗口，确认续约时间持续推进。
6. 发一条唯一 tenderId/traceId 的真实事件，同时保留 HTTP 状态、code、msg、result。
7. 按同一 trace 核对我方流水、EventBus 流水、Kafka 发布和最终消费者日志；验收到哪一层就只声明哪一层。
8. 一次实验只改变一个变量；连续两次修复失败后停止补丁，改做 A/B 探针矩阵和客户侧日志联合排查。

## 剩余边界与副作用（Cross-Module Impact）

- 当前 EventBus 注册回调已可达但返回 403；它没有阻止节点 `UP/ACTIVE` 和 4173 入库。仍应让客户明确回调只检查可达性还是要求业务 2xx，避免未来服务端收紧判定。
- 4173 证明 EventBus 入库，不自动证明 CRM Handler 已成功消费；若交付目标包含 CRM 业务处理，仍需客户用同一 trace 提供 Kafka/CRM 日志。
- `TenderEventPublishService` 当前异步发送与业务事务提交没有 outbox/after-commit 的可靠性闭环；网络失败记录为 FAILED 后也缺少自动补偿。传输通道已打通，但长期可靠投递应作为独立任务治理。
- 标讯与商机共享事件基础设施；任何 serviceName、trace、SDK 装配或 payload 变更必须同时回归两条链路。
