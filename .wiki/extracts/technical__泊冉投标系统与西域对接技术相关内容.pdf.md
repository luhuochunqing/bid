# Extract: 泊冉投标系统与西域对接技术相关内容.pdf

- source_path: .wiki/sources/technical/泊冉投标系统与西域对接技术相关内容.pdf
- source_type: pdf
- topic: technical
- generated_at: 2026-06-21T13:44:57.663Z
- status: extracted
- confidence: 0.6
泊冉投标系统与西域对接技术相关内容
组织架构

组织架构事件订阅与接口同步接入文档
版本：V1.0

接入方式：事件库 SDK 订阅 + 组织架构接口查询

适用对象：客户投标系统建设供应商 / 西域对接支持团队
文档定位：作为供应商开发、联调、验收的统一接入说明

版本记录
版本

日期

V1.0

2026-04-30

修订说明

修订人

初版：明确客户投标系统通过事件库 SDK 订阅组织架构变更事件，并通过唯一标识
调用组织架构接口获取最新数据。

西域项目组

1. 文档目的
本文档用于指导客户投标系统建设供应商完成组织架构数据接入开发。接入目标不是直接消费事件
消息中的完整组织数据，而是通过事件库 SDK 订阅变更通知，解析消息中的唯一标识，再调用西
域组织架构接口获取最新、完整、可信的组织架构数据，并更新客户投标系统本地数据。
·明确客户投标系统需要订阅的事件 Topic、消息结构、字段含义与处理方式。
·明确事件订阅、组织架构接口调用、本地落库、异常重试、日志监控、联调验收等开发要求。
·统一供应商开发口径，避免直接依赖事件消息作为主数据、避免重复消费或漏消费导致本地组织数据不
一致。

2. 接入范围与总体原则
2.1 本次接入范围
数据类别

事 件 To p i c

部门信息

BaseOssDept

员工信息

BaseOssUser

事件含义
部门基础信息发生新增、修改、启停等变
更时产生事件
员工基础信息发生新增、修改、离职、启
停等变更时产生事件

关键唯一标识
deptId / key

userId / key

后续动作
调用 “根据部门编码获取部门数据 ”接口
获取最新部门信息
调用 “根据员工 ID 获取员工数据 ”接口获
取最新员工信息

2.2 接入原则
·事件消息只作为“变更通知”和“触发器”，不得将事件中的 data 字段直接当作最终组织架构主数据使用。
·客户投标系统应以组织架构接口返回结果为最终数据源，按 userId、deptId 等业务唯一标识进行本地
upsert。
·客户投标系统必须具备幂等处理能力。相同事件重复投递、同一对象短时间多次变更，不应造成重复数
据或脏数据。
·客户投标系统不应依赖事件的全局顺序。对于同一 userId 或 deptId 的最终状态，以组织架构接口查询
结果为准。
·上线前必须完成一次基础数据初始化；上线后通过事件订阅增量同步，并建议保留定时全量或时间窗对
账机制。

3. 总体架构与处理流程
3.1 总体架构
西域组织架构系统
│ 组织/员工数据发生变更
▼
西域事件库 / Event Bus
│ 发布 BaseOssDept、BaseOssUser 事件
▼
客户投标系统（通过 SDK 订阅）
│ 解析 eventTopic、key、data.userId / data.deptId
▼
调用西域组织架构接口
│ 获取最新部门/员工/任职等明细数据
▼
客户投标系统本地组织架构库
│ upsert、失效处理、审计记录、对账
▼
投标业务模块使用本地组织架构数据

3.2 标准处理流程
1.客户投标系统启动后，SDK 根据配置完成服务注册、续约与事件消费初始化。
2.事件库向客户投标系统推送 BaseOssDept 或 BaseOssUser 事件。
3.客户投标系统接收 eventMessage 字符串，并解析为 JSON。
4.根据 eventTopic 判断事件类型：部门变更取 data.deptId；员工变更取 data.userId。
5.以 traceId、spanId、eventTopic、key、time 等记录事件接收日志和事件流水。

6.调用对应组织架构接口获取最新数据。
7.接口返回成功后，按业务主键 upsert 到本地表；如接口返回对象不存在或状态失效，应按西域接口字
段语义进行禁用/失效处理。
8.处理成功后返回 EventResult code=200；处理失败且需要事件库重试时返回 code=500，并记录失败
原因。

4. SDK 接入说明（Java）
4.1 SDK 依赖
供应商项目需引入西域事件库 ClientSDK 。版本以西域最终提供为准，当前参考版本为
release_0.0.1。
<dependency>
<groupId>com.ehsy.eventlibrary</groupId>
<artifactId>ClientSDK</artifactId>
<version>${eventlibrary.version}</version>
</dependency>
<properties>
<eventlibrary.version>release_0.0.2</eventlibrary.version>
</properties>

