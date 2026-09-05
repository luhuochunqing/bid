---
title: 标讯创建事件推送 - 西域 CRM 事件总线
space: engineering
category: integration
tags: [integration, tender, event-bus, event-sdk, crm, push]
created: 2026-08-12
updated: 2026-08-28
health_checked: 2026-09-05
sources:
  - backend/src/main/java/com/xiyu/bid/integration/tenderevent/
  - backend/src/main/resources/db/migration-mysql/V1185__create_tender_event_logs.sql
  - backend/src/main/resources/application.yml
backlinks:
  - _index
  - configuration-binding-silent-failure
---
# 标讯创建事件推送 - 西域 CRM 事件总线

## 结论

标讯创建后，平台向西域 CRM 事件总线推送一条"事件通知消息"，`data` 只放关键标识（`tenderId`/`externalId`），由 CRM 通过已有接口回查详情。**不是传输完整业务数据**。

## 触发时机

| 触发点 | 服务 | 说明 |
|---|---|---|
| 人工录入 / 批量导入 | `TenderCommandService.createTender` | 同步入口，异步推送 |
| 第三方平台创建 | `TenderIntegrationCommandService.createNewTender` | 同属外部 API Key 鉴权链 |

**排除来源**：`SourceType.CRM_OPPORTUNITY` 不推送（避免 CRM → 平台 → CRM 回发循环）。

**不覆盖**：存量补推、更新事件。仅创建时推送。

## 传输方式：经 SDK SystemInteractiveService 推送

> **2026-08-26 反编译核实（ClientSDK-release_0.0.2 字节码）**：SDK 的 `sendEvent(eventCode, eventSource, eventContent, eventTrackReq)` 把 `data` 字段取自 `eventContent` 入参，trace 信息经 `buildHeaderParamMap` 写入 HTTP Header，无签名/协议封装。此前「SDK 把 data 硬编码为 trace、无法携带业务关键标识」的判断**不成立**，已回归 SDK 标准发送通道（`@Import` 单类注册 `SystemInteractiveServiceImpl` + 自建 `ServiceRegisterProperties` bean，不能整包 `@Import SDKClientConfiguration`——会连带激活 Kafka/ZK/注册等消费侧组件），消除与 SDK 的 wire 契约漂移风险。
>
> **SDK 已知行为**（字节码 + wire 测试锁定）：`HttpClientUtil.httpPost` 仅在状态码 200 时解析响应体并返回，**非 200 状态码与 IO 异常均被其静默吞掉后返回 null**（旧链路观察到的"抛 NPE"实为 `sendEvent` 解引用 null 所致，非 HTTP 层主动抛出）；`data.result=null`（网关 code=00000 实测形态）视为受理成功，仅显式 `result=false` 判为失败。
>
> **联调最大教训（2026-08-26）**：「一直没打通」期间 `tender_event_logs` 流水表为 **0 行**——功能开关 `XIYU_TENDER_EVENT_SDK_ENABLED` 从未在任何环境启用（`.env.example`/部署脚本均未配置），推送链路从未真正执行过。怀疑 wire 契约前先查流水表/日志确认请求是否发出过。

### 请求结构

```json
{
  "serviceName": "bid",
  "eventTopic": "BidTenderChange",
  "eventSource": "bid",
  "data": "{\"tenderId\":123,\"externalId\":\"ext-1\"}"
}
```

- 这是 **SDK `sendEvent` 组装的标准 wire 形态（4 字段）**，由 `TenderEventSdkSender` 调用 SDK 标准通道发出（2026-08-27 客户明确指示，详见 §8）；`serviceName` 是网关投递路由的必带字段
- `data` 是 **JSON 字符串**（网关 `EventContentReqDto.data` 声明为 String），由 `TenderEventSdkSender.serializeData` 把业务载荷 Map 序列化后作为 `eventContent` 传给 SDK；只含 `tenderId`（必填）和 `externalId`（有值才入 Map）
- 链路追踪 `traceId` / `spanId` / `parentId` 经 `EventTrackReq` 由 SDK 写入 HTTP Header，不进 body；`key`/`time` 也不进 body（那是事件库内部存储形态字段，非 SDK wire 契约）
- ⚠️ **契约坑**：若 `data` 传原始对象而非 JSON 字符串，网关返回 `400 Bad Request`（2026-08-25 实战踩坑）

