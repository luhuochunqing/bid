# Handoff: 标讯创建事件推送西域 CRM 事件总线——网关受理成功但客户未收到（攻坚中）

> 提交时间：2026-08-27
> 提交人：Qoder Agent（分支 `agent/qoder/fix-tenderevent-raw-response-log`）
> 接收方：接手「标讯事件推送西域事件库」联调的 Agent / 工程师
> 状态：**未完成——网关 HTTP 受理成功（`code=00000`），但客户事件库确认多次未收到，需换人继续攻坚**

---

## 1. 需求目标（客户要的）

标讯创建后，平台向西域 CRM 事件库推送一条事件通知，**客户在事件库真实收到该事件**。

- 推送通道：`POST {serverRegisterUrl}/eventbus/publishEvent`（生产侧直连 HTTP，不经 SDK 高层封装）
- 事件类型：`BidTenderChange` / `eventSource=bid`
- 验证方式：客户在事件库管理端查询 tenderId 是否收到

**当前硬阻塞**：网关 HTTP 层始终返回受理成功（`{"code":"00000","msg":"响应成功"}`），但客户确认 **tenderId=2282 之后多次推送仍未收到**（2289/2292/2293/2294/2295/2296 均未收到或未确认收到）。

## 2. 最终 wire 契约（2026-08-27 实测锁定，与客户手动触发成功样例逐字段一致）

**HTTP body（8 字段，`data` 为 JSON 字符串）**：

```json
{
  "traceId": "e7f5856911cb4d488478333571a96e45",
  "spanId": "fb171cfc967e427dbf76b1bcde9fe949",
  "parentId": "0",
  "eventTopic": "BidTenderChange",
  "eventSource": "bid",
  "key": "tenderId",
  "time": 1787810045121,
  "data": "{\"tenderId\":2296}"
}
```

**HTTP Header**：`traceId` / `spanId` / `parentId`（与 body 双写，SDK `buildHeaderParamMap` 既有行为，无副作用）。

| 字段 | 类型 | 必需 | 说明 |
|---|---|---|---|
| traceId | String | 是 | 链路追踪 ID |
| spanId | String | 是 | spanId |
| parentId | String | 否 | 无父链路时 `"0"`（客户参考实现证实） |
| eventTopic | String | 是 | 事件主题（客户文档说明：SDK 的 eventCode 放入 body 的 eventTopic 字段） |
| eventSource | String | 是 | 事件来源系统 |
| key | String | 是 | 业务主键**字段名**（标讯=`tenderId`、部门=`deptId`、员工=`userId`），非主键值 |
| time | Long | **是** | 事件产生时间（毫秒时间戳），客户技术规格书公共消息结构标注必需 |
| data | String | 是 | **JSON 字符串**（网关 `EventContentReqDto.data` 声明为 String，传对象返回 HTTP 400） |

**成功信号**：HTTP 200 + `{"code":"00000","data":{"result":null},"msg":"响应成功"}`。

**明确不要发的字段**：`serviceName`（客户成功样例无此字段；且 SDK `ServiceRegisterProperties` 类级 `@ConfigurationProperties(prefix="client.register")` 会把环境值绑定覆盖到所有同类 bean，曾导致错误服务名上报 `result=false`）。

## 3. 排障时间线（关键 commit 链，均在分支 `agent/qoder/fix-tenderevent-raw-response-log`）

| commit | 动作 | 网关响应 | 结果 |
|---|---|---|---|
| `79e1e186d` 起 | 初版直连 HTTP + SDK 高层 sendEvent 双路径 | `result=false`（`topic未注册`） | 客户未收到 |
| `64a3fcae8` | serviceName 直取配置，规避 client.register 绑定覆盖 | `data:{}` | 客户未收到 |
| `a0f2cbf56` | body 补 `key`（业务主键字段名） | `data:{}` | 客户未收到 |
| `e341aa986` | body 完全复刻客户样例（trace 进 body + data 对象） | **response=null**（SDK httpPost 非 200 静默返回 null） | 客户未收到（复刻对象形态反而触发 400） |
| `6440b068b` | **data 回退 JSON 字符串**（网关 data 是 String，对象 400 实测） | `data:{}` | 客户未收到 |
| `35838bf28` | **body 补 `time` 字段**（文档标注必需）+ 修正 data:{} 误判 | `data:{}`（受理成功） | **待客户验证 tenderId=2296** |

**已部署到测试环境**：Release `35838bf28-api8080`（2026-08-27 13:50 部署，健康检查 UP）。