4.2 application.yml 参考配置
以下为测试环境参考配置，生产环境由西域另行提供。供应商不得将测试地址、测试
consumerGroup 或测试账号直接用于生产。
# ---------------- 事件库 SDK 配置：示例 ---------------client:
register:
serviceName: BidSystemOrgConsumer # 客户投标系统服务名，生产需按西域命名规范确认
serverRegisterUrl: http://event-busserver-test.ehsy.com
enableRegister: true
renewal:
initialDelay: 3

# 服务续约初始延迟

period: 3

# 服务续约周期

renewalDuration: 3000 # 服务续约时长，单位毫秒

# 如 SDK 版本要求显式配置 broker，则按西域提供值配置
broker:
configure:
serverList: kafka-01.test.ehsy.com:9094,kafka-02.stag.ehsy.com:9094,kafka-03.stag.ehsy.com:9094
zkServers: zookeeper-01.test.ehsy.com:2183,zookeeper-02.test.ehsy.com:2183,zookeeper-03.test.ehsy.com:2183
env: test

4.3 订阅关系配置

·由西域事件库管理端创建或确认客户投标系统的消费者订阅。
·本次需订阅 Topic：BaseOssDept、BaseOssUser。
·consumerGroup 建议使用独立命名，例如：bid-org-consumer-test、bid-org-consumer-prod。
·同一系统多实例部署时可以共用同一个 consumerGroup，由 SDK/Kafka 机制分配消费；不同系统不得
共用同一个 consumerGroup。
·订阅配置完成后，供应商需在测试环境完成至少一次部门事件、一次员工事件的端到端联调。

5. 事件消息规范
5.1 公共消息结构
字段

类型

是否必需

说明

traceId

String

是

事件链路追踪 ID ，需记录到日志和事件流水表，用于问题定位。

spanId

String

是

事件链路 spanId ，需记录。

parentId

String

否

父级链路 ID ，需记录。

eventSource

String

是

事件来源系统；组织架构事件当前示例为 oss 。

eventTopic

String

是

事件主题。本次范围为 BaseOssDept、 BaseOssUser。

time

Long

是

事件产生时间，毫秒时间戳。

key

String

是

事件业务 key 。部门事件一般为 deptId ；员工事件一般为 userId 。

data

Object

是

事件数据载体，仅包含用于后续查询的关键标识，不是完整业务主数据。

5.2 部门变更事件：BaseOssDept
字段路径

示例值

说明

处理要求

eventTopic

BaseOssDept

部门变更事件 Topic

用于路由到部门同步处理器。

key

3730158

事件业务 key ，通常与 deptId 一致

记录日志；可作为兜底业务键。

data.deptId

3730158

部门编码 / 部门唯一标识

必须使用该值调用部门接口获取最新部门数据。

3600

部门记录内部 ID 或事件相关 ID

DATA ID: O...

{
"traceId": "t509415008096264192",
"spanId": "s509415010981044224",
"data": {
"deptId": 3730158,
"id": 3600
},
"eventSource": "oss",
"eventTopic": "BaseOssDept",
"time": 1730884403101,
"parentId": "s509415008096264193",
"key": "3730158"
}

仅记录，除非接口文档明确要求，否则不作为主业务
键。

5.3 员工变更事件：BaseOssUser
字段路径

示例值

说明

处理要求

eventTopic

BaseOssUser

员工信息变更事件 Topic

用于路由到员工同步处理器。

key

720518523

事件业务 key ，通常与 userId 一致

记录日志；可作为兜底业务键。

data.userId

720518523

员工唯一标识

必须使用该值调用员工接口获取最新员工数据。

533032

员工记录内部 ID 或事件相关 ID

DATA ID: O...

仅记录，除非接口文档明确要求，否则不作为主业务
键。

{
"traceId": "t704982503755501568",
"spanId": "s704982511551434752",
"data": {
"id": 533032,
"userId": 720518523
},
"eventSource": "oss",
"eventTopic": "BaseOssUser",
"time": 1777511328702,
"parentId": "s704982503755501569",
"key": "720518523"
}

