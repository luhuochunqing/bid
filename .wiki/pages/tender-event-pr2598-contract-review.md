---
title: PR2598 标讯事件推送契约与验证审查
space: engineering
category: lessons
tags: [tender, event-sdk, integration, contract, review]
created: 2026-09-09
updated: 2026-09-09
health_checked: 2026-09-14
sources:
  - backend/libs/ClientSDK-release_0.0.2.jar
  - backend/src/main/java/com/xiyu/bid/integration/tenderevent/application/TenderEventPublishService.java
  - backend/src/main/java/com/xiyu/bid/integration/tenderevent/infrastructure/sdk/TenderEventSdkProducer.java
  - backend/src/main/java/com/xiyu/bid/integration/tenderevent/infrastructure/sdk/TenderEventSdkConfig.java
  - backend/src/test/java/com/xiyu/bid/integration/tenderevent/infrastructure/sdk/TenderEventSdkProducerTest.java
backlinks:
  - _index
---
# PR2598 标讯事件推送契约与验证审查

## 审查边界与结论

- 历史审查对象：[PR2598](https://gitee.com/allinai888/bid/pulls/2598)，当时 head `9c9baacee201352d64ec48e3e719ad7771dc00ca`、base `0ebf4248fc3374242898e76a709c8bb8caa1cc80`；下文涉及 PR 行号的结论均指该历史版本。
- 证据：用户本轮客户文档、Gitee PR 描述/差异/评论（评论为空）、本地 SDK javap 字节码、创建入口与发送流水源码。qoder 的 integration-tender-event-push.md 为二手线索。
- 初次审查时尚未取得客户投递日志；后续客户补充了 APISIX、事件库入库、Kafka 与 CRM 消费日志，形成同一 trace 的成功对照，相关更正见下一节。
- PR 补 key/time 的必要性已被后续证据否定；生产实现现已收敛为直接调用客户 `SystemInteractiveService.sendEvent`。

## 客户成功样本补证后的更正

客户补充 2282 的事件库界面截图，以及 2311 的 APISIX、事件总线入库、Kafka、CRM 消费日志后，以下事实已经闭环：

| 字段/行为 | 成功样本证据 | 结论 |
|---|---|---|
| `eventTopic` / `eventSource` | `BidTenderChange` / `bid` 从网关贯穿到 CRM | 取值正确 |
| `serviceName` | APISIX 入口请求为 `bid` | 取值正确 |
| `data` | 入口与下游均为转义后的 JSON 字符串 | `serializeData(data)` 正确 |
| `key` | 手动入口请求为 `tenderId`，下游消息为 `2311` | 入口是主键字段名；总线解析 data 后转为业务值 |
| trace/span | `t748...` / `s748...` 成功写入事件库并到达 CRM | 前缀是合法样本；本任务仍按用户最新要求限制 ID 不超过 18 位 |
| `parentId` | 首发事件为 `0` | 当前取值正确 |
| 成功信号 | HTTP 200、code=00000、result=true、事件库 INSERT、Kafka、CRM 接收 | 可作为端到端成功基准 |

成功样本是手动触发请求，不能推导 SDK 调用方也要复制完整 HTTP body。客户明确要求生产方调用 `SystemInteractiveService.sendEvent(...)`；因此 PR2598 只复用 `HttpClientUtil`、自行拼六字段的方向应撤回。实现应把调用收敛到 `TenderEventSdkProducer`，构造 `EventTrackReq` 后调用四参数 `sendEvent`，删除自定义 Sender/HttpSender 协议层。

仓库 JAR `ClientSDK-release_0.0.2.jar` 的字节码显示 SDK body 包含 `serviceName/eventTopic/eventSource/data`，trace 三元组走 Header。标准事件的 key 值解析、时间与其他消息封装由事件总线负责；业务代码不再复制该私有协议。

## 契约必须分层

| 层 | 已知证据 | 不可越界推论 |
|---|---|---|
| 事件消息模型 | 用户表含 eventCode/eventTopic/time/key/data Object/trace | 不能直接当 publishEvent HTTP DTO |
| Java 调用 | sendEvent(eventCode,eventSource,eventContent String,EventTrackReq)；示例只给业务标识 | 未提供 key/time 参数不能推导调用方须绕开该方法 |
| 当前 SDK HTTP | javap 确认 body 仅 serviceName/eventTopic/eventSource/data；data 为第三入参 String；trace 在 Header | SDK 行为并不证明部署网关版本兼容，须确认版本和真实 endpoint |
| 客户入库/投递 | 后续已取得 2311 的事件库 INSERT、Kafka 与 CRM 消费日志 | 同一 trace 已形成端到端成功对照 |

- PR 将自行拼包改称“SDK标准通道”，实际只复用 HttpClientUtil 和 header helper。使用 SDK 工具类不等于遵循客户给定的 SystemInteractiveService.sendEvent 调用契约。
- 如果客户消息表直接就是 HTTP 契约，PR 仍未按表发送 eventCode/data Object/body trace；选择性补 key/time 的理由不足。此处不是建议继续补字段，而是要求先确认接口层次。
- key 表述为业务 key（deptId/userId），不足以锁定固定字段名 tenderId 还是具体 ID 值；历史手动样例支持字段名但未证明同一接口。必须问清去重/分区语义。
- 用户本轮文档要求 data 仅标识；当前代码发20字段。历史 wiki 称客户后来要求全量，需核定有效版本，不能自行推翻任一口径。

## 确定缺陷与验证缺口

1. PR Sender 95–104：非 null JSON 只要 data.result 不是 Boolean.FALSE 均 accepted=true。不检查 code，也不验证 data/result 结构。合成的HTTP200反例 `{ "code":"ERROR", "msg":"topic未注册" }` 会被标成功。HttpClientUtil 字节码确认只检查 HTTP200，不检查业务code。旧SDK对于缺失data会异常并被捕获为失败，本PR扩大误报面。
2. 本条是审查 PR #2598 时的历史判断：当时尚未取得 `result=null` 的明确口径。客户随后确认 `null/true` 均表示受理成功、仅 `false` 表示失败；当前实现已按这一最终口径处理。受理成功仍不等于消费者最终收到，必须继续用事件库入库、Kafka 发布和 CRM 消费日志闭环验证。
3. Sender仅返回boolean，拒绝/异常细节不透传，PublishService 107–137 的 errorMessage 常为null。SDK HttpClientUtil不保留非200响应体；最新 isAccepted也只打印data，丢弃已拿到的code/msg，妨碍定位。
4. Wire测试使用MockWebServer自行返回true/null；断言key/time只证明构造行为，不能证明网关需要这些字段。缺少HTTP200业务错误、data缺失/异常结构的反例。PR报告27测试通过不构成客户收件证明。
5. 链路 ID 的最终用户约束是长度不超过 18 位；MDC 非空且长度合规即可复用。生产 Pod 有 `HOSTNAME` 时调用客户 SDK `SnowflakeIdUtil.generateId()`，完整 long 无损转 base36 并加 t/s 前缀，最长 14 位；无 `HOSTNAME` 的本地环境用 87 位安全随机值兜底，加前缀后最长 18 位。
6. 既有可靠性风险：TenderCommandService类级@Transactional、TenderIntegrationCommandService事务入口内调用publishOnCreate；其100行立即executor.execute，没有提交后挂钩。事件可能先于事务提交；失败只记流水，没有在该链路看到持久化待发送/重试调度。此为代码风险，不能直接认定是4160/4169的实测根因。

## 独立验证证据

- 独立审查使用JShell21和真实fastjson1.2.83复现PR判定逻辑：合成响应code=ERROR且无data返回true；code=ERROR且result=true也返回true；result=false返回false。这些是合成反例，不是客户实际响应。
- 同版本fastjson核实parseObject含result:null时containsKey(result)=true、size=1，而toJSONString为{}。历史wiki所称“解析丢弃null键”不准确，是默认序列化省略null；此行为不能证明业务成功。

## 为什么反复

- wiki历史中“8字段→4字段→6字段”“注册名不合法→已推翻”“19字符→18数字”等结论反复变化；应保存假设、原始证据、证伪条件，而非把推断写成确定根因。
- 缺少一份同环境、同SDK版本、同topic、客户确认入库的成功基准请求。
- 本地宽松成功判定、测试端预设成功和缺少客户收件核验共同造成假闭环。
- 多变量同时调整（装配、注册、trace、body、payload），无法归因。

## 最小闭环

1. 固定SDK版本/JAR哈希、网关地址/环境、生产服务名、topic/source、注册状态及标讯data字段版本。
2. 由客户参考方式运行最小Java发送器（同一SDK，仅tenderId、独立trace）；对齐原始HTTP请求/响应及客户入库ID/trace。使用已授权测试数据执行，当前审查没有发事件。
3. 最小调用失败则检查SDK/服务端版本与注册路由及服务端错误，先于平台拼包修改；成功则逐项对比平台同环境请求与配置。
4. 保留HTTP状态、code/msg/result、trace和入库核验状态；null结果在确认语义前保留不确定。
5. 以客户确认的成功基准建立契约回归及错误反例，再确认业务创建到事件库入库全链路。
6. 可靠性另行落实提交后发送/事务outbox、可控重试及幂等，避免用传输契约修改掩盖交付保障问题。

## 副作用（Cross-Module Impact）

- 历史 `TenderEventSdkSender` 曾被商机预测发布器共用，因此收敛时同步迁移了两个发布器。
- 后续实现已删除 Sender 与自定义 HTTP 协议层，两个发布器分别直接调用隔离的 SDK 实例；本页保留历史审查与证据演进记录。
