---
title: 标讯创建事件推送 - 西域 CRM 事件总线
space: engineering
category: integration
tags: [integration, tender, event-bus, event-sdk, crm, push]
created: 2026-08-12
updated: 2026-09-10
health_checked: 2026-09-14
sources:
  - backend/src/main/java/com/xiyu/bid/integration/tenderevent/
  - backend/src/main/resources/db/migration-mysql/V1185__create_tender_event_logs.sql
  - backend/src/main/resources/application.yml
backlinks:
  - _index
  - configuration-binding-silent-failure
  - opportunity-prediction-reminder
---
# 标讯创建事件推送 - 西域 CRM 事件总线

> 两次事件库集成的共同根因、文档责任与统一验收门禁见 [[event-library-integration-rca]]。

## 结论

标讯创建后，平台向西域 CRM 事件总线推送一条"事件通知消息"。**2026-09 演进：`data` 从"只放关键标识（`tenderId`/`externalId`）由 CRM 回查详情"改为直接携带标讯详情页全量展示字段（20 字段），供 CRM 直接消费、不再回查**（应客户全量字段推送要求，字段清单与链路 ID 约束详见 §11）。推送触发时机、策略、开关、线程池、流水编码均不变。

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

- 这是 **SDK `sendEvent` 组装的标准 wire 形态（4 字段）**，由两个事件发布器直接调用 SDK 标准通道发出（2026-08-27 客户明确指示，详见 §8）；`serviceName` 是网关投递路由的必带字段
- `data` 是 **JSON 字符串**（网关 `EventContentReqDto.data` 声明为 String），由发布器把业务载荷 Map 序列化后作为 `eventContent` 传给 SDK；**2026-09 起固定包含标讯全量 20 字段（清单见 §11），null/空白字段传空字符串**（此前只含 `tenderId`/`externalId`，一度错误省略空字段）
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
    └── sdk/         # ClientSDK 发布器与隔离配置