6. 订阅处理实现规范
6.1 Java 订阅方法示例
SDK 通过 @AcceptEvent 注解标识订阅处理方法。方法入参为 eventMessage 字符串，返回值应
继承或符合 EventResult 结构。以下代码为示例，实际包名、 DTO 、日志工具以供应商项目为准。
@Data
public class EventInfoRespDto extends EventResult {
private String dataContent;
}
@Slf4j
@Service
public class OrgEventConsumer {
@AcceptEvent(eventTopic = "BaseOssDept", consumerGroup = "bid-org-consumer-test")
public EventInfoRespDto onDeptChanged(String eventMessage) {
return handleOrgEvent("BaseOssDept", eventMessage);
}
@AcceptEvent(eventTopic = "BaseOssUser", consumerGroup = "bid-org-consumer-test")
public EventInfoRespDto onUserChanged(String eventMessage) {
return handleOrgEvent("BaseOssUser", eventMessage);
}

private EventInfoRespDto handleOrgEvent(String expectedTopic, String eventMessage) {
EventInfoRespDto resp = new EventInfoRespDto();
try {
// 1. 解析 JSON
OrgEventMessage msg = JSON.parseObject(eventMessage, OrgEventMessage.class);
// 2. 校验 eventTopic、data、key 等必需字段
// 3. 写入本地事件流水表，按 traceId + spanId + eventTopic 做幂等
// 4. 根据 topic 调用组织架构接口
//

BaseOssDept -> data.deptId -> 根据部门编码获取部门数据

//

BaseOssUser -> data.userId -> 根据员工 ID 获取员工数据

// 5. 接口返回成功后 upsert 本地组织架构表
resp.setCode("200");
resp.setMsg("success");
return resp;
} catch (Exception e) {
log.error("组织架构事件处理失败，topic={}, eventMessage={}", expectedTopic, eventMessage, e);
resp.setCode("500");
resp.setMsg(e.getMessage());
return resp;
}
}
}

6.2 返回值规范
处理结果

返回 c o d e

返回 m s g

成功

200

success

失败

500

具体错误信息

说明
事件解析、组织架构接口查询、本地落库全部成功，或事件已幂等处理过且无需重复
处理。
事件解析失败、接口调用失败、落库失败等需要重试的场景。

·不得在未完成持久化或未写入可靠本地队列的情况下直接返回 200。
·如采用“先入本地事件流水表，再异步处理”的模式，只有在事件已可靠落地后才允许返回 200。
·如接口短暂不可用、数据库异常、网络超时，应返回 500 或进入本地可靠重试机制，避免事件丢失。

7. 组织架构接口调用策略
7.1 本次事件对应接口

件 To p i c

取值字段

推荐调用接口

aseOssDept

data.deptId

根据部门编码获取部门数据

aseOssUser

data.userId

根据员工 ID 获取员工数据

YA PI 地址

https://yapi.ehsy.com/project/40...
https://yapi.ehsy.com/project/40...

用途

按 deptId 获取最新部门数据。

按 userId 获取最新员工基础数
据。

7.2 可选扩展接口

接口名称

YA PI 地址

使用场景

根据时间窗口分页获取员工信息列表

https://yapi.ehsy.com/project/406/int...

用于初始化员工数据、时间窗口增量补偿、定时对账。

根据时间窗口分页获取部门信息列表

https://yapi.ehsy.com/project/406/int...

用于初始化部门数据、时间窗口增量补偿、定时对账。

获取所有职位信息列表

https://yapi.ehsy.com/project/406/int...

客户投标系统需要职位字典时使用。

批量根据员工工号获取所属部门信息

https://yapi.ehsy.com/project/406/int...

批量补齐员工所属部门信息。

批量根据员工工号获取所负责部门列表

https://yapi.ehsy.com/project/406/int...

业务需要员工负责部门范围时使用。

批量根据部门编码获取部门组织树信息

https://yapi.ehsy.com/project/406/int...

业务需要部门树、父子级路径或部门子树时使用。

根据职位 ID 获取职位信息数据

https://yapi.ehsy.com/project/406/int...

员工任职记录中需要补齐职位详情时使用。

根据用户 id 获取员工信息数据

https://yapi.ehsy.com/project/406/int...

需要获取员工任职记录时使用；字段 id 与 userId 的使用需以
接口文档为准。

7.3 调用要求
·组织架构接口请求方式、入参字段、鉴权方式、返回字段以 YAPI 最新文档为准。供应商开发前需完成
字段级映射确认。
·接口调用必须传递链路追踪信息。若网关或接口规范要求 Header，例如 EHSY-TraceID、
EHSY-SRCAPP，应使用事件中的 traceId 透传或生成新的调用 traceId。
·接口调用超时时间建议设置为 3-5 秒，失败后进入指数退避重试，避免事件堆积时对组织架构服务造成
放大冲击。
·对于同一个 userId 或 deptId 在短时间内多次变更的情况，可在本地队列层做合并处理，但最终必须至
少查询一次组织架构接口以获取最新状态。
·当接口返回未查询到数据时，不得直接物理删除本地数据；应结合接口返回码、状态字段和西域确认口
径，做禁用、离职、失效或待确认处理。

7.4 状态处理建议
场景

推荐处理

新增或修改

按接口返回结果 upsert 本地数据。

员工离职 /禁用

优先根据接口状态字段更新为离职、禁用或不可用，保留历史引用关系，不建议物理删除。

部门禁用 /撤销

更新部门状态；若业务仍有历史投标单据引用，应保留部门名称快照或历史映射。

接口暂时无数据

记录待确认状态并进入补偿任务；不要立即删除。

重复事件

事件流水命中幂等键时直接返回成功，避免重复调用或重复落库。

8. 初始化、补偿与对账机制
8.1 初始化

