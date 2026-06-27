# Extract: 泊冉投标系统与西域对接技术相关内容.md

- source_path: .wiki/sources/technical/泊冉投标系统与西域对接技术相关内容.md
- source_type: markdown
- topic: technical
- generated_at: 2026-06-27T13:34:19.545Z
- status: extracted
- confidence: 1
# 泊冉投标系统与西域对接技术相关内容

## 组织架构事件订阅与接口同步接入文档

**版本：** V1.0  
**接入方式：** 事件库 SDK 订阅 + 组织架构接口查询  
**适用对象：** 客户投标系统建设供应商 / 西域对接支持团队  
**文档定位：** 作为供应商开发、联调、验收的统一接入说明

### 版本记录

| 版本 | 日期 | 修订说明 | 修订人 |
| :--- | :--- | :--- | :--- |
| V1.0 | 2026-04-30 | 初版：明确客户投标系统通过事件库 SDK 订阅组织架构变更事件，并通过唯一标识调用组织架构接口获取最新数据。 | 西域项目组 |

---

## 1. 文档目的

本文档用于指导客户投标系统建设供应商完成组织架构数据接入开发。接入目标不是直接消费事件消息中的完整组织数据，而是通过事件库 SDK 订阅变更通知，解析消息中的唯一标识，再调用西域组织架构接口获取最新、完整、可信的组织架构数据，并更新客户投标系统本地数据。

*   明确客户投标系统需要订阅的事件 Topic、消息结构、字段含义与处理方式。
*   明确事件订阅、组织架构接口调用、本地落库、异常重试、日志监控、联调验收等开发要求。
*   统一供应商开发口径，避免直接依赖事件消息作为主数据、避免重复消费或漏消费导致本地组织数据不一致。

---

## 2. 接入范围与总体原则

### 2.1 本次接入范围

| 数据类别 | 事件 Topic | 事件含义 | 关键唯一标识 | 后续动作 |
| :--- | :--- | :--- | :--- | :--- |
| 部门信息 | BaseOssDept | 部门基础信息发生新增、修改、启停等变更时产生事件 | deptId / key | 调用“根据部门编码获取部门数据”接口获取最新部门信息 |
| 员工信息 | BaseOssUser | 员工基础信息发生新增、修改、离职、启停等变更时产生事件 | userId / key | 调用“根据员工 ID 获取员工数据”接口获取最新员工信息 |

### 2.2 接入原则

*   **事件消息只作为“变更通知”和“触发器”**：不得将事件中的 data 字段直接当作最终组织架构主数据使用。
*   **最终数据源**：客户投标系统应以组织架构接口返回结果为最终数据源，按 userId、deptId 等业务唯一标识进行本地 upsert。
*   **幂等处理**：客户投标系统必须具备幂等处理能力。相同事件重复投递、同一对象短时间多次变更，不应造成重复数据或脏数据。
*   **不依赖全局顺序**：对于同一 userId 或 deptId 的最终状态，以组织架构接口查询结果为准。
*   **初始化与对账**：上线前必须完成一次基础数据初始化；上线后通过事件订阅增量同步，并建议保留定时全量或时间窗对账机制。

---

## 3. 总体架构与处理流程

### 3.1 总体架构

1.  **西域组织架构系统**：组织/员工数据发生变更。
2.  **西域事件库 / Event Bus**：发布 BaseOssDept、BaseOssUser 事件。
3.  **客户投标系统（通过 SDK 订阅）**：解析 eventTopic、key、data.userId / data.deptId。
4.  **调用西域组织架构接口**：获取最新部门/员工/任职等明细数据。
5.  **客户投标系统本地组织架构库**：执行 upsert、失效处理、审计记录、对账。
6.  **投标业务模块**：使用本地组织架构数据。

### 3.2 标准处理流程

1.  客户投标系统启动后，SDK 根据配置完成服务注册、续约与事件消费初始化。
2.  事件库向客户投标系统推送 BaseOssDept 或 BaseOssUser 事件。
3.  客户投标系统接收 eventMessage 字符串，并解析为 JSON。
4.  根据 eventTopic 判断事件类型：部门变更取 data.deptId；员工变更取 data.userId。
5.  以 traceId、spanId、eventTopic、key、time 等记录事件接收日志和事件流水。
6.  调用对应组织架构接口获取最新数据。
7.  接口返回成功后，按业务主键 upsert 到本地表；如接口返回对象不存在或状态失效，应按西域接口字段语义进行禁用/失效处理。
8.  处理成功后返回 EventResult code=200；处理失败且需要事件库重试时返回 code=500，并记录失败原因。

---

## 4. SDK 接入说明（Java）

### 4.1 SDK 依赖

供应商项目需引入西域事件库 ClientSDK。版本以西域最终提供为准，当前参考版本为 release_0.0.1。

```xml
<dependency>
    <groupId>com.ehsy.eventlibrary</groupId>
    <artifactId>ClientSDK</artifactId>
    <version>${eventlibrary.version}</version>
</dependency>

<properties>
    <eventlibrary.version>release_0.0.2</eventlibrary.version>
</properties>
```