### 事件编码（`TenderEventCode`）

| 编码 | source | topic |
|---|---|---|
| `BID_TENDER_CHANGE` | `bid` | `BidTenderChange` |

## 流水表

`tender_event_logs`（V1185）记录每条事件：`tender_id`、`event_code`、`event_source`、`event_topic`、`trace_id`/`span_id`/`parent_id`、`event_content`（data JSON）、`status`（SENT/FAILED）、`error_message`、`created_at`。

## 配置

```yaml
xiyu:
  integrations:
    tender-event:
      sdk:
        enabled: ${XIYU_TENDER_EVENT_SDK_ENABLED:false}
        server-register-url: ${XIYU_TENDER_EVENT_SERVER_REGISTER_URL:}
        service-name: ${XIYU_TENDER_EVENT_SERVICE_NAME:bid}
```

> 默认 `disabled`（安全默认），生产环境用 `XIYU_TENDER_EVENT_SDK_ENABLED=true` 启用。

## 模块结构

```text
backend/src/main/java/com/xiyu/bid/integration/tenderevent/
├── domain/          # 纯核心：推送策略、事件编码/状态/消息体
├── application/     # 编排：端口(发布/流水)、payload mapper、发布服务
└── infrastructure/
    ├── persistence/ # 事件流水表实体/仓库
    └── sdk/         # 直连 HTTP 生产者 + 配置（TenderEventHttpSender 持有 wire 契约）
```

## Wire 契约单一源（2026-08-24 去重）

`TenderEventHttpSender`（`infrastructure/sdk/`）是事件总线发送的唯一入口：委托 SDK `SystemInteractiveService.sendEvent` 上报，本方只负责 data 载荷 JSON 序列化与结果解释（`!Boolean.FALSE.equals(result)`，兼容网关 result=null 形态）。`TenderEventSdkProducer` 与 `OpportunityPredictionEventPublisher` 都委托它发送，各自只保留自己的载荷与上下文日志（公开构造/入口不变）。

> 新的事件发布方直接复用 `TenderEventHttpSender`，**不要**再复制 body/trace/超时逻辑（本页此前的重复即此类 slop）。

## 关键设计

- **不阻塞主链路**：`TenderEventPublishService` 用独立线程池 `tenderEventExecutor` 异步发送。
- **开关优先**：`enabled=false` 时不推送、不产生流水。
- **策略前置**：`TenderEventPolicy.shouldPublish(sourceType)` 决定是否推送。
- **失败不阻断创建**：推送异常仅记录 `FAILED` 流水 + 告警日志，不影响标讯创建结果。

## 根因与契约修复（2026-08-25 部署实战）

### 1. YAML 缩进错位 → 配置绑定失败（无报错，最难排查）

- **现象**：测试环境启用 `XIYU_TENDER_EVENT_SDK_ENABLED=true` 后仍不推送。
- **根因**：`application.yml` 中 `tender-event:` 块因 **6 空格缩进**被错误嵌套进 `organization:` 子树，Spring 无法绑定 `xiyu.integrations.tender-event.sdk.enabled`，开关读到 `null`→默认 `false`。
- **定位手段**：Arthas 探针在启动期读取实际绑定的 `enabled` 值，确认 `null`，再对照 YAML 树形结构发现缩进错位。
- **修复**：`tender-event:` 必须与 `organization:` 同级（**4 空格缩进**），作为 `integrations:` 的直接子节点。
- **教训**：YAML 缩进错位**不报错、静默回退默认值**。配置类属性绑定失败时，先用探针/日志确认 Spring 实际读到的值，再反查 yml 层级。

### 2. data 契约：必须是 JSON 字符串（见「请求结构」）

- 万不可传 Map/对象，否则网关 `400`。