事件订阅只覆盖接入之后的变更，不负责补齐历史全量数据。供应商上线前必须通过组织架构接口
完成一次基础数据初始化。
9.调用“根据时间窗口分页获取部门信息列表”完成部门基础数据初始化。
10.调用“根据时间窗口分页获取员工信息列表”完成员工基础数据初始化。
11.如业务需要职位、任职、负责部门、部门树，则同步调用对应扩展接口完成数据补齐。
12.初始化完成后，记录初始化时间点，并开启事件订阅增量同步。

8.2 补偿与对账
·建议每天低峰期按时间窗口拉取最近 1-3 天变更数据，与本地数据进行对账。
·对于事件消费失败、接口调用失败、落库失败的数据，应由补偿任务按 retry_count、next_retry_time
进行重试。
·对于长期失败事件，应进入人工处理队列，并提供 traceId、eventTopic、key、错误信息和最近一次失
败时间。
·客户投标系统应提供手工按 userId / deptId 重新同步的运维入口，便于问题定位与数据修复。

9. 幂等、顺序与并发要求
要求

说明
事件可能被重复投递。供应商必须设计事件流水表，基于 traceId + spanId + eventTopic 或 eventTopic + key +

幂等消费

time 做幂等判断。

业务主键 upsert

部门按 deptId upsert，员工按 userId upsert。不得使用自增 ID 作为跨系统主键。

不依赖全局顺序

不同对象之间无全局顺序要求。同一对象多次变更时，以接口最新查询结果覆盖本地旧数据。

并发控制

同一 userId / deptId 建议串行或加分布式锁处理，避免并发写入覆盖。

削峰处理

大批量组织调整时应使用本地队列限流，避免瞬时大量调用组织架构接口。

10. 异常处理与重试策略
异常类型

示例

处理要求

eventMessage 不是合法 JSON 、缺少
消息格式异常

eventTopic 、缺少 data.userId /

记录失败事件和原始报文；返回 500 或进入人工处理，避免静默丢弃。

data.deptId
未知 Topic

收到非 BaseOssDept / BaseOssUser

记录告警；如未在接入范围内，不处理业务数据。

组织架构接口失败

超时、 5xx 、网络不可达

按指数退避重试；达到阈值后告警。

参数错误、无权限、数据不存在

记录接口返回码和返回报文；需按西域确认口径处理。

本地数据库失败

连接失败、死锁、唯一键冲突

事务回滚，返回失败或进入可靠重试。

重复事件

同一 traceId/spanId 被重复投递

命中幂等后直接返回成功，不重复落库。

组织架构接口返回业务失
败

11. 日志、监控与告警
11.1 日志字段要求
·必须记录：eventTopic、key、traceId、spanId、parentId、eventTime、consumerGroup、处理结果、
耗时、错误原因。
·调用组织架构接口时必须记录：接口名称、请求唯一标识、入参 userId / deptId、返回码、耗时。
·日志中不得明文输出不必要的个人敏感信息；如需输出手机号、邮箱等，应做脱敏。

11.2 监控指标要求
指标

建 议 阈 值 /说 明

事件消费成功率

持续低于 99% 应告警。

事件处理延迟

从 event.time 到本地落库完成的耗时，需按 P95/P99 监控。

失败事件积压量

失败数量超过阈值或超过 30 分钟未恢复应告警。

组织架构接口调用成功率

5xx、超时、业务失败需拆分统计。

本地补偿任务成功率

长期失败应进入人工处理。

12. 安全与合规要求
·网络访问应采用白名单原则，仅开放客户投标系统到西域事件库、组织架构接口所需域名、IP 和端
口。
·接口鉴权方式、Header、签名或 token 机制以西域正式接口规范为准，供应商不得绕过鉴权。
·客户投标系统只应同步投标业务所需的最小字段，不应全量保存无关个人信息。
·本地数据库、日志、备份中的员工信息应按公司数据安全要求控制访问权限。
·生产环境配置、密钥、地址不得提交到代码仓库；应通过配置中心、环境变量或密钥管理系统管理。
·供应商不得将西域内网地址、SDK 包、接口文档、测试数据提供给未授权第三方。

13. 联调测试与验收标准
13.1 联调用例
用例编号

场景

操作

预期结果

TC-01

SDK 启动注册

启动客户投标系统订阅服务

服务注册成功，订阅处理器正常加载，无异常日志。

TC-02

部门变更事件

西域触发 BaseOssDept 测试事件

TC-03

员工变更事件

西域触发 BaseOssUser 测试事件

TC-04

重复事件

重复投递同一事件

系统命中幂等，不重复产生脏数据，返回成功。

TC-05

组织架构接口超时

模拟接口超时或 5xx

事件失败可重试，失败流水、告警、补偿任务正常。

TC-06

字段缺失

模拟缺失 data.userId 或 data.deptId

系统记录错误，不产生错误业务数据。