**实测证据**（tenderId=2296，2026-08-27 13:54:05）：
```
事件总线请求 body={"traceId":"e7f5856911cb4d488478333571a96e45","spanId":"fb171cfc967e427dbf76b1bcde9fe949",
  "data":"{\"tenderId\":2296}","eventSource":"bid","eventTopic":"BidTenderChange",
  "time":1787810045121,"parentId":"0","key":"tenderId"}
事件总线响应 response={"msg":"响应成功","code":"00000","data":{}}
```

## 4. 已澄清的关键技术结论（避免接手者重复踩坑）

1. **`data:{}` 不是网关返回空对象**——网关对所有合规请求均返回 `{"data":{"result":null}}`，SDK 内部用 **fastjson 1.2.83** 解析响应时**默认丢弃 null 值**，`result:null` 键被丢弃，data 变成 `{}`。本地探针实证：同一请求原始 HTTP 响应 `data:{"result":null}`，fastjson 解析后 `data:{}`。**`data:{}` 与 `result:null` 都是受理成功，`interpretResult` 判定逻辑正确。**

2. **`data` 必须是 JSON 字符串**：网关 `EventContentReqDto.data` 声明为 String，传对象返回 HTTP 400（"Can not deserialize instance of java.lang.String out of START_OBJECT token"）。

3. **缺 `time` 字段曾被认为是可选**——客户技术规格书公共消息结构明确标注 `time` 为**必需**（`time | Long | 是 | 事件产生时间（毫秒时间戳）`），客户手动触发成功样例亦含。已补齐，但**补齐后客户仍未确认收到**，说明 time 缺失不是（或不是唯一）根因。

4. **SDK 高层 `sendEvent` 判定**：`data` 空对象 `{}` → `result=null`，与 `data:{result:null}` 等价，都判成功。SDK `HttpClientUtil.httpPost` 仅状态码 200 时解析响应，非 200 / IO 异常**静默返回 null**。

5. **服务注册参数**：客户手动触发成功样例证明 `bid`/`BidTenderChange` 注册值**正确**（此前"从未在网关注册"的判断被推翻）。`enableRegister` 注册流程（`HTTP_REGISTER`/`renew`）在组织事件消费侧使用，标讯生产侧直连 HTTP 未走注册流程。

6. **Kafka 版本教训**（用户提示的历史经验）：组织事件消费侧曾因 Kafka broker 版本（客户 `0.10.1.0`）与 SDK 引入版本不匹配踩坑（`pom.xml` 注释"matches customer broker 0.10.1.0"）。但标讯推送是**生产侧直连 HTTP**，不直接连 Kafka——若怀疑投递链路，应查**网关内部到事件库 Kafka** 的环节（客户侧）。

## 5. 客户侧信息（已获取）

- **手动触发成功样例**（tenderId=2282，客户在事件库管理端手动触发，执行成功）：
  ```json
  {
    "traceId": "748104276040151040",
    "spanId": "s748104276040151041",
    "data": {"tenderId": "2282"},
    "eventSource": "bid",
    "eventTopic": "BidTenderChange",
    "time": 1787792358513,
    "parentId": "0",
    "key": "tenderId"
  }
  ```
  > 注意：样例里 data 显示为对象是**网关内部存储形态**，HTTP 层必须是字符串（第 4.2 条 400 实测）。

- **客户接口文档三篇**（已学习并沉淀到代码注释与 wiki）：消息结构 9 字段（traceId/spanId/parentId/eventCode/eventSource/eventTopic/time/key/data）、`sendEvent(eventCode, eventSource, eventContent, eventTrackReq)`、事件库举例（`EventInfoReqDTO` + `eventHandleService.sendEvent`）。

- **客户技术规格书**：`.wiki/sources/technical/西域投标系统对接技术规格书.md`（公共消息结构见 §5.1，`time` 标注必需）。

## 6. 核心未解问题（接手者的攻坚重点）

**网关 HTTP 受理成功（`code=00000`）≠ 事件进入事件库被消费。** 差异发生在网关受理之后的投递链路（网关内部 → Kafka topic → 事件库消费者）。已排除的因素：

- ❌ 不是 body 字段缺失（8 字段与客户成功样例完全一致）
- ❌ 不是 data 类型（字符串已对齐）
- ❌ 不是 traceId 格式（UUID 与数字 snowflake 均受理成功）
- ❌ 不是网关按 key 幂等（curl 重复发真实 tenderId 仍返回 `result:null`）
- ❌ 不是 HTTP 客户端差异（SDK Apache HttpClient 与 curl 响应形态差异仅为 fastjson 解析表现，均受理成功）