### 3. 网关成功信号：外层 `code=="00000"`

- **现象**：网关已返回 `{"code":"00000","data":{"result":null},"msg":"响应成功"}`，但发送器误判失败（流水标 `FAILED`）。
- **修复**：结果解释兼容 result=null 受理形态，仅显式 `result=false` 判失败。
- **教训**：外部网关的"成功"语义必须以实测响应为准，勿默认业务 payload 里的 `result` 必为布尔。

### 4. 排障盲区：sendEvent 丢弃外层 code/msg（2026-08-26，tenderId=2282）

- **现象**：测试环境推送流水标 FAILED 且 `result=false`，但无任何异常堆栈--网关为什么拒收完全不可知。
- **根因（一层）**：SDK `sendEvent` 只透传 `data.result` 并丢弃外层 `code`/`msg`，而 `result=false` 时拒绝原因恰在外层（实测样例 `{"code":"B20001","msg":"topic未注册","data":{"result":false}}`）；`SendEventRespDto` 又仅含一个 Boolean 字段，序列化返回值同样无济于事。
- **修复（一层）**：sender 直调 `HttpClientUtil.httpPost`，完整响应原文落 info 日志（`事件总线原始响应 eventTopic=… response=…`），在离源头最近的一层保留第一手证据。
- **根因（二层，tenderId=2285）**：拿到原始响应后看到 `code=00000 / msg=响应成功 / data.result=false`--网关协议层正常，业务层拒绝。对照客户接口文档发现 SDK 0.0.2 的 `sendEvent` 组装的 body `{serviceName, eventTopic, eventSource, data}` **缺少 `eventCode`、`time`、`key` 等必需字段**，网关业务层因缺字段无法路由到处理逻辑，直接返回 `result=false`。
- **修复（二层）**：按客户文档全字段组装 body：补 `eventCode`/`eventSource`/`eventTopic`/`time`/`key`/`data`/`traceId`/`spanId`/`parentId`（trace 三元组同时保留在 Header）。
- **教训**：集成排障必须在**离源头最近的一层**留全量响应证据；只透传业务字段的 SDK 包装层会把故障细节藏在调用方看不见的地方。另注意 `httpPost` 对非 200/IO 异常静默返回 null，null 响应须显式判失败而非等 NPE 暴露。**SDK 旧版 body 结构不一定与网关最新接口文档一致，必须以接口文档为准逐字段核对。**

### 5. ~~注册参数是占位值：bid/BidTenderChange 从未在网关注册~~（已推翻，详见 §7）

> **2026-08-27 更正**：本节"bid/BidTenderChange 从未在网关注册"的结论已被客户手动触发成功样例（tenderId=2282）与探针实测推翻。`bid`/`BidTenderChange` 注册值正确，`data:{}` 不是"网关静默丢弃"，而是 fastjson 1.2.83 解析时丢弃 null 值的默认行为。本节内容仅保留作为排障历史参考。

- **现象**：客户反馈 tenderId=2289 仍未推送到事件库；网关已回 `code=00000` 但 `data:{}`。
- **排障过程**：客户提供全量接口文档（`EventInfoReqDTO`/`EventHandleService` 参考实现）后，修正两处契约偏差：
  - body 回退 SDK 原始 4 字段 `{serviceName, eventTopic, eventSource, data}`：客户文档"消息结构"（eventCode/time/key/traceId/spanId/parentId）是**网关内部消息格式**，由网关自行填充，不放在 HTTP body。此前误塞导致网关返回 `data:{}` 但事件未发布。
  - `parentId` 从 traceId 值改为 `"0"`（客户参考实现：无父链路时 parentId="0"）。