系统解析 deptId，调用部门接口，本地部门数据更新，事件流
水成功。
系统解析 userId，调用员工接口，本地员工数据更新，事件流
水成功。

TC-07

初始化数据

执行初始化任务

部门、员工基础数据完整同步，数量与西域接口口径一致。

TC-08

对账补偿

执行最近 N 天时间窗口对账

能发现差异并完成补偿更新。

13.2 验收标准
·客户投标系统能够稳定订阅 BaseOssDept、BaseOssUser 两类事件。
·事件触发后，系统能够在约定时间内完成组织架构接口查询与本地数据更新。
·重复事件、乱序事件、接口失败、数据库失败均有明确处理策略和可观测日志。
·具备初始化、补偿、对账能力

OA流程创建

客户投标系统 OA 流程接入文档
适用：投标系统通过西域 backend 服务创建 OA 流程
版本：v1.0

日期：2026-04-30

测 试 域 名 ： b a c k e n d - te s t .e h s y .c o m

文档控制
版本

日期

v1.0

2026-04-30

说明
依据 OAWorkflowController、OAWorkflowService、
WorkflowRequestVO 代码整理客户投标系统接入规范

编写/维护

西域智慧供应链

1. 接入目标与范围
客户投标系统建设供应商需通过西域 backend 服务统一创建 OA 流程，不直接对接 OA 原生接
口。投标系统按本文约定提交统一 JSON 请求，西域 backend 根据 workflowId 查询 MySQL 字
段映射配置，将投标系统源字段转换为 OA 表单字段，并调用 OA 创建流程接口生成 OA
requestId。

·接入方式：HTTP JSON 调用西域 backend 服务。
·核心接口：POST /oaWorkflow/createWorkflow。
·字段映射：西域同事进行配置映射
·申请人识别：请求体 userNo 为西域员工号 / OA 登录账号，服务端据此查询 OA 用户、部门、费用承
担中心等信息。
·流程创建结果：返回 OA requestId 和 oaWorkflowFormUrl，投标系统需保存与自身业务单号的关
联。
序号

流程名称

workflowId

1

西域集团-公章盖章申请单

2

报价章盖章申请单

3

印章及证件借用申请单

4

西域-投标资料包申请流程

5

一般付款流程

6

费用报销流程

7

借款与保证金申请流程

8

借款与保证金核销流程

由西域提供/配置后回
填
由西域提供/配置后回
填
由西域提供/配置后回
填
由西域提供/配置后回
填
由西域提供/配置后回
填
由西域提供/配置后回
填
由西域提供/配置后回
填
由西域提供/配置后回
填

主表数据
mainData

mainData

mainData

mainData

mainData

mainData

mainData

mainData

明细数据

说明

按流程需要使用 detailData /

投标系统传源字段 key，西域配置映

detailData2

射到 OA 字段

按流程需要使用 detailData /

投标系统传源字段 key，西域配置映

detailData2

射到 OA 字段

按流程需要使用 detailData /

投标系统传源字段 key，西域配置映

detailData2

射到 OA 字段

按流程需要使用 detailData /

投标系统传源字段 key，西域配置映

detailData2

射到 OA 字段

按流程需要使用 detailData /

投标系统传源字段 key，西域配置映

detailData2

射到 OA 字段

按流程需要使用 detailData /

投标系统传源字段 key，西域配置映

detailData2

射到 OA 字段

按流程需要使用 detailData /

投标系统传源字段 key，西域配置映

detailData2

射到 OA 字段

按流程需要使用 detailData /

投标系统传源字段 key，西域配置映

detailData2

射到 OA 字段

2. 总体架构与处理链路
1.投标系统根据业务动作组装统一请求体，包括 userNo、workflowId、mainData、detailData、
detailData2 等。
2.投标系统调用 https://backend-test.ehsy.com/oaWorkflow/createWorkflow。
3.西域 backend 根据 workflowId 查询 MySQL 配置表 WorkflowOaConfig，获取 mainData、
detailData、detailData2 字段映射配置。
4.西域 backend 调用 getUserInfo(userNo) 获取申请人 OA 用户信息、部门信息、费用承担中心信
息，并调用 applyToken 获取 OA token 与加密 userId。
5.西域 backend 将源字段转换成 OA 字段，组装 OA doCreateRequest 请求，并提交至 OA。
6.OA 创建成功后返回 requestId；西域 backend 返回 requestId 与 OA 表单链接

oaWorkflowFormUrl。
7.投标系统保存 requestId 与自身业务单号的映射，用于后续状态跟踪、页面跳转、问题排查和重复提
交控制。

3. 接口清单
接口

方法

用途

是否必须

/oaWorkflow/createWorkflow

POST

创建 OA 流程。投标系统主要调用此接口。

必须

4. 创建 OA 流程接口规范
4.1 请求地址
POST https://backend-test.ehsy.com/oaWorkflow/createWorkflow
Content-Type: application/json; charset=UTF-8