```

## SDK 发送边界（2026-09-09）

`TenderEventSdkProducer` 与 `OpportunityPredictionEventPublisher` 分别直接调用客户 SDK 的 `SystemInteractiveService.sendEvent`。发布器负责 data 载荷 JSON 序列化、`EventTrackReq` 组装和结果解释（`!Boolean.FALSE.equals(result)`，兼容 SDK 返回 `result=null` 的受理成功形态）。`TenderEventSdkConfig` 为两个发布器分别传入隔离的 SDK 实例，避免 Spring 的 `client.register` 配置重新绑定客户地址。

> 新的事件发布方应调用 SDK 高层 `sendEvent` 接口，不再通过 `HttpClientUtil` 手工拼装事件总线 HTTP 请求体。

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
- **当前代码落点**：`TenderEventSdkProducer` 与 `OpportunityPredictionEventPublisher` 分别直接调用 `SystemInteractiveService.sendEvent`，并在发布器边界用单元测试锁定 SDK 参数与返回值语义；中间 Sender 已于 2026-09-09 删除。
- **SDK NPE 兜底**：非 200/IO 异常时 SDK 可能抛出运行时异常；两个发布器均捕获异常并将本次发布判为失败。
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

### 11. 全量字段推送演进 + 链路 ID varchar(19) 雪花约束（2026-09-06）

- **演进背景**：应 CRM 客户要求，事件推送从"轻量标识 + CRM 回查"改为"直接携带全量业务字段"，CRM 无需回查即可消费。推送触发/策略/开关/线程池/流水编码均不变，仅扩充 `data` 字段（最小加法）。
- **标讯 data 全量字段（20 个，`TenderEventPayload`）**：`tenderId` / `externalId` / `title`(项目名称) / `purchaserName`(招标主体) / `region`(总部所在地) / `registrationDeadline`(报名截止) / `bidOpeningTime`(开标时间) / `customerType` / `priority` / `projectType`（三者存后端枚举码，`TenderEventPayloadMapper.toData` 序列化时转中文标签）/ 联系人1（`contactName`/`contactPhone`/`contactTel`/`contactMail`/`contactNature`）/ 联系人2（`contactName2`/`contactPhone2`/`contactTel2`/`contactMail2`/`contactNature2`）。
- **商机 data 全量字段（10 个，`OpportunityPredictionEventPayload`）**：见 [[opportunity-prediction-reminder]]。
- **⚠️ 链路 ID varchar(19) 陷阱（高价值）**：客户事件库 `trace_id`/`span_id` 列宽为 **varchar(19)**。`TenderEventSnowflakeUtil` 生成 "t"/"s" 前缀 + 18 位雪花 ID = 恰好 19 字符；**UUID 去横线后 32 字符会超长写入失败**。标讯侧早已用雪花 ID，商机侧 `OpportunityPredictionEventPublisher` 曾遗漏仍用 UUID（2026-09-06 修复：`spanId` 改 `nextSpanId()`，`currentTraceId()` 加 ≤19 校验 + 回退 `nextTraceId()`）。复用上游 MDC traceId 时同样必须校验 ≤19。
- **⚠️ 18 位长度约束最终实现（2026-09-09）**：用户确认链路 ID **不超过 18 位**，并未要求纯数字。生产 Pod 有 `HOSTNAME` 时调用客户 SDK `SnowflakeIdUtil.generateId()`，完整 long 无损转 base36 并恢复 `t`/`s` 前缀，最长 14 位；本地不再自建 worker/datacenter。ClientSDK 0.0.2 在缺少 `HOSTNAME` 时会静态初始化空指针，因此无该环境变量的本地环境使用 87 位 `SecureRandom` 兜底，base36 加前缀后最长 18 位。MDC 中非空且长度不超过 18 的上游 traceId 可直接复用。
- **枚举中文映射同源**：`customerType`/`priority`/`projectType`/`resultType` 的中文标签与 `ProjectExportService`、前端 `projectListFormatters.js` 同源，未知值 fallback 原值。事件侧标签已收敛到 `TenderEventEnumLabels` 单一来源（标讯 Mapper 与商机 Publisher 共用，2026-09-06 deslop）；与 `ProjectExportService`/前端的跨模块字面量重复仍是技术债（`duplicate-implementations` 门禁不检测常量表，待单独任务收敛）。

### 12. 注册地址与续约参数修复（2026-09-09，tenderId=4170/4171/4172）

- **真实现象**：测试标讯 `4170`、`4171` 对应失败流水 `1880`、`1881`；SDK 发布和相同四字段直连请求均返回 `result=false`。手工调用 `/eventbus/register` 注册 `bid` 后，相同四字段请求返回 `result=true`。
- **advertised 地址对照**：实际拓扑是 Java 进程监听 `18080`，Nginx 对外监听 `8080` 并反向代理到 `127.0.0.1:18080`。事件库跨网段无法直达 `18080`；宣告该端口时 register/renew 均约需 128 秒，注册完成后的短暂窗口虽可能 publish 返回 `true`，但续约阻塞无法维持 3 秒节奏，服务不能稳定在线。改为事件库可达的对外代理端口 `8080` 后，register/renew 分别约 66 ms、56 ms；连续 24 秒按不超过 3 秒的节奏续约后，`getActiveService` 显示 `bid:8080` 为 `UP/ACTIVE`，标准四字段 publish 返回 `result=true`。因此 serviceAddress 是服务活跃状态的关键输入，不能视为与发布链路无关。
- **应用正向对照**：在同一手工注册有效窗口内，真实 `POST /api/tenders` 创建标讯 `4172`；`TenderEventSdkProducer` 返回 `result=true`，流水 `1882` 为 `SENT`。其 `traceId=t8k9399gtu15a4mybv`、`spanId=sbbh1mkc5ch3vzc2tr` 均为 18 位，`parentId=0`，证明“应用 → SDK → 网关受理”已走通。
- **已排除项**：本轮 traceId/spanId 均不超过 18 位，不能再把这两次失败归因于链路 ID 长度。
- **完整根因**：一是旧实现宣告 Java 监听端口 `18080`，事件库跨网段无法直达，而应宣告事件库可达的 Nginx 对外代理端口 `8080`；二是客户正式续约参数为 `initialDelay=3` 秒、`period=3` 秒、`renewalDuration=3000` 毫秒，旧实现使用 10 秒、30 秒、60，并把 `renewalDuration` 解释为秒。SDK 实际只对前两项使用 `TimeUnit.SECONDS`，第三项原样写入续约请求，因此旧的“60 秒有效期”解释错误，错误地址与错误续约参数共同导致服务不能稳定在线，单改续约单位不足以解决问题。
- **修复口径**：advertised host/port 使用标讯专用配置，缺省回退组织事件 SDK 配置；注册后 3 秒首次续约，此后每 3 秒续约一次，请求携带 `renewalDuration=3000`。常量名必须显式标明 seconds/millis，禁止把三个参数视为同一单位。
- **证据边界**：`4172` 的 `result=true` 与 `SENT` 只证明手工注册有效时应用经 SDK 获得网关受理；目前不能据此声称 CRM 已消费。部署后仍须用同一 trace 核对持续 renew、事件库入库、Kafka 发布及 CRM 消费。


### 13. 注册必须等待 ApplicationReadyEvent（2026-09-10，PR !2601 部署验证）

- **部署版本**：`452dae98b`（PR !2601 合并版本）。
- **启动时序证据**：07:03:05.096 开始注册，07:03:05.478 SDK 已认为注册流程成功；
  `Started XiyuBidApplication` 到 07:03:05.965 才出现。
- **同步回调证据**：EventBus 在注册过程中回调 advertised 地址的
  `POST /api/task/startTask`；Nginx 在 07:03:05 返回 502，因为 Java `18080` 尚未监听。
- **状态后果**：EventBus 注册接口仍返回 `code=00000`，SDK 因此启动 renew；但该实例保持
  `DOWN/WAIT`，renew 成功响应不能把无效实例激活。
- **对照实验**：应用就绪后只手工 register 一次，实例立即变成 `UP/ACTIVE`；停止手工续约后
  观察 24 秒，SDK 的 `lastRenewalTime` 仍约每 3 秒更新。这证明 !2601 的 advertised 地址和
  3/3/3000 续约参数正确，剩余根因只有注册早于 Web 服务就绪。
- **代码约束**：`TenderEventRegistrar` 监听 `ApplicationReadyEvent`，使用
  `@Order(Ordered.LOWEST_PRECEDENCE)` 后再调用 SDK 注册；`TenderEventSdkConfig` 禁止使用
  `initMethod="register"`。
- **回归测试**：Spring Context 创建后 300 ms 内不得出现 `/eventbus/register`；发布
  `ApplicationReadyEvent` 后才允许依次出现 `/eventbus/register` 与 `/eventbus/renew`。
  临时恢复旧 `initMethod` 时该测试会稳定失败并捕获提前注册。
- **验收边界**：重新部署后不得手工 register；需确认回调无 502、`bid:8080` 持续
  `UP/ACTIVE`、约每 3 秒续约，再以新 tenderId 核对 SDK、事件流水、事件库、Kafka 与 CRM。

### 14. 全量字段必须保留空值键（2026-09-10，tenderId=4173）

- **客户反馈**：真实联调事件 `traceId=tb7u2qaaihsgx0u7s8`、`tenderId=4173`、`topic=BidTenderChange` 已返回 `result=true` 并进入事件库，但 `data` 只包含 `tenderId`、`externalId`、`title` 三个非空字段。
- **补充契约**：20 个标讯字段必须保持固定结构；除必填数字 `tenderId` 外，其余字段无值时传空字符串，不能省略 key。
- **根因**：`TenderEventPayloadMapper.toData` 使用 `putIfPresent`/`putDateTimeIfPresent` 主动省略 null 和空白字段。SDK 与事件库链路工作正常，问题发生在 SDK 调用前的业务 data 映射层。
- **修复**：映射层固定写入 20 个字段，字符串/枚举/时间的 null 或空白值统一为 `""`；流水 JSON 与 SDK `eventContent` 继续共用该映射结果。
- **回归证据**：用 4173 的三字段样本断言输出 Map 恰好 20 个 key，剩余 17 个字段均存在且为空字符串；SDK 生产器测试同时断言传给 `sendEvent` 的 JSON 满足相同结构。