- **根因（三层，关键）**：三次实测交叉证明 `serviceName`/`eventTopic` 是**自造占位值，从未在网关注册**：
  | 实测 | serviceName | eventTopic | 网关响应 | 语义 |
  |---|---|---|---|---|
  | 2285/2290 | `BidSystemOrgConsumer`（已注册组织事件服务） | `BidTenderChange` | `data:{result:false}` | 服务认识，**topic 未注册** |
  | 2291 | `bid`（未注册） | `BidTenderChange` | `data:{}` | **服务不认识，静默丢弃** |
  - 组织事件 SDK 的 `ServiceRegisterProperties` 类级标注 `@ConfigurationProperties(prefix="client.register")`，Spring Boot 把 `client.register.serviceName`（=`BidSystemOrgConsumer`）绑定覆盖到该类型**所有 bean**，导致标讯发送器拿到错误服务名。
  - 修复：`TenderEventHttpSender` 不再注入 SDK bean，`serviceName`/`serverRegisterUrl` 直取 `TenderEventSdkProperties`（默认 `bid`）。
- **当前阻塞**：标讯事件在网关注册的**正式参数**（serviceName / eventTopic / eventSource）需客户提供。`TenderEventCode` 的 topic/source 仍为占位值（类注释已标注"待联调确认"），客户文档 `EventInfoReqEnum` 仅含组织事件。
- **教训**：外部事件库对接，**服务注册名与事件 topic 必须从客户侧拿正式值**，自造占位值联调只会得到"网关静默接受但不发布"（`data:{}`）的假象，流水表 SENT 与 `code=00000` 都不等于客户真实收到。

### 6. 客户手动触发成功样例逐字段对比（2026-08-27，tenderId=2282）

- **客户成功样例**（手动触发，执行成功）：
  ```json
  {
    "data": {"tenderId": "2282"},   // 对象形态
    "eventSource": "bid",
    "eventTopic": "BidTenderChange",
    "key": "tenderId",              // 业务主键字段名，body 必填
    "parentId": "0",
    "traceId": "748104276040151040",
    "spanId": "s748104276040151041"
  }
  ```
- **重大结论**：`bid`/`BidTenderChange` 注册值**正确**（客户手动触发执行成功），上一节"从未在网关注册"的判断被客户实测推翻；`parentId="0"` 证实我方修改方向正确。
- **逐字段对比**：
  | 字段 | 客户成功 | 我方（2292 实测） | 判定 |
  |---|---|---|---|
  | eventTopic | BidTenderChange | BidTenderChange | ✅ |
  | eventSource | bid | bid | ✅ |
  | key | "tenderId" | "tenderId" | ✅（已补） |
  | data | 对象 `{"tenderId":"2282"}` | 字符串 `"{\"tenderId\":2292}"` | 🔴 形态存疑 |
  | traceId/spanId/parentId | 在 body | 在 HTTP Header | 🔴 位置存疑 |
  | serviceName | 样例截断未见 | bid | ❓ 待确认 |
  | time / eventCode | 样例截断未见 | 无 | ❓ 待确认 |
- **补 key 后仍 `data:{}`**（2292 实测），说明 key 非充分条件；剩余差异集中在 **data 形态** 与 **trace 位置**。
- **待客户补全**：完整未截断的请求 body 原文（确认 serviceName/time/eventCode 是否在 body、data 到底传对象还是字符串、trace 三元组在 body 还是 Header）。

### 7. ~~最终契约与 fastjson null 丢弃发现（2026-08-27，探针实测）~~（8 字段契约已被 §8 推翻；fastjson null 丢弃结论仍有效）

- **最终 HTTP body 契约**（8 字段）：
  ```json
  {
    "traceId": "...",
    "spanId": "...",
    "parentId": "0",
    "eventTopic": "BidTenderChange",
    "eventSource": "bid",
    "key": "tenderId",
    "time": 1787810045121,
    "data": "{\"tenderId\":2282}"
  }
  ```
  - `data` 是 **JSON 字符串**（网关 `EventContentReqDto.data` 声明为 String，传对象 HTTP 400）
  - `key` = 业务主键**字段名**（`tenderId`），非主键值
  - `traceId`/`spanId`/`parentId` 同时写入 body 和 HTTP Header（SDK `buildHeaderParamMap` 既有行为）
  - `time` = 事件产生时间（毫秒时间戳），**客户文档公共消息结构标注必需**——2026-08-27 补齐（commit 35838bf28），缺此字段网关虽受理（HTTP 200）但事件不进入投递链路（客户收不到的修复项）
  - 不发 `serviceName`（客户样例无此字段；SDK `ServiceRegisterProperties` 类级绑定会覆盖）