说明：生产环境域名、网关鉴权方式、IP 白名单、额外 Header 由西域在上线前统一提供；供应商不得
直连 OA 原生接口，不需要持有 OA appid、secret、token、加密 userId 等敏感信息。

4.2 请求体字段

字段

类型

必填

说明

建 议 /示 例

userNo

String

是

西域员工号 / OA 登录账号。服务端按 loginId 查询 OA 用户信息。

zhangsan

workflowId

String

是

OA 流程 ID，也是 MySQL 字段映射配置的查询键。

由西域为每个流程提供

encodingConversion

Integer

否

是否对创建 OA 请求参数进行转义：0 不转义，1 转义；默认 0。

sysCode

String

建议

来源系统编码，便于日志、审计和后续扩展。

callbackUrl

String

可选

投标系统回调地址。如回调地址固定，也可由西域在流程配置表维护。

mainData

Object

是

detailData

Array<Object>

否

明细表 1 数据。适用于资料清单、付款明细、费用明细、借用明细等。

[{"itemName":"投标文件"}]

detailData2

Array<Object>

否

明细表 2 数据。仅流程存在第二明细表时使用。

[] 或不传

attachmentList

Array<Object>

谨慎使用

代码模型存在该字段，但当前创建流程逻辑中附件生成代码未启用。附

建议先用 mainData/detailData 传附件

件方案需单独确认。

名称和 URL

主表业务数据。key 使用投标系统源字段名；服务端根据配置映射到
OA 主表字段。

默认 0；中文或特殊字符异常时联调使
用1
BID 或 TENDER

https://bid.example.c...
{"sourceBillNo":"BID20260430001"}

重要约束：当前代码中 WorkflowRequestVO 定义了 attachmentList。涉及投标资料包、盖章材料、
付款/报销凭证等附件时，上传西域FTP，以URL链接传递。

4.3 标准请求示例
{

"userNo": "10000",
"workflowId": "<由西域提供>",
"encodingConversion": 0,
"sysCode": "BID",
"callbackUrl": "https://bid-system.example.com/api/oa/callback",
"mainData": {
"sourceBillNo": "BID202604300001",
"applyTitle": "某客户投标项目公章盖章申请",
"projectName": "某客户 2026 年 MRO 采购投标项目",
"customerName": "某某客户有限公司",
"applyReason": "投标文件盖章",
"requiredDate": "2026-05-06",
"remark": "请按投标截止时间前完成"
},
"detailData": [
{
"itemName": "投标文件正本",
"itemType": "投标资料",
"quantity": 1,
"fileName": "投标文件正本.pdf",
"fileUrl": "https://.../files/tender-main.pdf",
"remark": "需加盖公章"
}
],
"detailData2": []
}

4.4 返回结果
// 成功时 data 中至少包含 requestId 与 oaWorkflowFormUrl。
{
"code": 0,
"msg": "success",
"data": {
"requestId": "123456",
"oaWorkflowFormUrl": "https://<OA域
名>/spa/workflow/static4form/index.html?#/main/workflow/req?requestid=123456"
}
}

// 失败时，后端返回错误码和错误信息；错误信息可能来自 OA 原生返回或后端校验异常。

{
"code": 1,
"msg": "创建OA流程失败 / 找不到对应的员工信息！ / OA返回错误内容"
}

注意：具体 code/msg/data 外层结构以西域 RUtil 统一返回格式为准；供应商开发时应以联调实际报
文为最终解析依据，但必须解析并保存 data.requestId、data.oaWorkflowFormUrl。

5. 字段映射配置机制
5.1 设计原则
·投标系统只传业务源字段，不传 OA 内部字段名。
·西域在 MySQL 配置表中按 workflowId 维护字段映射，主要包括 mainData、detailData、
detailData2 三类配置。
·创建流程时，backend 先读取配置，再调用 OARequestVOGenerator 将源字段转换成 OA 表单字
段。
·同一套 createWorkflow 接口可承载多个 OA 流程；新增或调整 OA 字段时，优先通过配置表维护映
射，减少投标系统改造。
配置项

含义

维护方

供应商关注点

workflowId

OA 流程 ID；也是查询字段映射配置的唯一键。

西域

请求必须传正确 workflowId。

mainData

主表字段映射配置。

西域

投标系统 mainData 必须使用约定 sourceKey。

detailData

第一明细表字段映射配置。

西域

有明细表时传数组；无明细时传 [] 或不传。

detailData2

第二明细表字段映射配置。

西域

仅流程存在第二明细表时使用。

oaResultNoticeUrl

OA 结果回传到业务系统的地址配置。

西域/供应商共同确认

供应商需提供可访问的回调 URL，并返回约定成功
码。

5.2 源字段传输规范
规则

字段命名

空值处理