### 4.2 application.yml 参考配置

```yaml
# ---------------- 事件库 SDK 配置：示例 ----------------
client:
  register:
    serviceName: BidSystemOrgConsumer # 客户投标系统服务名
    serverRegisterUrl: http://event-busserver-test.ehsy.com
    enableRegister: true
  renewal:
    initialDelay: 3 # 服务续约初始延迟
    period: 3 # 服务续约周期
    renewalDuration: 3000 # 服务续约时长，单位毫秒

# 如 SDK 版本要求显式配置 broker
broker:
  configure:
    serverList: kafka-01.test.ehsy.com:9094,kafka-02.stag.ehsy.com:9094,kafka-03.stag.ehsy.com:9094
    zkServers: zookeeper-01.test.ehsy.com:2183,zookeeper-02.test.ehsy.com:2183,zookeeper-03.test.ehsy.com:2183
    env: test
```

### 4.3 订阅关系配置

*   由西域事件库管理端创建或确认客户投标系统的消费者订阅。
*   本次需订阅 Topic：BaseOssDept、BaseOssUser。
*   consumerGroup 建议：bid-org-consumer-test、bid-org-consumer-prod。

---

## 5. 事件消息规范

### 5.1 公共消息结构

| 字段 | 类型 | 必需 | 说明 |
| :--- | :--- | :--- | :--- |
| traceId | String | 是 | 事件链路追踪 ID，用于问题定位。 |
| spanId | String | 是 | 事件链路 spanId。 |
| parentId | String | 否 | 父级链路 ID。 |
| eventSource | String | 是 | 事件来源系统；当前为 oss。 |
| eventTopic | String | 是 | 事件主题。 |
| time | Long | 是 | 事件产生时间（毫秒时间戳）。 |
| key | String | 是 | 业务 key（deptId 或 userId）。 |
| data | Object | 是 | 数据载体，仅包含关键标识。 |

### 5.2 部门变更事件：BaseOssDept

| 字段路径 | 示例值 | 说明 | 处理要求 |
| :--- | :--- | :--- | :--- |
| eventTopic | BaseOssDept | 部门变更事件 Topic | 用于路由。 |
| key | 3730158 | 业务 key | 记录日志。 |
| data.deptId | 3730158 | 部门编码 | **必须**调用接口获取最新数据。 |

**示例 JSON：**
```json
{
  "traceId": "t509415008096264192",
  "spanId": "s509415010981044224",
  "data": { "deptId": 3730158, "id": 3600 },
  "eventSource": "oss",
  "eventTopic": "BaseOssDept",
  "time": 1730884403101,
  "key": "3730158"
}
```

### 5.3 员工变更事件：BaseOssUser

| 字段路径 | 示例值 | 说明 | 处理要求 |
| :--- | :--- | :--- | :--- |
| eventTopic | BaseOssUser | 员工变更事件 Topic | 用于路由。 |
| key | 720518523 | 业务 key | 记录日志。 |
| data.userId | 720518523 | 员工唯一标识 | **必须**调用接口获取最新数据。 |

---

## 6. 订阅处理实现规范

### 6.1 Java 订阅方法示例

```java
@Slf4j
@Service
public class OrgEventConsumer {
    @AcceptEvent(eventTopic = "BaseOssDept", consumerGroup = "bid-org-consumer-test")
    public EventInfoRespDto onDeptChanged(String eventMessage) {
        return handleOrgEvent("BaseOssDept", eventMessage);
    }

    private EventInfoRespDto handleOrgEvent(String expectedTopic, String eventMessage) {
        EventInfoRespDto resp = new EventInfoRespDto();
        try {
            OrgEventMessage msg = JSON.parseObject(eventMessage, OrgEventMessage.class);
            // 1. 解析 & 校验
            // 2. 幂等处理
            // 3. 调用组织架构接口获取明细
            // 4. Upsert 本地库
            resp.setCode("200");
            resp.setMsg("success");
        } catch (Exception e) {
            resp.setCode("500");
            resp.setMsg(e.getMessage());
        }
        return resp;
    }
}
```

---

## 7. 组织架构接口调用策略

### 7.1 本次事件对应接口

| 事件 Topic | 字段 | 推荐接口 | 用途 |
| :--- | :--- | :--- | :--- |
| BaseOssDept | data.deptId | 根据部门编码获取部门数据 | 获取最新部门信息 |
| BaseOssUser | data.userId | 根据员工 ID 获取员工数据 | 获取最新员工信息 |

### 7.2 可选扩展接口 (部分)

*   `根据时间窗口分页获取员工信息列表`：用于初始化/对账。
*   `根据时间窗口分页获取部门信息列表`：用于初始化/对账。
*   `获取所有职位信息列表`：职位字典。
*   `批量根据员工工号获取所属部门信息`。
*   `批量根据部门编码获取部门组织树信息`。

### 7.3 调用要求

*   **超时设置**：建议 3-5 秒，失败后进入指数退避重试。
*   **状态处理**：接口未查询到数据时，应做禁用/离职处理，不得物理删除。

---

## 8. 初始化、补偿与对账机制