**待验证的方向（按优先级）**：

1. **客户事件库侧确认 tenderId=2296 是否收到**（2026-08-27 13:54 推送，body 已含 time）。若收到 → 任务完成；若未收到 → 继续下方。
2. **向客户索取事件库侧投递日志/消费日志**：网关受理后是否写入 Kafka topic、事件是否被路由到正确消费组。这是判断"网关内部投递"还是"事件库消费"哪一环断掉的关键证据。
3. **确认客户事件库是否配置了 `BidTenderChange` 的订阅关系**（类比组织事件：需客户在 event-busserver 管理端创建订阅，见 `.wiki/pages/oss-organization-sync-playbook.md` §8 与 `interface-integration-record-2026-07-09-1st-prod.md`——组织事件曾因"客户未配置订阅"而 Kafka 连通但无消息）。
4. **向客户确认 `eventSource=bid` 生产方是否需要在网关注册**（`enableRegister` + `register` 接口），以及 `time` 是否必须是"网关认可的时钟窗口"内（如 2296 的 time 为 2026-08-27 13:54，手动触发样例 time 为 1787792358513≈同日 09:59，均在当日，应无时钟问题）。

## 7. 相关文件与资源

| 资源 | 路径 |
|---|---|
| 发送器实现 | `backend/src/main/java/com/xiyu/bid/integration/tenderevent/infrastructure/sdk/TenderEventHttpSender.java`（类注释含完整排障回顾） |
| 生产器 | `backend/src/main/java/com/xiyu/bid/integration/tenderevent/infrastructure/sdk/TenderEventSdkProducer.java` |
| 编排服务 | `backend/src/main/java/com/xiyu/bid/integration/tenderevent/application/TenderEventPublishService.java` |
| 事件码 | `backend/src/main/java/com/xiyu/bid/integration/tenderevent/domain/TenderEventCode.java` |
| wire 测试 | `backend/src/test/java/com/xiyu/bid/integration/tenderevent/infrastructure/sdk/TenderEventHttpSenderWireTest.java`、`TenderEventSdkProducerTest.java` |
| SDK jar | `backend/libs/ClientSDK-release_0.0.2.jar`（可用 `javap -c` 反编译核实行为） |
| 客户技术规格书 | `.wiki/sources/technical/西域投标系统对接技术规格书.md` |
| 知识沉淀 wiki | `.wiki/pages/integration-tender-event-push.md`（§5 已标注推翻、§6 样例对比、§7 最终契约） |
| 历史教训 | `.wiki/pages/integration-organization-event-sdk.md`（组织事件对接，含订阅关系待客户配置的教训） |
| 组织事件同步手册 | `.wiki/pages/oss-organization-sync-playbook.md`（§8 Kafka 增量同步 + 订阅配置） |
| 生产接口集成记录 | `docs/release/interface-integration-record-2026-07-09-1st-prod.md` |

## 8. 测试环境信息

- 服务器：`172.16.38.78`（winbid-01.test），SSH 用户 `jetty`
- 服务：`xiyu-bid-backend`（systemd），端口 18080
- 日志：`/var/log/xiyu-bid/application.json.log`（JSON 格式，`grep "事件总线" | tail` 看推送记录）
- 当前部署：Release `35838bf28-api8080`
- 事件总线：`http://event-busserver-test.ehsy.com`（APISIX 网关，解析到 240.0.0.21）
- 数据库：`winbid-01.test.rds.ehsy.com`（本地/服务器不可直连，需跳板）
- 登录：`admin` / `XiyuAdmin2026!`（token 通过 Set-Cookie `access_token` 返回）
- 标讯创建验证：`POST /api/auth/login` 拿 cookie → `POST /api/tenders`（`{"title":"...","deadline":"2026-09-30T17:00:00"}`）→ 查日志 `事件总线请求/响应`

## 9. 交接建议

1. 先向客户确认 **tenderId=2296 是否收到**（这是 time 字段修复后的第一次完整验证）。
2. 若未收到，**立即向客户索取事件库侧投递/消费日志**——这是定位"网关受理后到事件库"断点的唯一证据，别再在 HTTP 请求层死磕（请求层已与客户成功样例完全一致）。
3. 优先排查**订阅关系**：客户是否在 event-busserver 管理端为 `BidTenderChange` 创建了消费者订阅（组织事件曾因缺订阅而"连通但无消息"）。
4. 若客户确认订阅已配置，再评估 `eventSource` 生产方注册、时钟窗口、Kafka 版本匹配（客户侧）等方向。