要求
建议使用英文驼峰或下划线，保持稳定，例如 sourceBillNo、projectName、customerName、
applyAmount。字段 key 一旦进入配置表，不得随意变更。
非必填字段可不传或传空字符串；明细无数据时传 []。不得传不符合类型的字符串，例如金额字段不要传“壹
万元”。

日期格式

日期使用 yyyy-MM-dd；时间使用 yyyy-MM-dd HH:mm:ss。

金额格式

使用数字或可解析的数字字符串，建议保留 2 位小数，例如 12500.00。

枚举格式

枚举值需使用双方约定编码，如 stampType=公章/报价章，applyType=借款/保证金。不要使用页面展示文案
之外的临时值。

建议传 fileName、fileUrl、fileType、fileSize、sourceFileId；最终是否进入 OA 附件区或表单字段由西域确

附件字段

认。

敏感信息

银行账号、证件号码、客户联系人等敏感数据只传流程必要字段，日志展示需脱敏。

6. 状态回传与回调规范
如投标系统需要接收 OA 流程状态或合同/文件归档结果，应向西域提供可公网或专线访问的回调
URL，由西域在对应 workflowId 的配置中维护。OA 消息进入西域
/oaWorkflow/commonCallback 后，西域根据配置转发给业务系统。
字段

类型

必填

说明

workflowId

String

是

OA 流程 ID

flowCode

String

是

OA 流程编号/业务流程编号

todoId

String

是

OA 待办 ID

status

String

是

状态枚举：REJECT、ARCHIVE、SUPPLIER_SIGNING

rejectOpinion

String

REJECT 时建议

驳回意见；西域转发前会去除 HTML 标签

contractFileId

String

ARCHIVE 时必填

OA 文档 ID，多个可逗号分隔；西域最多转换 3 个文档链接

contractFiles

Array<Object>

西域补充

根据 contractFileId 解析得到的文件名和文件 URL

// 投标系统建议实现的回调接收示例
POST https://bid-system.example.com/api/oa/callback
Content-Type: application/json

{
"workflowId": "<流程ID>",
"flowCode": "<流程编号>",
"todoId": "<待办ID>",
"status": "ARCHIVE",
"contractFileId": "123,456",
"contractFiles": [
{"fileName":"归档文件.pdf", "fileUrl":"https://.../archive.pdf"}
]
}

// 建议返回
{
"code": "SUCCESS",
"msg": "success"

}

7. 幂等、重试与数据一致性要求
·投标系统同一业务单据不要重复发起多个 OA 流程。
·如调用超时但不确定是否创建成功，不得盲目重复提交，应先检查本地是否已保存 requestId，必要时
联系西域按日志排查。
·成功返回后，投标系统需保存：sourceBillNo、workflowId、requestId、oaWorkflowFormUrl、创
建时间、创建人 userNo、请求摘要。
·如同一业务场景确需重新发起 OA，应生成新的 sourceBillNo 或明确标识
reapplyFromSourceBillNo，避免审批数据混淆。
·网络异常建议最多重试 1 次；重试前需确认本地没有 requestId。
·所有请求需记录 traceId/请求流水号；西域 backend 日志会记录 OA 创建参数和返回结果，便于双方
定位。

8. 校验规则与常见错误
场景

表现

处理建议

申请人不存在

返回“找不到对应的员工信息！”

检查 userNo 是否为 OA loginId / 西域员工号，先调用 getUserInfo 校验。

workflowId 配置不存在或错

创建失败，可能出现配置为空或 OA 返回错

由西域确认 workflowId 已在 MySQL 配置表维护 mainData/detailData 映

误

误

射。

字段 key 不匹配

OA 表单字段为空或创建失败

中文或特殊字符异常

OA 字段乱码或请求失败

尝试 encodingConversion=1，并提供失败样例给西域排查。

附件未进入 OA

表单无附件或文件不可访问

当前附件自动上传逻辑未启用；需确认附件传递方式和文件访问权限。

OA 原生接口失败

backend 返回 OA 错误 JSON 或错误文本

保留完整请求和 response，按 requestId/时间点由西域定位。

重复创建

同一业务出现多个 OA requestId

投标系统用 sourceBillNo 控制幂等；超时未知结果时不要直接重试。

供应商按本文 sourceKey 传值；西域检查配置表源字段 key 与请求 JSON 是
否一致。

9. 安全与合规要求
·网络访问：正式上线前由双方确认测试/生产域名、IP 白名单、DNS 解析、证书和防火墙策略。
·最小权限：投标系统只调用西域 backend，不接触 OA appid、secret、token、spk、加密 userId。
·数据最小化：仅传创建流程所需字段，证件号、银行账号、客户联系人等敏感字段按需传输并脱敏展
示。
·传输安全：生产环境必须使用 HTTPS；回调地址也应使用 HTTPS。
·日志脱敏：银行账号、身份证号、手机号、邮箱、文件下载 URL 中的 token 等敏感信息在供应商日志
中应脱敏。