- **`data:{}` 真相**：网关对所有合规请求均返回原始 HTTP 响应 `{"code":"00000","data":{"result":null},"msg":"响应成功"}`。SDK `HttpClientUtil.httpPost` 内部用 fastjson 1.2.83 的 `JSONObject.parseObject` 解析响应，**fastjson 默认丢弃 null 值**，`result:null` 的键被丢弃，data 变成空对象 `{}`。应用日志打印的 `data:{}` 是 fastjson 序列化结果，**不是网关返回空对象**。

- **探针实证**（同一程序、同一网关、同一 body）：
  | 路径 | 原始 HTTP 响应 | fastjson 解析后 |
  |---|---|---|
  | SDK `HttpClientUtil.httpPost` | 不可见（SDK 内部解析） | `data:{}` |
  | 直接 Apache HttpClient + `EntityUtils.toString` | `data:{"result":null}` | `data:{}` |

- **结论**：
  - `interpretResult` 逻辑正确（data 是 JSONObject 且 result 非 false = 成功，空 data 同样判成功）
  - 应用推送 tenderId=2289/2294/2295 全部**受理成功**（`result:null`）
  - 客户反馈"2289 未收到"是旧版本问题（serviceName 错误导致 `result:false`），当前版本已修复
  - **闭环验证待办**：请客户在事件库查询 tenderId=2294/2295（或新建标讯）是否收到

- **教训**：跨系统联调中，"日志打印的响应"与"网关原始 HTTP 响应"之间可能存在 JSON 库的序列化行为差异（fastjson 丢弃 null）。排障时不能只看应用日志的 JSON 对象打印，必须在 HTTP 客户端层打印原始响应字符串才能看到真相。
### 8. SDK sendEvent 4 字段契约回归——serviceName 是投递路由必带字段（2026-08-27，客户指示 + 双重反编译证据）

- **客户明确指示**：不要手工拼 body 直连 `POST /eventbus/publishEvent`，必须走 SDK 标准通道 `SystemInteractiveServiceImpl.sendEvent(eventCode, eventSource, eventContent, eventTrackReq)`。客户同时发来 IDEA 反编译截图（与本仓 ClientSDK-release_0.0.2 字节码逐条吻合）。
- **SDK sendEvent 的真实 wire 形态（4 字段）**：`{serviceName, eventTopic, eventSource, data}`——serviceName 从 SDK 自身 `ServiceRegisterProperties` 取值放进 body；trace 三元组经 `EventTrackReq` 走 Header；**key/time/traceId 均不进 body**。消息结构文档的 9 字段是事件库内部存储形态，不是 SDK wire 契约（§7 的 8 字段直连方向错误）。
- **根因论断**：手工 body 省略 serviceName 后，网关 HTTP 层受理成功（`code=00000`）但**事件不进入投递链路**（tenderId=2289~2296 客户事件库均未收到）——serviceName 极可能是网关投递路由的必需字段。此前带 serviceName 失败（`result=false`「topic未注册」）是因为值被 `client.register.*` 绑定覆盖成组织消费侧的 `BidSystemOrgConsumer`，不是 serviceName 本身不该发。
- **serviceName 供给链**：`TenderEventSdkConfig` 用标讯自己的 `TenderEventSdkProperties.serviceName`（环境变量 `XIYU_TENDER_EVENT_SERVICE_NAME`，测试环境=`bid`）构造 `@Primary ServiceRegisterProperties` 满足 SDK 注入；与组织事件消费侧的 `client.register.*` 配置隔离。
- **代码落点**：`TenderEventSdkSender`（原 `TenderEventHttpSender` 更名）直接调 `SystemInteractiveService.sendEvent`；wire 测试改为真实 SDK 通道打 MockWebServer 锁 4 字段契约。
- **SDK NPE 兜底**：非 200/IO 异常时 `httpPost` 静默返回 null，`sendEvent` 对 null 取 `data` 抛 NPE——`TenderEventSdkSender` 捕获后判失败。
- **闭环验证待办**：部署后新建标讯推送，请客户在事件库确认收到（此前 2289~2296 均未收到）。