*   **初始化**：上线前必须通过接口完成全量初始化。
*   **对账**：建议每天低峰期拉取最近 1-3 天数据进行对账修复。
*   **补偿**：长期失败事件应进入人工处理队列。

---

## 9. 幂等、顺序与并发要求

| 要求 | 说明 |
| :--- | :--- |
| 幂等消费 | 基于 traceId + spanId 或 Topic + key + time。 |
| 业务主键 | 部门用 deptId，员工用 userId。不得用自增 ID。 |
| 并发控制 | 同一对象建议串行或分布式锁，避免覆盖。 |

---

## 10. 异常处理与重试策略

*   **格式异常**：记录失败并进入人工处理，不直接丢弃。
*   **接口失败**：指数退避重试，达阈值告警。
*   **重复事件**：直接返回成功，不重复落库。

---

## 11. 日志与监控

*   **指标**：持续低于 99% 应告警。
*   **延迟**：监控从事件产生到落库的时间差。

---

## 12. 联调测试用例

| 编号 | 场景 | 操作 | 预期结果 |
| :--- | :--- | :--- | :--- |
| TC-01 | SDK 启动 | 启动服务 | 服务注册成功，处理器加载。 |
| TC-02 | 部门变更 | 触发事件 | 系统获取明细并更新本地库。 |
| TC-04 | 重复事件 | 重复投递 | 命中幂等，不产生脏数据。 |
| TC-07 | 初始化 | 执行任务 | 数量与西域口径一致。 |

---

## 客户投标系统 OA 流程接入文档

**版本：** v1.0  
**核心接口：** `POST /oaWorkflow/createWorkflow`

### 1. 总体流程

1.  投标系统提交请求（包含 userNo, workflowId, mainData 等）。
2.  西域 backend 转换字段并调用 OA 接口。
3.  返回 requestId 和表单 URL。

### 2. 流程清单 (workflowId)

| 序号 | 流程名称 | 主表数据 | 说明 |
| :--- | :--- | :--- | :--- |
| 1 | 西域集团-公章盖章申请单 | mainData | 投标系统传源字段，西域映射到 OA |
| 2 | 报价章盖章申请单 | mainData | 同上 |
| 3 | 印章及证件借用申请单 | mainData | 同上 |
| 4 | 西域-投标资料包申请流程 | mainData | 同上 |
| 5 | 一般付款流程 | mainData | 同上 |
| 6 | 费用报销流程 | mainData | 同上 |
| 7 | 借款与保证金申请流程 | mainData | 同上 |
| 8 | 借款与保证金核销流程 | mainData | 同上 |

### 3. 创建 OA 流程接口规范

#### 3.1 请求体字段

| 字段 | 类型 | 必填 | 说明 |
| :--- | :--- | :--- | :--- |
| userNo | String | 是 | 员工号/OA 账号 |
| workflowId | String | 是 | 流程 ID |
| mainData | Object | 是 | 主表业务数据 (Key 为源字段名) |
| detailData | Array | 否 | 明细表 1 数据 |
| attachmentList | Array | 否 | 附件 (建议传 URL) |

#### 3.2 标准请求示例
```json
{
  "userNo": "10000",
  "workflowId": "OA_FLOW_001",
  "mainData": {
    "sourceBillNo": "BID20260430001",
    "applyTitle": "公章盖章申请",
    "requiredDate": "2026-05-06"
  },
  "detailData": [
    { "itemName": "投标文件正本", "quantity": 1 }
  ]
}
```

---

## 4. 状态回传与回调 (Callback)

**规范：**
*   **状态枚举**：REJECT (驳回), ARCHIVE (归档), SUPPLIER_SIGNING (供应商签署)。
*   **示例 JSON：**
```json
{
  "workflowId": "...",
  "status": "ARCHIVE",
  "contractFiles": [
    { "fileName": "归档文件.pdf", "fileUrl": "..." }
  ]
}
```

---

## 5. 联调验收清单

| 阶段 | 验收项 | 通过标准 |
| :--- | :--- | :--- |
| 准备 | 确认 8 个流程 workflowId | 西域提供对应 ID 并在配置表生效 |
| 准备 | 确认字段映射表 | sourceKey 与 OA 字段一一对应 |
| 用户 | 申请人账号校验 | getUserInfo 能正常返回 |
| 创建 | 流程创建最小样例 | 返回 requestId 且表单可打开 |
| 字段 | 主表/明细字段核对 | OA 表单展示值与提交值一致 |
| 幂等 | 重复提交验证 | 阻止同一 sourceBillNo 重复创建 |
| 回调 | 状态回传验证 | 投标系统能正确更新流程状态 |

---

## CRM 接口规范

*   **通讯协议**：HTTPS
*   **鉴权方式**：Token 鉴权 (Authorization Header)
*   **接口列表**：
    1.  `登录鉴权接口`：获取 Token。
    2.  `登出接口`：用户退出。
    3.  `根据名称模糊查询存量有效客户列表`：用于客户信息检索。
    4.  `根据公司 id 列表查询客户负责人列表`：用于获取客户经理。