·文件安全：附件 URL 应具备有效期或访问鉴权，避免长期公开可访问。

10. 联调验收清单
阶段

验收项

通过标准

准备

确认 8 个流程 workflowId

西域提供测试环境 workflowId，配置表存在记录。

准备

确认字段映射表

用户

申请人账号校验

创建

每个流程创建最小样例

返回 requestId 和 oaWorkflowFormUrl，OA 表单可打开。

字段

主表字段核对

OA 表单主表字段与投标系统提交值一致。

字段

明细字段核对

OA 明细行数、字段值、金额合计与投标系统一致。

附件

附件/文件链接验证

附件或文件链接在 OA 中可访问，权限符合要求。

异常

必填缺失/错误 userNo/错误 workflowId

返回明确错误，不产生脏流程。

幂等

重复提交验证

投标系统能阻止同一 sourceBillNo 重复创建。

回调

状态回传验证

ARCHIVE/REJECT/SUPPLIER_SIGNING 能正确更新投标系统流程状态。

上线

生产域名与白名单验证

生产网络连通，证书、域名、网关策略正常。

每个流程 mainData/detailData/detailData2 的 sourceKey 与 OA 目标字段
一一对应。
getUserInfo 能返回 userId、userName、departmentId、费用承担中心等
信息。

11. 供应商交付物要求
·接口调用代码和配置说明，包括测试/生产域名切换配置。
·8 个流程的最终请求 JSON 样例，每个流程至少提供一笔成功样例。
·字段映射确认表：sourceKey、中文名、类型、是否必填、示例值、对应业务页面字段。
·幂等控制说明：sourceBillNo 生成规则、重复提交拦截逻辑、超时处理策略。
·回调接口文档：URL、鉴权方式、请求/响应报文、失败重试策略。
·日志与监控说明：请求流水号、sourceBillNo、workflowId、requestId 的日志打印位置。
·测试报告：覆盖 8 个流程创建成功、字段核对、附件、异常、回调、幂等场景。

CRM接口
1. 接口规范
·通讯协议: HTTPS
·接口安全：使用token鉴权
· 请求方式

●G ET：从服务器获取资源，可以在URL中传递参数，用于查询数据。
●POST：向服务器提交数据，用于增删改等操作

· 编码格式：UT F-8
· 请求URL
Get:不支持参数含有特殊字符。查询操作时使用。
Post:不支持文件传输。application/json
定义：协议://域名/[服务名:可选]/[客户端类型:可选]/[版本号:可选]/路径
说明：

1. URL命名：字母驼峰
2. 协议：https
3. 域名：业务简称。运维定义。例如CAC：cac.ehsy.com
4. 服务名：网关服务需要有服务名，域名标准为主。可选。
示例：https://cac.ehsy.com/cac/api/get-info
· 请求头

●认证：Authorization（基于框架选择）
●Content-Type：application/json（post）
· 请求参数

●参数名：驼峰命名，例如userId。
●类型：传参数据类型，参照json基础数据类型。
●是否必填：定义该参数是否必填，必填情况下，接口应作必填校验，并抛出异常。
●默认值：参数非必传情况下，不传时默认的值。
●取值范围：数值或者枚举类型，需要书写该范围值。
●参数格式：如果是日期或者需要固定格式类型的参数，需标注该格式。例如：
yyyy-MM-dd。

●示例值：提供该入参示例，以便开发更好的理解和使用该。
●备注：参数说明
· 响应参数

●参数名称：描述响应参数的名称。
●参数类型：描述响应参数的数据类型。如：String，Integer。

查询存量有效客户

表查询客户负责人

取员工信息

●参数格式：描述该响应参数的数据格式。如：日期 yyyy-MM-dd。
●参数说明：对该响应参数进行详细描述。
●取值范围：对响应参数的取值范围进行定义。如整数范围，字符串长度。
●是否必填：该参数是否必填。
●示例值：提供响应参数的示例值，方便开发人员更好的理解和使用参数。
按照code，msg，data，success格式返回：

2. 接口清单
YA PI 地址

https://yapi.ehsy.com/project/406/interface/api/23352

https://yapi.ehsy.com/project/406/interface/api/23370

https://yapi.ehsy.com/project/509/interface/api/25338

使用场景

调用接口前，获取token信息，缓存到本地，设置
效期

用于登录之后用户登出

根据公司名称模糊查询符合条件的前二十条公司
公司名称长度排序，越小靠前
用于页面客户负责人列表展示

https://yapi.ehsy.com/project/509/interface/api/25259

根据公司id查询客户负责人列表（支持批量公司i
号）

https://yapi.ehsy.com/project/406/interface/api/35642

根据系统类型获取对应系统的菜单树

https://yapi.ehsy.com/project/406/interface/api/23358

根据token获取员工的用户信息

https://yapi.ehsy.com/project/557/interface/api/35649

发送消息（企微+站内信）