### 9. 装配层大坑：ServiceRegisterProperties 类级绑定覆盖（2026-08-27，result=false 实测）

- **现象**：切回 SDK sendEvent 通道后部署（commit 69802229a），创建标讯 tenderId=2297，网关返回 **`result=false`**（此前直连同 4 字段是 `result:null` 受理成功）。
- **探针矩阵定位**（curl 直接打网关，Content-Type 已核实为 application/json）：
  | 探针 | serviceName | 网关响应 |
  |---|---|---|
  | A | `bid` | `result:null`（受理成功） |
  | B | `BidSystemOrgConsumer` | `result:false` |
  | C | `text/plain`（任何） | 415 Unsupported Media Type |
  → **bid 是合法注册名**，SDK 实际发出的 serviceName 被替换成了 `BidSystemOrgConsumer`。
- **根因**：SDK 的 `ServiceRegisterProperties` 自带**类级** `@ConfigurationProperties(prefix="client.register")`。`@Bean` 方法 `setServiceName("bid")` 之后，Spring 的 `ConfigurationPropertiesBindingPostProcessor` 用环境值（组织消费侧 `client.register.service-name=BidSystemOrgConsumer`）**重新绑定覆盖**。这是当年 `64a3fcae8` "规避 client.register 绑定覆盖"踩过的同一个坑——@Bean 注册方案原样重踩。
- **第二层坑**：即便用反射注入干净配置，SDK 服务类 `SystemInteractiveServiceImpl` 的 `@Autowired clientSdkConfig` 字段只要经过容器（@Bean/@Import），`AutowiredAnnotationBeanPostProcessor` 就会再次注入容器里的 bean（覆盖反射值或直接报无 bean）。
- **教训**：客户 SDK 的属性类若带类级 `@ConfigurationProperties`，手工构造对象必须**完全脱离容器**，不能作为 @Bean 注册（后置处理器会无条件重绑）；带 `@Autowired` 字段的 SDK 服务同理。

### 10. 服务注册生命周期与测试环境隔离（2026-09-01，mvn -q test 挂起根因）

- **现象**：执行 `mvn -q test` 时测试命令长时间不退出、拿不到退出码。
- **根因**：
  1. `TenderEventSdkConfigTest` 在装配测试中硬编码了假域名 `http://event-bus.local`；
  2. SDK 的 `ClientRegisterComponent.register()` 底层为 `while (isRun && enableRegister)` 循环，在 DNS 解析失败时返回 null 并立即无休眠重试；
  3. `TenderEventRegistrar` 在虚拟线程中启动该循环但未实现 `AutoCloseable`，测试容器销毁后线程依然常驻 JVM 并死循环发起 DNS 查询，导致 Surefire 无法回收。
- **修复**：
  1. `TenderEventRegistrar` 实现 `AutoCloseable`，在 `close()` 中通过反射将 `ClientRegisterComponent.isRun` 设为 `false` 并中断后台注册线程；
  2. `TenderEventSdkConfig` 在 `@Bean` 注解上声明 `destroyMethod = "close"`；
  3. `TenderEventSdkConfigTest` 改用 `MockWebServer` 提供本地快速响应，隔离真实网络；
  4. 清理 `TenderEventSdkProducerTest` 中的假域名引用（改为本地回环 `http://127.0.0.1:8080`）。
- **教训**：
  - 测试中绝不使用未配置的 `.local` 假域名，涉及 HTTP 的测试一律使用 `MockWebServer` 监听 `127.0.0.1:port`；
  - 任何包含重试/常驻后台线程的组件必须实现 `AutoCloseable` 并在容器销毁时显式终止内部循环。

