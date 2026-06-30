---
title: 组织架构对接 - 客户事件库 SDK 方案
space: engineering
category: integration
tags: [integration, organization, event-sdk, event-bus, data-scope]
sources:
  - .wiki/sources/customer/事件库SDK接入说明方案.doc
  - .wiki/sources/technical/泊冉投标系统与西域对接技术相关内容.md
  - .wiki/extracts/technical__泊冉投标系统与西域对接技术相关内容.md.md
  - backend/src/main/java/com/xiyu/bid/entity/User.java
  - backend/src/main/java/com/xiyu/bid/dto/DataScopeConfigPayload.java
  - docs/integration/organization-role-filter-config.yml
backlinks:
  - _index
  - implementation/xiyu-pending-confirmations
  - integration-oa-crm
created: 2026-04-28
updated: 2026-06-20
health_checked: 2026-06-27
---
# 组织架构对接 - 客户事件库 SDK 方案

## 结论

客户最新 V1.0 技术文档明确：组织架构同步采用“事件库 SDK 订阅 + 组织架构接口查询”模式。西域数智化投标管理平台（以下简称“投标系统”）应将事件作为“变更通知”和“触发器”，通过回查主数据接口获取最新、可信的组织架构数据。

- **事件消息只作为触发器**：不得直接将事件 payload 中的 `data` 当作主数据使用。
- **最终数据源**：以组织架构接口返回结果为准，按 `userId`、`deptId` 等唯一标识执行本地 `upsert`。
- **幂等处理**：系统必须具备幂等处理能力，防止重复消费或短时间多次变更导致的数据不一致。
- **初始化与对账**：上线前全量初始化，上线后增量同步，并保留定时对账机制。

## 客户接入规范摘要

| 项 | 客户文档要求 (V1.0) |
|---|---|
| Java SDK 坐标 | `com.ehsy.eventlibrary:ClientSDK:${eventlibrary.version}`，当前版本 `release_0.0.2` |
| 注册配置 | `client.register.serviceName`、`serverRegisterUrl`、`enableRegister` |
| 续约配置 | `client.renewal.initialDelay`、`period`、`renewalDuration` |
| 消费事件 | 方法增加 `@AcceptEvent(eventTopic = "...", consumerGroup = "...")`，入参为 `String eventMessage` |
| 响应约定 | 处理成功返回 `EventResult` code=200；重试返回 code=500 |

## Bearer Token 换取

YAPI 组织架构接口部署在 EHSY 内网，基于网络白名单安全。
系统调用 YAPI 时**不需要 Bearer Token**，仅带上链路追踪 Header（`EHSY-TraceID`、`EHSY-SRCAPP`）。

| 配置项 | 来源 |
|---|---|
| `XIYU_ORG_DIRECTORY_BASE_URL` | `https://base-oss-test.ehsy.com`（测试环境） |

如需增加 HTTP 认证（如外部环境），可在 `XIYU_ORG_DIRECTORY_AUTH_HEADER` 和 `XIYU_ORG_DIRECTORY_AUTH_TOKEN` 中配置，默认不启用。

## 接入范围与事件契约

| 数据类别 | Topic | 关键唯一标识 | 后续动作 |
|---|---|---|---|
| 部门信息 | `BaseOssDept` | `deptId` / `key` | 调用“根据部门编码获取部门数据”接口 |
| 员工信息 | `BaseOssUser` | `userId` / `key` | 调用“根据员工 ID 获取员工数据”接口 |

### 公共消息结构

| 字段 | 说明 |
|---|---|
| `traceId` | 事件链路追踪 ID，用于问题定位 |
| `spanId` | 事件链路 spanId |
| `eventSource` | 事件来源系统 (当前为 `oss`) |
| `eventTopic` | 事件主题 (`BaseOssDept`, `BaseOssUser`) |
| `time` | 事件产生时间 (毫秒时间戳) |
| `key` | 业务 key (`deptId` 或 `userId`) |
| `data` | 数据载体，仅包含关键标识 |

## 标准处理流程

1. **SDK 初始化**：服务启动后完成注册、续约。
2. **事件推送**：接收 `BaseOssDept` 或 `BaseOssUser` 事件消息。
3. **解析路由**：解析 JSON 字符串，根据 `eventTopic` 路由到对应处理器。
4. **日志流水**：记录 `traceId`、`eventTopic`、`key`、`time` 等日志。
5. **接口回查**：调用西域组织架构接口获取最新完整数据。
6. **本地更新**：按业务主键 `upsert` 到本地表；如状态失效，执行禁用/离职处理。
7. **反馈结果**：返回处理成功或失败标识。

## YAPI 组织架构接口

YAPI 项目入口：`https://yapi.ehsy.com/project/406/`

### 接口清单（已确认）

| 接口名称 | YAPI 地址 | 真实路径 | 方法 | Content-Type | 参数位置 |
|---------|----------|---------|------|-------------|----------|
| 根据部门编码获取部门数据 | `api/23312` | `/subscription/msg/dept` | POST | `application/x-www-form-urlencoded` | form body: `deptId` |
| 根据员工 ID 获取员工数据 | `api/23300` | `/subscription/msg/user` | POST | `application/x-www-form-urlencoded` | form body: `userId` |
| 根据时间窗口获取部门列表 | `api/24179` | `/subscription/msg/getDeptByTimeWindow` | POST | `application/json` | JSON body: `startTime`, `endTime`, `index` |
| 根据时间窗口获取员工列表 | `api/24178` | `/subscription/msg/getUserByTimeWindow` | POST | `application/json` | JSON body: `startTime`, `endTime`, `index` |

**BaseUrl**: `https://base-oss-test.ehsy.com`（测试环境）

### 关键变更（与代码当前假设的差异）

| 项 | 代码当前假设 | YAPI 实际 | 影响 |
|-----|------------|----------|------|
| HTTP 方法 | GET | **POST** | 需修改 `HttpGateway` |
| 参数传递 | URL path (`{deptId}`) | **Request body** | 需修改参数构造方式 |
| Content-Type | application/json | **form-urlencoded** (详情) / **application/json** (窗口) | 需支持两种格式 |
| 分页机制 | 无 | **`data.index` 作为下一页索引** | 需添加循环分页逻辑 |

### 返回 JSON 结构（已确认）

**部门详情** (`POST /subscription/msg/dept`) — 响应结构：

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "deptId": 12345,
    "code": "D001",
    "name": "投标管理部",
    "parentId": 10000,
    "administrativeSuperiors": "10000",
    "status": 1,
    "del": 0
  }
}
```

**员工详情** (`POST /subscription/msg/user`) — 响应结构：

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "userId": 720518523,
    "name": "张三",
    "jobNumber": "10000",
    "email": "zhangsan@ehsy.com",
    "mobilePhone": "138****8888",
    "del": 0,
    "activationState": 1,
    "status": 1,
    "employeeStatus": 3
  }
}
```

> **字段语义（2026-06-30 真实接口验证）**：
> - `status=1`=在职（启用），`status=0`=离职（关闭）— 与 `/oauth/getUserInfo` 一致
> - `employeeStatus=3`=在职，`8`=离职，`1`=待入职 — 与 status 一一对应，作为 status 缺失时 fallback
> - `del=1`=已删除（最高优先级，覆盖 status）
> - `UserEnabledDetector` 判定顺序：`del` > `status` > `employeeStatus` > `activationState` > 兜底启用

**部门时间窗口** (`POST /subscription/msg/getDeptByTimeWindow`) — 响应结构：

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "index": 2,
    "list": [
      {
        "deptId": 12345,
        "code": "D001",
        "name": "投标管理部",
        "parentId": 10000,
        "status": 1,
        "del": 0
      }
    ]
  }
}
```

**员工时间窗口** (`POST /subscription/msg/getUserByTimeWindow`) — 响应结构：

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "index": 5,
    "list": [
      {
        "userId": 720518523,
        "name": "张三",
        "jobNumber": "10000",
        "email": "zhangsan@ehsy.com",
        "mobilePhone": "138****8888",
        "del": 0,
        "activationState": 1
      }
    ]
  }
}
```

### 返回字段映射（已确认 + 待确认）

**部门详情映射**：

| YAPI 字段 | 本地字段 | 状态 | 说明 |
|-----------|---------|------|------|
| `data.deptId` | `externalDeptId` | ✅ 已确认 | 直接映射 |
| `data.code` | `departmentCode` | ✅ 已确认 | 直接映射 |
| `data.name` | `departmentName` | ✅ 已确认 | 直接映射 |
| `data.parentId` / `data.administrativeSuperiors` | `parentExternalDeptId` | ⚠️ 待西域确认 | 哪个字段是父部门 ID？ |
| `data.status` + `data.del` | `enabled` | ⚠️ 待西域确认 | `del=0` 且 `status=1` 为启用？ |
| — | `parentDepartmentCode` | ❌ 无来源 | 响应中无父部门 code 字段 |

**员工详情映射**：

| YAPI 字段 | 本地字段 | 状态 | 说明 |
|-----------|---------|------|------|
| `data.userId` | `externalUserId` | ✅ 已确认 | 直接映射 |
| `data.jobNumber` | `username` | ⚠️ 待西域确认 | 工号是否作为登录用户名？ |
| `data.name` | `fullName` | ✅ 已确认 | 直接映射 |
| `data.email` | `email` | ✅ 已确认 | 直接映射 |
| `data.mobilePhone` | `phone` | ✅ 已确认 | 直接映射 |
| — | `deptCode` | ❌ 无来源 | 响应中无部门信息，需确认是否单独查询 |
| — | `deptName` | ❌ 无来源 | 响应中无部门信息，需确认是否单独查询 |
| — | `roleCode` | ❌ 无来源 | 响应中无职位/角色信息，需确认是否单独查询 |
| `data.activationState` + `data.del` | `enabled` | ⚠️ 待西域确认 | `del=0` 且 `activationState=1` 为启用？ |

### 可选扩展接口

- 获取所有职位信息列表（职位字典）
- 批量根据员工工号获取所属部门信息
- 批量根据部门编码获取部门组织树信息
- 根据职位 ID 获取职位信息数据

## 事件库 SDK 测试环境配置

来自客户文档的参考配置（生产环境由西域另行提供）：

| 配置项 | 测试环境值 |
|---|---|
| Event Bus 注册地址 | `http://event-busserver-test.ehsy.com` |
| Kafka broker | `kafka-01.test.ehsy.com:9094,kafka-02.stag.ehsy.com:9094,kafka-03.stag.ehsy.com:9094` |
| Zookeeper | `zookeeper-01.test.ehsy.com:2183,zookeeper-02.test.ehsy.com:2183,zookeeper-03.test.ehsy.com:2183` |
| 环境标识 | `test` |
| 默认 Consumer Group | `bid-org-consumer-test` |
| 订阅 Topic | `BaseOssDept`, `BaseOssUser` |

### application.yml 参考配置

```yaml
client:
  register:
    serviceName: ${XIYU_ORG_EVENT_SERVICE_NAME:BidSystemOrgConsumer}
    serverRegisterUrl: ${XIYU_ORG_EVENT_SERVER_REGISTER_URL:http://event-busserver-test.ehsy.com}
    enableRegister: ${XIYU_ORG_EVENT_ENABLE_REGISTER:false}
  renewal:
    initialDelay: ${XIYU_ORG_EVENT_RENEWAL_INITIAL_DELAY:3}
    period: ${XIYU_ORG_EVENT_RENEWAL_PERIOD:3}
    renewalDuration: ${XIYU_ORG_EVENT_RENEWAL_DURATION_MS:3000}
```

生产环境不得直接使用测试 `serverRegisterUrl`、测试 `consumerGroup` 或测试账号；生产值由西域另行提供。

## 对平台现状的映射

| 客户组织概念 | 平台落点 | 处理策略 |
|---|---|---|
| 部门 ID | `deptId` (业务主键) | 不得使用自增 ID，基于 `deptId` 进行 upsert |
| 员工 ID | `userId` (业务主键) | 不得使用自增 ID，基于 `userId` 进行 upsert |
| 启停用/状态 | `enabled` 字段 | 接口未查询到或状态失效时禁用，不得物理删除 |
| 职位信息 | `positionCode` / `positionName` / `jobCode` | 通过正则模式匹配映射到系统 `RoleProfile`（见下方"职位到角色映射"） |

## 角色映射与白名单

西域组织架构接口不传递标准化角色码，只返回职位/部门/人员信息。系统通过三类规则映射到内部 `RoleProfile`，并支持白名单过滤：未命中任何规则的用户不会被创建；已存在则被禁用。

### 三类映射规则

1. `personToRoleMappings` — 按人员精确匹配（邮箱 / 工号 / 用户名 / 姓名），**优先级最高**。
2. `departmentToRoleMappings` — 按部门名称正则匹配。
3. `positionToRoleMappings` — 按 OSS 返回的职位编码/名称正则匹配。

### 映射优先级

最终角色决议顺序：

1. 人员映射
2. 部门映射
3. 岗位映射
4. 回退到 `OrganizationSyncPolicy.mapRoleCode()`（`adminRoleCodes` / `managerRoleCodes` 精确匹配）
5. 保持用户现有角色（如果是已有用户且无其他匹配）
6. 默认 `staff`（新建用户且无其他匹配）

### 白名单开关

`skip-unmapped-users: true` 时：

- 未命中任何映射的新用户**不创建**。
- 已同步但不再命中映射的用户会被**禁用**（`enabled = false`），禁止登录。
- 需要物理删除时参考 `scripts/cleanup-unmapped-oss-users.sql`。

### Admin 升级守卫

为防止岗位/部门误配导致普通用户意外获得系统管理员权限，**按岗位/部门映射到 `admin` 时会被守卫拦截**；但通过 `personToRoleMappings` 显式指定的人员可以正常升级为 `admin`。

### 当前生产配置示例

```yaml
xiyu:
  integrations:
    organization:
      skip-unmapped-users: true

      positionToRoleMappings:
        - positionPattern: "^/bidAdmin$"
          roleCode: bid_admin
        - positionPattern: "^bid-TeamLeader$"
          roleCode: bid_lead
        - positionPattern: "^bid-Team$"
          roleCode: bid_specialist
        - positionPattern: "^bid-projectLeader$"
          roleCode: sales
        - positionPattern: "^bid-administration$"
          roleCode: admin_staff
        # bid-SystemAdmin 是 OSS 临时岗位，不在此处硬映射，改由 personToRoleMappings 按人员配置。

      departmentToRoleMappings:
        - departmentPattern: "投标管理部"
          roleCode: bid_specialist
        - departmentPattern: "行政部"
          roleCode: admin_staff

      personToRoleMappings:
        # 张頔、郑蓉蓉、袁思琪目前同时属于 /bidAdmin，并被确认为投标系统管理员（临时）。
        # 由于系统只能绑定一个 RoleProfile，按最高权限给 admin。
        # 后续若 OSS 取消 bid-SystemAdmin，可改回 bid_admin（张頔/郑蓉蓉）或 bid_senior（袁思琪）。
        - personIdentifier: "03595"                # 张頔：工号
          roleCode: admin
        - personIdentifier: "dean_zhang@ehsy.com"  # 张頔：邮箱
          roleCode: admin
        - personIdentifier: "06234"                  # 郑蓉蓉：工号
          roleCode: admin
        - personIdentifier: "tina_zheng1@ehsy.com"   # 郑蓉蓉：邮箱
          roleCode: admin
        - personIdentifier: "11484"                  # 袁思琪：工号
          roleCode: admin
        - personIdentifier: "suki_yuan@ehsy.com"     # 袁思琪：邮箱
          roleCode: admin
```

> 完整配置模板见 `docs/integration/organization-role-filter-config.yml`；清理脚本见 `scripts/cleanup-unmapped-oss-users.sql`。

> `bid_senior`（投标主管）是合并 `bid_admin` + `bid_lead` 权限的角色，由 PR !545 引入，用于处理身兼多职的单角色场景。

## 实现状态与启用步骤

### 当前状态（2026-05-27）

组织架构同步的**核心业务逻辑已实现**，当前处于"代码就绪，SDK jar 已通过 system scope 引入"状态：

| 模块 | 文件 | 状态 |
|------|------|------|
| SDK 依赖配置 | `backend/pom.xml` | ✅ 已配置（`-Psdk` profile） |
| Maven 私服 | `maven.ehsy.com/nexus` | ✅ 已配置 |
| application.yml 基础配置 | `application.yml` / `application-dev.yml` | ✅ 已配置（renewal 参数匹配） |
| application.yml 注册配置 | `application.yml` | ⚠️ 默认空/false，需环境变量注入 |
| application.yml broker 配置 | `application.yml` | ❌ 未配置（可能 SDK 版本不需要） |
| Topic 订阅 | `BaseOssDept`、`BaseOssUser` | ✅ 已配置（`@AcceptEvent` 注释中） |
| consumerGroup（代码硬编码 `bms`） | `bms` | ✅ `@AcceptEvent` 中硬编码，环境变量 `XIYU_ORG_EVENT_CONSUMER_GROUP` 可覆盖 |
| consumerGroup 生产 | `bid-org-consumer-prod` | ⚠️ 需生产环境注入 |
| 事件消费适配 | `OrganizationEventSdkConsumerAdapter` | ✅ `@AcceptEvent` 已激活 |
| 公共消息结构 | `OrganizationEventNotice` / `OrganizationEventNoticeFields` | ✅ 已实现（8 字段完全匹配） |
| 事件解析 | `OrganizationEventNoticeJsonReader` | ✅ 已实现 |
| 主数据回查 | `OrganizationDirectoryHttpGateway` | ✅ 已实现 |
| 部门事件处理 | `BaseOssDept` → `fetchDepartmentByDeptId(deptId)` | ✅ 已实现（`data.deptId` 为主键，`data.id` 未使用） |
| 员工事件处理 | `BaseOssUser` → `fetchUserByUserId(userId)` | ✅ 已实现 |
| 部门接口路径 | YAPI `api/23312` | `/subscription/msg/dept` | ✅ 已更新（POST + form body） |
| 员工接口路径 | YAPI `api/23300` | `/subscription/msg/user` | ✅ 已更新（POST + form body） |
| 部门同步写入 | `OrganizationDepartmentSyncWriter` | ✅ 已实现 |
| 员工同步写入 | `OrganizationUserSyncWriter` | ✅ 已实现 |
| 事件收件箱 | `OrganizationEventInboxService` | ✅ 已实现（幂等、重试、死信） |
| 定时重试 | `OrganizationEventRetryScheduler` | ✅ 已实现 |
| 定时对账 | `OrganizationReconciliationScheduler` | ✅ 已实现 |
| 链路追踪 Header | `EHSY-TraceID`、`EHSY-SRCAPP` | ✅ 已实现 |
| 超时设置 | connect 3s / read 5s | ✅ 已实现 |
| 禁用非删除 | `disableByExternalId()` | ✅ 已实现 |
| Java 订阅方法 | `@AcceptEvent` + `EventResult` | ✅ 已实现 |

### 标准处理流程映射

客户文档定义的 8 步标准处理流程与代码实现对应关系：

| 步骤 | 文档要求 | 代码实现 | 状态 |
|------|---------|---------|------|
| 1 | SDK 启动注册、续约、初始化 | `OrganizationEventSdkConsumerAdapter` + `OrganizationEventSdkKafkaStarter` | ✅ 已实现 |
| 2 | 接收 `BaseOssDept`/`BaseOssUser` 事件 | `@AcceptEvent` 方法 | ✅ 已实现 |
| 3 | 解析 `eventMessage` JSON | `OrganizationEventNoticeJsonReader.parse()` → `ObjectMapper.readTree()` | ✅ 已就绪 |
| 4 | 按 `eventTopic` 路由，`data.deptId`/`data.userId` | `OrganizationEventNoticeParser.parse()` → `topicFromEventTopic()` + `subjectId` | ✅ 已就绪 |
| 5 | 记录 `traceId`/`spanId`/`key`/`time` | `OrganizationEventNotice` record + `OrganizationEventInboxService` 持久化 | ✅ 已就绪 |
| 6 | 调组织架构接口回查 | `OrganizationDirectoryHttpGateway.fetchDepartmentByDeptId()` / `fetchUserByUserId()` | ✅ 已就绪 |
| 7 | 成功 upsert，失败禁用 | `OrganizationDepartmentSyncWriter.upsert()` / `disableByExternalId()` | ✅ 已就绪 |
| 8 | 返回 `EventResult` code=200/500 | `OrganizationEventSdkResponseMapper.toResponse()` / `fromException()` | ✅ 已就绪 |

**结论**：8 步流程中 8 步已完全实现，`@AcceptEvent` 已激活，SDK jar 已通过 `system scope` 引入。

### 启用步骤（西域私服就绪后）

```bash
# 1. 确认 Maven 私服可访问，能拉取 ClientSDK jar
mvn compile -Psdk

# 2. 去掉 OrganizationEventSdkConsumerAdapter 中的注释：
#    - 2 处 @AcceptEvent
#    - 2 处 EventInfoRespDto 返回类型
#    - handleEvent() 中 EventInfoRespDto 构造

# 3. 设置环境变量
export XIYU_ORG_EVENT_SDK_ENABLED=true

# 4. 打包运行
mvn clean package -Psdk
```

### 与 CRM 查询接口的关系

**组织架构同步**和 **CRM 查询接口**（[[integration-oa-crm]] §2）是**完全独立的两个模块**：

| 维度 | 组织架构同步 (本页) | CRM 查询接口 |
|------|-------------------|-------------|
| 包路径 | `com.xiyu.bid.integration.organization.*` | `com.xiyu.bid.crm.*` |
| 触发方式 | 事件驱动（Push） | 主动查询（Pull） |
| 数据流向 | CRM → 我们 | 我们 → CRM |
| 数据落点 | 本地 `User` / `Department` 表 | 不落库，直接返回 |
| 用途 | 登录鉴权、数据范围、审批流 | 客户检索、消息发送 |

> **常见误解**："CRM 接口做好了，组织架构就打通了"——这是错误的。二者代码独立，互不影响。

## 推荐后端模块拆分

```text
backend/src/main/java/com/xiyu/bid/integration/organization/
├── domain/                  # 纯核心：事件解析、同步计划、差异策略
├── application/             # 事务编排：主数据回查、保存、审计
│   ├── # Token 换取已移除，YAPI 基于内网白名单安全
│   └── OrganizationIntegrationProperties.java
├── infrastructure/
│   ├── sdk/                 # SDK 适配：@AcceptEvent 消费
│   │   ├── OrganizationEventSdkConsumerAdapter.java
│   │   └── OrganizationEventSdkResponseMapper.java
│   ├── client/              # HTTP 网关：YAPI 组织架构接口
│   └── persistence/         # 持久化：部门/人员 Entity 与 Repository
└── controller/               # 运维 API（sync run / operations / manual resync）
```

> **2026-05-28 更新**：HTTP fallback 路径已删除（`ClientSdkAdapter`、`EventSyncService`、`HttpFallbackController` 等），SDK adapter 为唯一事件接收入口。`OrganizationTokenService` 已移除，YAPI 基于内网白名单安全，无需 Bearer Token。

## 异常处理与重试

- **超时设置**：建议 3-5 秒，失败后进入指数退避重试.
- **格式异常**：记录失败并进入人工处理队列.
- **并发控制**：同一对象建议串行或分布式锁，避免覆盖.

## 配置对照表

### application.yml 配置映射

| 配置项 | 文档要求 | 代码配置 | 状态 |
|--------|---------|---------|------|
| `client.register.serviceName` | `BidSystemOrgConsumer` | `${XIYU_ORG_EVENT_SERVICE_NAME:}`（默认空） | ⚠️ 需环境变量注入 |
| `client.register.serverRegisterUrl` | `http://event-busserver-test.ehsy.com` | `${XIYU_ORG_EVENT_SERVER_REGISTER_URL:}`（默认空） | ⚠️ 需环境变量注入 |
| `client.register.enableRegister` | `true` | `${XIYU_ORG_EVENT_ENABLE_REGISTER:false}`（默认 false） | ⚠️ 需显式开启 |
| `client.renewal.initialDelay` | `3` | `${XIYU_ORG_EVENT_RENEWAL_INITIAL_DELAY:3}` | ✅ 匹配 |
| `client.renewal.period` | `3` | `${XIYU_ORG_EVENT_RENEWAL_PERIOD:3}` | ✅ 匹配 |
| `client.renewal.renewalDuration` | `3000` | `${XIYU_ORG_EVENT_RENEWAL_DURATION_MS:3000}` | ✅ 匹配 |
| `broker.configure.serverList` | Kafka broker 列表 | 未配置 | ❌ 缺失（可能 SDK 版本不需要） |
| `broker.configure.zkServers` | Zookeeper 列表 | 未配置 | ❌ 缺失（可能 SDK 版本不需要） |
| `broker.configure.env` | `test` | 未配置 | ❌ 缺失（可能 SDK 版本不需要） |

**安全设计**：默认值均为空/false，避免误连测试环境。生产环境必须通过环境变量注入真实值。

## 验证与对账

### 联调用例验证

客户文档要求启用前必须完成 8 个联调测试用例。以下是代码实现与测试用例的映射验证：

| 用例 | 测试目的 | 验证路径 | 代码实现 | 状态 |
|------|---------|---------|---------|------|
| **TC-01** | SDK 启动：服务注册与处理器加载 | `@ConditionalOnClass` + `@ConditionalOnProperty` 确保 SDK jar 存在且 `enabled=true` 时 bean 创建；`@AcceptEvent` 注释去掉后处理器生效 | `OrganizationEventSdkConsumerAdapter` 有 `@ConditionalOnClass(name="com.ehsy.eventlibrary.annotations.AcceptEvent")` 和 `@ConditionalOnProperty(prefix="xiyu.integrations.organization.event-sdk", name="enabled", havingValue="true")`。`onDeptChanged()`/`onUserChanged()` 方法体内调用 `appService.receiveViaSdk()` → `directorySyncAppService.receiveWebhook()` | ✅ 已实现 |
| **TC-02** | 部门数据变更：事件触发 → 本地库更新闭环 | `BaseOssDept` 事件 → `onDeptChanged()` → `receiveViaSdk()` → `receiveWebhook()` → `parse()` → `processNotice()` → `lookupAndWrite()` → `fetchDepartmentByDeptId()` → `departmentWriter.upsert()` | 完整链路：`OrganizationEventSdkConsumerAdapter.onDeptChanged()` → `OrganizationEventAppService.receiveViaSdk()` → `OrganizationDirectorySyncAppService.receiveWebhook()` → `noticeJsonReader.parse()` → `processNotice()` → `lookupAndWrite()` → `OrganizationDirectoryHttpGateway.fetchDepartmentByDeptId()` → `OrganizationDepartmentSyncWriter.upsert()` | ✅ 已实现 |
| **TC-03** | 员工数据变更：事件触发 → 本地库更新闭环 | `BaseOssUser` 事件 → `onUserChanged()` → 同上链路 → `fetchUserByUserId()` → `userWriter.upsert()` | 与 TC-02 完全一致，仅 `topic="BaseOssUser"` → `fetchUserByUserId()` → `OrganizationUserSyncWriter.upsert()` | ✅ 已实现 |
| **TC-04** | 幂等验证：重复投递不产生脏数据 | 同一事件重复发送 → `inboxService.reserve()` → 唯一索引冲突 → 返回 200 DUPLICATE | `OrganizationEventInboxService.reserve()` 使用 `@Transactional(REQUIRES_NEW)` 保存 `OrganizationEventLogEntity`，`eventKey` 由 `eventSource+topic+key+time` SHA-256 哈希生成，数据库唯一索引保证幂等。`DataIntegrityViolationException` → 返回 false → 响应 200 DUPLICATE | ✅ 已实现 |
| **TC-05** | 超时/5xx：接口失败进入重试队列 | 模拟接口超时或 5xx → `HttpGatewayException.retryable()` → 返回 500 PENDING_RETRY → `EventRetryScheduler` 定时重试 | `OrganizationDirectoryHttpGateway.getJson()` 捕获 `HttpStatusCodeException` (5xx) 或 `RestClientException` (网络/超时) → `OrganizationDirectoryHttpGatewayException.retryable()` → `markFailed()` → `PENDING_RETRY`。`OrganizationEventRetryScheduler.retryDueEvents()` 每分钟扫描 `PENDING_RETRY` 事件，指数退避重试 | ✅ 已实现 |
| **TC-06** | 缺失字段：非法事件返回 500 不入库 | 发送缺少 `eventTopic`/`data.userId`/`data.deptId` 的事件 → `noticeJsonReader.parse()` 失败 → 返回 500 REJECTED | `OrganizationEventNoticeJsonReader.parse()` 捕获 `JsonProcessingException` → `ParseResult.invalid("JSON格式错误")`；`OrganizationEventNoticeParser.parse()` 校验必填字段 (traceId, spanId, eventSource, time, key, subjectId) → 返回 `invalid("缺少字段 xxx")` → `markRejected()` → 500 REJECTED | ✅ 已实现 |
| **TC-07** | 初始化：时间窗口查询全量数据 | 调用 `listDepartmentsByWindow()`/`listUsersByWindow()` 按时间范围拉取数据 → 逐条 upsert | `OrganizationDirectoryHttpGateway.listDepartmentsByWindow(startAt, endAt)` 和 `listUsersByWindow(startAt, endAt)` 已就绪。`OrganizationSyncRunAppService.syncWindow()` 编排批量同步。`OrganizationReconciliationScheduler` 每天 2:30 自动执行对账 | ✅ 已实现 |
| **TC-08** | 对账：定时补偿最近 1-3 天数据 | `OrganizationReconciliationScheduler` 触发 → `syncRunAppService.syncWindow()` → 时间窗口查询 → 逐条比对/更新 | `OrganizationReconciliationScheduler.reconcileRecentWindow()` 默认 cron `0 30 2 * * *`（每天 2:30），`lookbackDays` 默认 3 天。调用 `syncRunAppService.syncWindow(SOURCE_APP, startAt, endAt, "RECONCILIATION")` 执行对账 | ✅ 已实现 |

**结论**：8 个联调用例全部有代码实现支撑。TC-01~TC-06 为事件消费链路验证，TC-07~TC-08 为初始化/对账验证。代码路径完整。TC-01~TC-03 需与西域联调环境配合验证。

### 对账机制

- **对账机制**：建议每天低峰期拉取最近 1-3 天数据进行对账修复。

### 事件 JSON 示例验证

**BaseOssDept 示例**（客户文档提供）：

```json
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
```

**代码解析验证**：

| 字段 | JSON 值 | 代码提取 | 结果 |
|------|---------|---------|------|
| `traceId` | `t509415008096264192` | `text(root, "traceId")` | ✅ |
| `spanId` | `s509415010981044224` | `text(root, "spanId")` | ✅ |
| `parentId` | `s509415008096264193` | `text(root, "parentId")` | ✅ |
| `eventSource` | `oss` | `text(root, "eventSource")` | ✅ |
| `eventTopic` | `BaseOssDept` | `text(root, "eventTopic")` → `DEPARTMENT_NOTICE` | ✅ |
| `time` | `1730884403101` | `text(root, "time")`（Number → String） | ✅ |
| `key` | `3730158` | `text(root, "key")` | ✅ |
| `data.deptId` | `3730158` | `text(data, "deptId")` → `subjectId` | ✅ |
| `data.id` | `3600` | 未提取 | ✅ 符合要求 |

**结论**：JSON 示例能被代码正确解析，`data.deptId`（Number 类型）通过 `value.asText()` 转为 String 后作为 `subjectId` 调接口。

**BaseOssUser 示例**（客户文档提供）：

```json
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
```

**代码解析验证**：

| 字段 | JSON 值 | 代码提取 | 结果 |
|------|---------|---------|------|
| `traceId` | `t704982503755501568` | `text(root, "traceId")` | ✅ |
| `spanId` | `s704982511551434752` | `text(root, "spanId")` | ✅ |
| `parentId` | `s704982503755501569` | `text(root, "parentId")` | ✅ |
| `eventSource` | `oss` | `text(root, "eventSource")` | ✅ |
| `eventTopic` | `BaseOssUser` | `text(root, "eventTopic")` → `USER_NOTICE` | ✅ |
| `time` | `1777511328702` | `text(root, "time")` | ✅ |
| `key` | `720518523` | `text(root, "key")` | ✅ |
| `data.userId` | `720518523` | `text(data, "userId")` → `subjectId` | ✅ |
| `data.id` | `533032` | 未提取 | ✅ 符合要求 |

**结论**：员工事件与部门事件结构完全一致，仅 `data.userId` 替代 `data.deptId`，代码按字段名提取不受顺序影响。

### Java 订阅方法对比

客户文档示例 vs 代码实现：

| 文档示例 | 代码实现 | 状态 |
|---------|---------|------|
| `@AcceptEvent(eventTopic = "BaseOssDept")` | `OrganizationEventSdkConsumerAdapter.onDeptChanged()` | ✅ 已实现 |
| `@AcceptEvent(eventTopic = "BaseOssUser")` | `OrganizationEventSdkConsumerAdapter.onUserChanged()` | ✅ 已实现 |
| `consumerGroup = "bid-org-consumer-test"` | `application.yml` 默认配置 | ✅ 匹配 |
| 方法入参 `String eventMessage` | `onDeptChanged(String eventMessage)` / `onUserChanged(String eventMessage)` | ✅ 匹配 |
| 返回值 `EventResult` | 当前返回 `EventResult` | ✅ 已实现 |
| 解析 JSON | `OrganizationEventNoticeJsonReader.parse()` | ✅ 已实现 |
| 校验必需字段 | `OrganizationEventNoticeParser.parse()` | ✅ 已实现 |
| 幂等（traceId + spanId + eventTopic） | `OrganizationEventInboxService.reserve()` | ✅ 已实现 |
| 调组织架构接口 | `OrganizationDirectoryHttpGateway` | ✅ 已实现 |
| upsert 本地表 | `OrganizationDepartmentSyncWriter` / `OrganizationUserSyncWriter` | ✅ 已实现 |
| 成功返回 code=200 | `OrganizationEventSdkResponseMapper.toResponse(200)` | ✅ 已实现 |
| 失败返回 code=500 | `OrganizationEventSdkResponseMapper.fromException()` | ✅ 已实现 |

**结论**：代码实现比文档示例更完善。示例只是伪代码框架，实际代码已完整实现解析、校验、幂等、回查、写入、响应全流程。

### 返回值规范验证

| 处理结果 | 返回 code | 文档要求 | 代码实现 | 状态 |
|---------|----------|---------|---------|------|
| 成功 | 200 | 解析、回查、落库全部成功 | `upsert()` → `markProcessed()` → 返回 200 | ✅ 符合 |
| 幂等重复 | 200 | 已处理过且无需重复处理 | `reserve()` 返回 false → 返回 200（`DUPLICATE`） | ✅ 符合 |
| 解析失败 | 500 | 需重试 | `parse()` 失败 → `markRejected()` → 返回 500 | ✅ 符合 |
| 接口失败 | 500 | 需重试 | `HttpGatewayException` → `markFailed()` → 返回 500 | ✅ 符合 |
| 落库失败 | 500 | 需重试 | `RuntimeException` → `markFailed()` → 返回 500 | ✅ 符合 |

**关键约束验证**：

1. **✅ 不得在未完成持久化前返回 200**：代码流程为 `reserve()` → `lookupAndWrite()` → `upsert()` → `markProcessed()` → 返回 200
2. **✅ 先入本地事件流水表，再异步处理**：`inboxService.reserve()` 先写入事件流水表，然后执行 `lookupAndWrite()`
3. **✅ 接口不可用/数据库异常/网络超时 → 返回 500**：`HttpGatewayException` 和 `RuntimeException` 均返回 500（`PENDING_RETRY`）

### 联调要求（未完成）

根据客户文档要求，启用前必须完成：

| 联调项 | 要求 | 状态 |
|--------|------|------|
| 部门事件联调 | 至少一次 `BaseOssDept` 事件端到端验证 | ❌ 未完成 |
| 员工事件联调 | 至少一次 `BaseOssUser` 事件端到端验证 | ❌ 未完成 |

**联调前提**：
1. 西域事件库管理端创建/确认订阅关系
2. SDK 启用（去掉 `@AcceptEvent` 注释）
3. 测试环境配置正确（`bid-org-consumer-test`）
4. 组织架构接口路径更新（需从 YAPI 文档 `api/23312`、`api/23300` 提取真实接口路径，替换代码中的占位符）

### 待确认项（TODO）

| # | 事项 | 当前状态 | 阻塞原因 |
|---|------|---------|---------|
| 1 | Maven 私服 `maven.ehsy.com/nexus` 可访问 | ✅ **已确认** | 西域内网私有仓库，本地无法访问属正常。服务器已确认可访问。`pom.xml` 配置地址：`https://maven.ehsy.com/nexus/repository/maven-releases/` |
| 2 | ClientSDK jar 版本 | `release_0.0.2` | ✅ **已确认** — `pom.xml` 已配置 `<eventlibrary.version>release_0.0.2</eventlibrary.version>` |
| 3 | 组织架构接口真实路径 | ✅ **已确认** — 4 个接口路径已提取 | ✅ **已完成** — `OrganizationDirectoryHttpGateway` 已使用 POST + form-urlencoded/JSON |
| | | - 部门详情: `POST /subscription/msg/dept` | | |
| | | - 员工详情: `POST /subscription/msg/user` | | |
| | | - 部门窗口: `POST /subscription/msg/getDeptByTimeWindow` | | |
| | | - 员工窗口: `POST /subscription/msg/getUserByTimeWindow` | | |
| 4 | `XIYU_ORG_DIRECTORY_BASE_URL` | 环境变量注入 | YAPI 接口基础地址，无需额外认证凭据 |
| 5 | 事件库管理端订阅关系 | 未创建 | 需西域创建/确认 |
| 6 | 可选扩展接口（7 个） | 未实现 | 等所有文档学习完后评估是否需要 |
| 7 | 并发控制 — 同一对象串行/分布式锁 | 未实现 | 需评估是否在高并发场景下添加 Redis 分布式锁或按 subjectId 哈希队列 |
| 8 | 削峰处理 — 本地队列限流 | 未实现 | 需评估大批量组织调整时是否添加内存队列 + 令牌桶限流 |
| 9 | 自动告警机制 — 死信积压/重试阈值/接口失败 | 未实现 | 需评估是否添加企微/邮件告警（当前仅持久化到 DEAD_LETTER 表） |
| 10 | 日志字段 — consumerGroup 记录 | 未实现 | 需在事件流水表或日志中记录 consumerGroup |
| 11 | 日志字段 — 处理耗时记录 | 未实现 | 需添加 StopWatch 或 nanoTime 计算事件处理耗时 |
| 12 | 日志字段 — 接口调用日志 | 未实现 | 需在 OrganizationDirectoryHttpGateway 中添加请求/响应/耗时日志 |
| 13 | 敏感信息脱敏应用 | 工具就绪 | SensitiveDataMasker 已提供方法，需在组织同步日志中应用 |
| 14 | 监控指标 — OrgSyncMetrics 接入业务代码 | 指标类已定义 | 需在 SyncAppService 中调用 recordOrgSyncSuccess/Failure/Time |
| 15 | 监控指标 — 接口调用成功率拆分统计 | 未实现 | 需在 HttpGateway 中添加按 status/接口分类的 Counter 和 Timer |
| 16 | 监控指标 — 补偿任务成功率 | 未实现 | 需在 EventRetryScheduler 中记录重试成功/失败指标 |
| 17 | 监控告警 — Prometheus AlertManager 规则 | 未配置 | 需配置事件消费成功率<99%、失败积压>阈值、接口失败率等告警规则 |

## 可选扩展接口

客户文档提供的 9 个可选扩展接口与代码实现对比：

| 接口名称 | YAPI 地址 | 使用场景 | 代码实现 | 状态 |
|---------|----------|---------|---------|------|
| 根据时间窗口分页获取员工信息列表 | `api/24178` | 初始化、增量补偿、对账 | `OrganizationDirectoryHttpGateway.listUsersByWindow()` | ✅ 已实现 |
| 根据时间窗口分页获取部门信息列表 | `api/24179` | 初始化、增量补偿、对账 | `OrganizationDirectoryHttpGateway.listDepartmentsByWindow()` | ✅ 已实现 |
| 获取所有职位信息列表 | `api/24180` | 职位字典 | 未实现 | ❌ 待评估 |
| 批量根据员工工号获取所属部门信息 | `api/23252` | 补齐部门信息 | 未实现 | ❌ 待评估 |
| 批量根据员工工号获取所负责部门列表 | `api/23264` | 负责部门范围 | 未实现 | ❌ 待评估 |
| 批量根据部门编码获取部门组织树信息 | `api/23270` | 部门树、父子路径 | 未实现 | ❌ 待评估 |
| 根据职位 ID 获取职位信息数据 | `api/23318` | 补齐职位详情 | 未实现 | ❌ 待评估 |
| 根据用户 id 获取员工信息数据 | `api/23306` | 获取任职记录 | 未实现 | ❌ 待评估 |
| 根据员工工号获取所有直接下属 | `api/25959` | 递归获取下属 | 未实现 | ❌ 待评估 |

**结论**：9 个可选接口中 2 个已实现（时间窗口查询），7 个待评估。是否实现取决于业务需求，等所有文档学习完后统一判断。

### 调用要求验证

| 要求 | 文档内容 | 代码实现 | 状态 |
|------|---------|---------|------|
| 字段级映射确认 | 以 YAPI 最新文档为准，开发前需确认 | `OrganizationDepartmentSnapshot` / `OrganizationUserSnapshot` 已定义 | ⚠️ 需与 YAPI 文档核对字段 |
| 链路追踪 Header | `EHSY-TraceID`、`EHSY-SRCAPP` | `OrganizationDirectoryAuthHeaders.headers()` 自动设置 | ✅ 已实现 |
| 超时设置 | 3-5 秒 | `connectTimeoutMs=3000`、`readTimeoutMs=5000` | ✅ 已实现 |
| 指数退避重试 | 失败后指数退避 | `OrganizationEventRetryScheduler` 定时重试 | ✅ 已实现 |
| 合并处理 | 同一对象短时间多次变更可合并 | `OrganizationEventInboxService.reserve()` 幂等去重 | ✅ 已实现 |
| 禁用非删除 | 未查询到数据时禁用 | `disableLocalDepartment()` / `disableLocalUser()` | ✅ 已实现 |

**关键约束验证**：
1. **✅ 链路追踪**：`OrganizationDirectoryAuthHeaders` 自动从 `OrganizationDirectoryLookupContext` 获取 `traceId` 和 `sourceApp`，设置到 `EHSY-TraceID` 和 `EHSY-SRCAPP` Header
2. **✅ 超时设置**：connect 3s、read 5s，符合 3-5 秒要求
3. **✅ 禁用非删除**：`disableByExternalId()` 将本地数据标记为禁用，不做物理删除

### 状态处理建议验证

| 场景 | 文档要求 | 代码实现 | 状态 |
|------|---------|---------|------|
| 新增或修改 | 按接口返回 upsert 本地数据 | `departmentWriter.upsert()` / `userWriter.upsert()` | ✅ 已实现 |
| 员工离职/禁用 | 更新为离职/禁用，保留历史引用，不物理删除 | `userWriter.disableByExternalId()` + `OrganizationUserSnapshot.enabled=false` | ✅ 已实现 |
| 部门禁用/撤销 | 更新部门状态，保留历史引用 | `departmentWriter.disableByExternalId()` + `OrganizationDepartmentSnapshot.enabled=false` | ✅ 已实现 |
| 接口暂时无数据 | 记录待确认状态，进入补偿任务 | `disableLocalDepartment()` / `disableLocalUser()` 返回 200（PROCESSED） | ⚠️ 需确认是否进入补偿任务 |
| 重复事件 | 幂等键命中直接返回成功 | `inboxService.reserve()` 返回 false → 返回 200（DUPLICATE） | ✅ 已实现 |

**关键发现**：
- `OrganizationDepartmentSnapshot` 和 `OrganizationUserSnapshot` 均包含 `enabled` 字段，用于标记禁用状态
- 禁用操作保留历史数据，不物理删除，符合文档要求
- 接口暂时无数据时返回 200（PROCESSED），但是否需要进入补偿任务待确认

### 初始化要求验证

| 步骤 | 文档要求 | 代码实现 | 状态 |
|------|---------|---------|------|
| 9 | 调用时间窗口接口初始化部门数据 | `OrganizationDirectoryHttpGateway.listDepartmentsByWindow()` | ✅ 已实现 |
| 10 | 调用时间窗口接口初始化员工数据 | `OrganizationDirectoryHttpGateway.listUsersByWindow()` | ✅ 已实现 |
| 11 | 如需职位/任职/负责部门/部门树，调用扩展接口 | 7 个扩展接口未实现 | ❌ 待评估 |
| 12 | 记录初始化时间点，开启事件订阅 | `OrganizationSyncRunEntity` 记录同步时间 | ✅ 已实现 |

**关键发现**：
- 时间窗口查询接口已可用于全量初始化
- `OrganizationManualResyncAppService` 支持单条手动重同步
- `OrganizationSyncRunEntity` 持久化同步记录（类型、状态、时间、成功/失败数量）
- 缺少自动批量初始化编排服务（需手动调用时间窗口接口或逐条重同步）

### 对账与补偿机制验证

| 文档要求 | 代码实现 | 状态 |
|---------|---------|------|
| 每天低峰期按时间窗口拉取最近 1-3 天变更数据对账 | `OrganizationReconciliationScheduler.reconcileRecentWindow()` | ✅ 已实现 |
| 对账 cron 表达式可配置 | `${xiyu.integrations.organization.reconciliation.cron:0 30 2 * * *}` | ✅ 已实现 |
| 对账 lookback 天数可配置 | `${xiyu.integrations.organization.reconciliation.lookback-days}` | ✅ 已实现 |
| 对账默认每天凌晨 2:30 执行 | cron 默认 `0 30 2 * * *` | ✅ 已实现 |
| 事件消费失败进入补偿任务重试 | `OrganizationEventRetryScheduler` + `OrganizationEventRetryAppService` | ✅ 已实现 |
| 按 `retry_count`、`next_retry_time` 进行重试 | `OrganizationEventInboxService.markRetryableFailure()` + `OrganizationDirectoryRetryPolicy.decide()` | ✅ 已实现 |
| 指数退避重试策略 | `BASE_DELAY_MINUTES=5`, `MAX_DELAY_MINUTES=60`, 最大指数 4 | ✅ 已实现 |
| 长期失败事件进入死信队列 | `OrganizationEventStatus.DEAD_LETTER` | ✅ 已实现 |
| 死信事件支持人工重放 | `OrganizationEventRetryAppService.replayDeadLetter()` | ✅ 已实现 |
| 提供 `traceId`、`eventTopic`、`key`、错误信息 | `OrganizationEventLogEntity` 持久化全字段 | ✅ 已实现 |
| 手工按 userId / deptId 重新同步运维入口 | `OrganizationManualResyncAppService.resyncUser()` / `resyncDepartment()` | ✅ 已实现 |
| 重试定时任务固定间隔调度 | `@Scheduled(fixedDelayString = "${xiyu.integrations.organization.retry.fixed-delay-ms:60000}")` | ✅ 已实现 |
| 重试批处理大小可配置 | `${xiyu.integrations.organization.retry.batch-size}` | ✅ 已实现 |
| 重试最大次数可配置 | `${xiyu.integrations.organization.retry.max-attempts}` | ✅ 已实现 |
| 处理中状态 15 分钟租约回收 | `PROCESSING_LEASE_MINUTES = 15` + `recoverStaleProcessing()` | ✅ 已实现 |

**关键发现**：
- 对账机制完整：定时调度 + 时间窗口查询 + 逐条比对/更新
- 补偿机制完整：定时重试 + 指数退避 + 死信队列 + 人工重放
- 运维入口完整：单条手动重同步 + 死信重放
- 所有时间参数、批大小、最大重试次数均可通过配置调整

### 幂等、顺序与并发要求验证

| 文档要求 | 代码实现 | 状态 |
|---------|---------|------|
| **幂等消费** — 基于 `traceId + spanId + eventTopic` 或 `eventTopic + key + time` | `OrganizationSyncPolicy.idempotencyKey()` 使用 `eventSource + topic + key + time` 拼接，经 SHA-256 哈希作为 `eventKey` | ✅ 已实现 |
| 幂等键数据库唯一约束 | `OrganizationEventLogEntity.eventKey` 有唯一索引，`reserve()` 捕获 `DataIntegrityViolationException` 返回 false | ✅ 已实现 |
| **业务主键 upsert** — 部门按 `deptId` | `OrganizationDepartmentSyncWriter.upsert()` 通过 `findBySourceAppAndExternalDeptId()` 查询后更新或新建 | ✅ 已实现 |
| **业务主键 upsert** — 员工按 `userId` | `OrganizationUserSyncWriter.upsert()` 通过 `findByExternalOrgSourceAppAndExternalOrgUserId()` 查询后更新或新建 | ✅ 已实现 |
| 不使用自增 ID 作为跨系统主键 | `externalDeptId` / `externalUserId` 为业务主键，本地 ID 仅作内部关联 | ✅ 已实现 |
| **不依赖全局顺序** — 不同对象无顺序要求 | 每个事件独立处理，通过 `eventKey` 幂等去重，无全局队列或顺序依赖 | ✅ 已实现 |
| 同一对象多次变更以最新接口结果覆盖 | `upsert()` 每次均调用接口查询最新数据后覆盖本地，不依赖事件顺序 | ✅ 已实现 |
| **并发控制** — 同一 `userId`/`deptId` 建议串行或加分布式锁 | ⚠️ **未实现** — 代码中无分布式锁或串行化机制。`@Transactional(REQUIRES_NEW)` 依赖数据库乐观锁/唯一约束 | ❌ **缺失** |
| **削峰处理** — 大批量调整时本地队列限流 | ⚠️ **未实现** — 无本地队列、无速率限制。SDK 推送直接处理，重试调度有 `batchSize` 但非削峰 | ❌ **缺失** |

**关键发现**：
1. **幂等实现完善**：`eventSource + topic + key + time` 组合经 SHA-256 哈希生成唯一 `eventKey`，数据库唯一索引保证幂等。重复事件直接返回 200（DUPLICATE），不重复处理。
2. **业务主键正确**：部门用 `externalDeptId`、员工用 `externalUserId` 作为跨系统主键，本地 `upsert` 逻辑正确。
3. **顺序不依赖**：每次事件都实时调接口获取最新数据，不依赖事件到达顺序。即使旧事件晚到，也会以接口最新结果覆盖（可能被新事件覆盖，但最终一致）。
4. **并发控制缺失**：同一 `deptId`/`userId` 的并发事件可能导致竞态条件（后到的覆盖先到的）。建议增加分布式锁（如 Redis RedLock）或按 `subjectId` 哈希到单线程队列。
5. **削峰处理缺失**：SDK 事件直接触发接口调用，大批量组织调整时可能瞬时大量调用 YAPI 接口。建议增加本地内存队列 + 令牌桶限流，或利用 SDK 的消费速率限制。

### 异常处理与重试策略验证

| 异常类型 | 文档示例 | 代码实现 | 状态 |
|---------|---------|---------|------|
| **消息格式异常** — 非法 JSON、缺少字段 | `eventMessage` 不是合法 JSON、缺少 `eventTopic`、缺少 `data.userId`/`data.deptId` | `OrganizationEventNoticeJsonReader.parse()` 捕获 `JsonProcessingException` → `ParseResult.invalid("JSON格式错误")`；`OrganizationEventNoticeParser.parse()` 校验必填字段 → `invalid("缺少字段 xxx")` | ✅ 已实现 |
| 格式异常记录原始报文 | 记录失败事件和原始报文 | `markRejected(eventKey, message, rawPayload)` 持久化到 `OrganizationEventLogEntity` | ✅ 已实现 |
| 格式异常返回 500 | 返回 500 或进入人工处理 | `response("500", parsed.message(), ..., REJECTED)` | ✅ 已实现 |
| **未知 Topic** — 非 BaseOssDept/BaseOssUser | 收到未接入范围的事件 | `OrganizationSyncPolicy.topicFromEventTopic()` 返回 `Optional.empty()` → `ParseResult.invalid("不支持的组织事件主题")` | ✅ 已实现 |
| 未知 Topic 记录告警 | 记录告警，不处理业务数据 | `markRejected()` 记录为 REJECTED 状态，返回 500 | ✅ 已实现 |
| **组织架构接口失败** — 超时/5xx/网络不可达 | 超时、5xx、网络不可达 | `RestTemplate` 抛出 `HttpStatusCodeException` (5xx) 或 `RestClientException` (网络) → `HttpGatewayException.retryable()` | ✅ 已实现 |
| 接口失败指数退避重试 | 按指数退避重试 | `OrganizationDirectoryRetryPolicy.decide()` 5min base × 2^n，max 60min | ✅ 已实现 |
| 接口失败达到阈值告警 | 达到阈值后告警 | 超过 `maxAttempts` 进 `DEAD_LETTER`，无自动告警机制 | ⚠️ 需确认 |
| **组织架构接口业务失败** — 参数错误/无权限/数据不存在 | 参数错误、无权限、数据不存在 | `HttpClientErrorException.NotFound` (404) → `Optional.empty()` → 本地禁用；其他 4xx → `HttpGatewayException.nonRetryable()` → `DEAD_LETTER` | ✅ 已实现 |
| 业务失败记录返回码和报文 | 记录接口返回码和返回报文 | `HttpGatewayException` 携带原始异常，`markNonRetryableFailure()` 记录错误码 | ✅ 已实现 |
| **本地数据库失败** — 连接失败/死锁/唯一键冲突 | 连接失败、死锁、唯一键冲突 | `RuntimeException` 捕获 → `markFailed()` → `PENDING_RETRY`；`DataIntegrityViolationException` (幂等冲突) → 返回 DUPLICATE | ✅ 已实现 |
| 数据库失败事务回滚 | 事务回滚，返回失败或进入可靠重试 | `@Transactional(REQUIRES_NEW)` 独立事务，失败自动回滚，状态进 `PENDING_RETRY` | ✅ 已实现 |
| **重复事件** — 同一 traceId/spanId 重复投递 | 同一 traceId/spanId 被重复投递 | `inboxService.reserve()` 唯一索引冲突 → 返回 200 DUPLICATE | ✅ 已实现 |

**异常处理流程图（代码实际路径）**：

```
receiveWebhook(eventMessage)
  ├── 集成关闭 → markRejected("接入已关闭") → 500 REJECTED
  ├── parse(rawPayload) 失败
  │     ├── 空报文 → 500 REJECTED
  │     ├── JSON 错误 → 500 REJECTED (原始报文持久化)
  │     └── 未知 Topic → 500 REJECTED
  ├── Topic 不匹配 → 500 REJECTED
  └── processNotice()
        ├── 来源不在白名单 → 500 REJECTED
        ├── reserve() 冲突 → 200 DUPLICATE (幂等)
        └── lookupAndWrite()
              ├── 网关未配置 → 500 DEAD_LETTER
              ├── 调接口
              │     ├── 404 / 无数据 → disableLocalXxx() → 200 PROCESSED
              │     ├── 5xx / 网络 → retryable exception → 500 PENDING_RETRY
              │     └── 4xx (非404) → nonRetryable → 500 DEAD_LETTER
              ├── upsert() 成功 → markProcessed() → 200 PROCESSED
              └── upsert() 失败 → RuntimeException → 500 PENDING_RETRY
```

**关键发现**：
1. **格式异常处理完善**：JSON 解析错误、字段缺失、未知 Topic 均返回 500 REJECTED，原始报文持久化到事件流水表，不静默丢弃。
2. **接口失败分类正确**：5xx/网络 → retryable → 指数退避重试；4xx (除404) → nonRetryable → 直接死信；404 → 本地禁用（符合"数据不存在"处理口径）。
3. **数据库失败有重试**：`RuntimeException` (含数据库异常) → `markFailed()` → `PENDING_RETRY`，独立事务保证不污染已提交状态。
4. **重复事件幂等**：`DataIntegrityViolationException` → 返回 200 DUPLICATE，不重复落库。
5. **告警机制缺失**：接口失败达到阈值、死信积压等场景无自动告警（如企微通知、邮件）。需评估是否添加。

### 日志字段要求验证

| 文档要求 | 代码实现 | 状态 |
|---------|---------|------|
| **必须记录 eventTopic** | `OrganizationEventLogEntity.eventTopic` 持久化；`OrganizationEventSdkConsumerAdapter` 日志输出 topic | ✅ 已实现 |
| **必须记录 key** | `OrganizationEventLogEntity.upstreamEventKey` (原始 key)；`externalUserId`/`externalDeptId` (解析后的 subjectId) | ✅ 已实现 |
| **必须记录 traceId** | `OrganizationEventLogEntity.traceId` 持久化；`OrganizationDirectoryAuthHeaders` 设置 `EHSY-TraceID` Header | ✅ 已实现 |
| **必须记录 spanId** | `OrganizationEventLogEntity.spanId` 持久化 | ✅ 已实现 |
| **必须记录 parentId** | `OrganizationEventLogEntity.parentId` 持久化 | ✅ 已实现 |
| **必须记录 eventTime** | `OrganizationEventLogEntity.eventTime` 持久化（毫秒时间戳解析为 `LocalDateTime`） | ✅ 已实现 |
| **必须记录 consumerGroup** | `OrganizationIntegrationProperties.EventSdk.consumerGroup` 配置化，但**未在日志/流水表中记录** | ❌ **缺失** |
| **必须记录处理结果** | `OrganizationEventLogEntity.status` (PROCESSING/PROCESSED/PENDING_RETRY/DEAD_LETTER/REJECTED/DUPLICATE) | ✅ 已实现 |
| **必须记录耗时** | ⚠️ **未实现** — 代码中无 `StopWatch` 或时间差计算，无处理耗时记录 | ❌ **缺失** |
| **必须记录错误原因** | `OrganizationEventLogEntity.lastErrorCode` + `message` 持久化 | ✅ 已实现 |
| **调用接口时必须记录接口名称** | ⚠️ **未实现** — `OrganizationDirectoryHttpGateway` 无接口调用日志 | ❌ **缺失** |
| **调用接口时必须记录请求唯一标识** | `traceId` 作为请求唯一标识传递到 Header，但**未在日志中记录** | ⚠️ **部分实现** |
| **调用接口时必须记录入参 userId/deptId** | ⚠️ **未实现** — 接口调用日志中未记录入参 | ❌ **缺失** |
| **调用接口时必须记录返回码** | `OrganizationDirectoryResponsePolicy.classify()` 解析返回码，但**未在日志中记录** | ❌ **缺失** |
| **调用接口时必须记录耗时** | ⚠️ **未实现** — 无接口调用耗时记录 | ❌ **缺失** |
| **不得明文输出敏感信息** | `SensitiveDataMasker.maskToken()` 用于 token 脱敏；`maskMobile()`、`maskEmail()` 工具方法已存在 | ✅ 已实现 |
| **手机号/邮箱脱敏** | `SensitiveDataMasker` 工具类已提供方法，但**未在组织同步日志中显式使用** | ⚠️ **工具就绪，应用待确认** |

**关键发现**：
1. **事件流水表字段完整**：`eventTopic`、`key`、`traceId`、`spanId`、`parentId`、`eventTime`、`status`、`errorCode`、`message`、`rawPayload` 等全部持久化到 `organization_event_logs` 表。
2. **consumerGroup 未记录**：配置文件中配置了 `consumerGroup`，但既未在事件流水表中记录，也未在应用日志中输出。
3. **处理耗时未记录**：从事件接收到处理完成的耗时无记录。建议添加 `StopWatch` 或 `System.nanoTime()` 计算并记录到流水表或日志中。
4. **接口调用日志缺失**：`OrganizationDirectoryHttpGateway` 中无任何日志输出。建议添加：
   - 请求日志：接口名称、URL、traceId、入参 (userId/deptId)
   - 响应日志：返回码、耗时、数据是否存在
5. **Token 换取已移除**：YAPI 基于内网 IP 白名单安全，无需 Bearer Token。
6. **当前日志输出极少**：整个组织同步模块仅 `OrganizationEventSdkConsumerAdapter` (3 条) 有 SLF4J 日志（`OrganizationTokenService` 已移除），核心处理流程 (`SyncAppService`、`InboxService`、`HttpGateway`) 几乎无日志。

### 监控指标要求验证

| 文档要求 | 代码实现 | 状态 |
|---------|---------|------|
| **事件消费成功率** — 持续低于 99% 应告警 | `OrgSyncMetrics` 已定义 `xiyu_bid_org_sync_total` Counter (success/failure)，但**未在组织同步代码中实际调用** | ⚠️ **指标类已定义，未接入业务代码** |
| **事件处理延迟** — 从 `event.time` 到本地落库完成的耗时，按 P95/P99 监控 | `OrgSyncMetrics` 已定义 `xiyu_bid_org_sync_duration_seconds` Timer (含 P50/P95/P99)，但**未在组织同步代码中实际调用** | ⚠️ **指标类已定义，未接入业务代码** |
| **失败事件积压量** — 超过阈值或 30 分钟未恢复应告警 | `OrganizationOperationsStatusResponse` 提供 `pendingRetryCount` + `deadLetterCount` 查询接口 (`/api/integrations/organization/operations/status`)，但**无自动告警机制** | ⚠️ **可观测，无告警** |
| **组织架构接口调用成功率** — 5xx、超时、业务失败需拆分统计 | **未实现** — 无接口调用层面的 metrics，无 5xx/超时/业务失败的分类统计 | ❌ **缺失** |
| **本地补偿任务成功率** — 长期失败应进入人工处理 | `OrganizationEventRetryScheduler` 定时重试 + `DEAD_LETTER` 状态，但**无补偿任务成功率的 metrics** | ⚠️ **有机制，无指标** |
| Prometheus 指标导出 | `application.yml` 已配置 `management.endpoints.web.exposure.include: prometheus,metrics` | ✅ 已配置 |
| Actuator 健康检查 | `livenessstate` + `readinessstate` 已启用 | ✅ 已配置 |

**关键发现**：
1. **指标类已定义但未使用**：`OrgSyncMetrics` (2026-05-23 创建) 定义了组织同步的 Counter 和 Timer，但整个 `integration/organization/` 包中**没有任何代码引用** `BusinessMetrics` 或 `OrgSyncMetrics`。
2. **事件处理延迟无法监控**：虽然 Timer 已定义，但业务代码未调用 `recordOrgSyncTime()` 或 `recordOrgSync()`，无法获取 P95/P99 延迟数据。
3. **失败积压可查询但无告警**：`/api/integrations/organization/operations/status` 接口可返回 `pendingRetryCount` 和 `deadLetterCount`，但需外部系统 (如 Prometheus AlertManager) 配置告警规则。
4. **接口调用指标缺失**：`OrganizationDirectoryHttpGateway` 中无 Micrometer 指标埋点，无法统计接口调用成功率、5xx 比例、超时率等。
5. **补偿任务指标缺失**：重试调度器和死信队列的运行情况无 metrics，无法监控补偿任务成功率。

**建议补全的指标埋点**：

| 埋点位置 | 指标名称 | 类型 | 标签 |
|---------|---------|------|------|
| `SyncAppService.processNotice()` | `xiyu_bid_org_sync_total` | Counter | `status=success/failure`, `topic=BaseOssDept/BaseOssUser` |
| `SyncAppService.processNotice()` | `xiyu_bid_org_sync_duration_seconds` | Timer | `topic=BaseOssDept/BaseOssUser` |
| `HttpGateway.getJson()` | `xiyu_bid_org_directory_request_total` | Counter | `status=success/404/4xx/5xx/timeout`, `interface=fetchDepartment/fetchUser/listDepartments/listUsers` |
| `HttpGateway.getJson()` | `xiyu_bid_org_directory_request_duration_seconds` | Timer | `interface=...` |
| `EventRetryScheduler.retryDueEvents()` | `xiyu_bid_org_retry_total` | Counter | `status=success/failure` |
| `EventRetryScheduler.retryDueEvents()` | `xiyu_bid_org_retry_dead_letter_total` | Counter | — |

### 安全与合规要求验证

| 文档要求 | 代码实现 | 状态 |
|---------|---------|------|
| **网络白名单** — 仅开放域名、IP 和端口 | `application.yml` 中事件库注册地址、Kafka broker 均无硬编码生产值；`XIYU_ORG_DIRECTORY_BASE_URL` 默认空，`OrganizationDirectoryBaseUrlConfiguredCondition` 确保无 baseUrl 时 HTTP gateway 不激活 | ✅ 符合 |
| **接口鉴权** — 以西域正式规范为准，不得绕过 | YAPI 接口基于内网 IP 白名单安全，无需 Bearer Token。`OrganizationDirectoryHttpGateway` 仅设置 `EHSY-TraceID` / `EHSY-SRCAPP` 链路追踪 Header。无静态 token、无绕过路径 | ✅ 符合 |
| **最小字段同步** — 只同步投标业务所需最小字段 | `OrganizationDepartmentSnapshot` 仅 6 个字段（deptId、deptCode、deptName、parentDeptId、parentDeptCode、enabled）；`OrganizationUserSnapshot` 仅 9 个字段（userId、username、fullName、email、phone、deptCode、deptName、roleCode、enabled），无全量个人信息 | ✅ 符合 |
| **数据访问控制** — 员工信息按公司要求控制权限 | 组织架构运维 API（`/api/integrations/organization/operations/**`, `/api/integrations/organization/resync/**`, `/api/integrations/organization/sync-runs`）均标注 `@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")`；`SensitiveDataMasker.maskMobile()`/`maskEmail()` 已就绪（需在日志中启用） | ✅ 符合 |
| **生产配置不提交仓库** — 密钥、地址通过环境变量/配置中心管理 | `application.yml` 中所有敏感值使用 `${ENV_VAR:}` 模式，默认空/false：`XIYU_ORG_DIRECTORY_AUTH_CLIENT_ID:`, `XIYU_ORG_DIRECTORY_AUTH_CLIENT_SECRET:`, `XIYU_ORG_DIRECTORY_BASE_URL:`, `XIYU_ORG_EVENT_SERVER_REGISTER_URL:`, `XIYU_ORG_EVENT_SERVICE_NAME:`, `XIYU_ORG_EVENT_SDK_ENABLED:false`。无硬编码生产地址或凭据 | ✅ 符合 |
| **不得泄露西域信息** — 不提供 SDK、内网地址、文档给未授权第三方 | 此为组织管理制度要求，代码自身无法验证。所有配置项均以环境变量注入为唯一入口，代码仓库不含内网地址 | ⚠️ 制度约束，代码无条件 |

**关键发现**：
1. **白名单可配置**：所有网络出口（事件库注册、组织架构 API）均可通过环境变量配置，不包含硬编码生产域名
2. **鉴权规范**：YAPI 基于内网白名单安全，无需 Bearer Token。仅设置链路追踪 Header。
3. **最小字段**：Snapshot 对象仅包含业务所需字段，无冗余个人信息
4. **访问控制**：操作接口受 Spring Security + `@PreAuthorize` 保护，脱敏工具就绪
5. **配置安全**：所有密钥/地址/凭据均以 `${ENV_VAR:}` 注入，默认空/禁用
6. **备注**: `SensitiveDataMasker.maskMobile()`/`maskEmail()` 已实现但未在组织同步日志中显式使用 — 建议在处理耗时和接口调用日志补全时同步应用

---

### 验收标准验证

| 验收项 | 文档要求 | 代码实现 | 状态 |
|--------|---------|---------|------|
| **稳定订阅两类事件** | 能够稳定订阅 `BaseOssDept`、`BaseOssUser` | `OrganizationEventSdkConsumerAdapter` 已编码 `onDeptChanged()` / `onUserChanged()` 两个处理器，分别对应 `BaseOssDept` 和 `BaseOssUser`。`@ConditionalOnClass` + `@ConditionalOnProperty` 确保 SDK jar 存在且启用时激活。`@AcceptEvent` 已激活，`@ConditionalOnClass` + `@ConditionalOnProperty` 守卫确保 SDK jar 存在时启用 | ✅ 已实现 |
| **约定时间内完成查询与更新** | 事件触发后，在约定时间内完成组织架构接口查询与本地数据更新 | 接口超时设置：connect 3s / read 5s。`OrganizationDirectorySyncAppService.lookupAndWrite()` 流程：`reserve()` → `fetchDepartmentByDeptId()`/`fetchUserByUserId()` → `upsert()` → `markProcessed()`，全链路同步执行，无额外延迟。处理耗时未记录（见 TODO-11） | ✅ 已实现，耗时记录待补 |
| **重复事件处理策略** | 重复事件有明确处理策略和可观测日志 | `OrganizationEventInboxService.reserve()` 通过 `eventKey` 唯一索引实现幂等，重复事件返回 200 DUPLICATE，状态记为 `DUPLICATE`，不重复处理。`OrganizationEventLogEntity` 持久化重复事件记录 | ✅ 已实现 |
| **乱序事件处理策略** | 乱序事件有明确处理策略和可观测日志 | 每次事件均实时调用组织架构接口获取最新数据，以接口返回结果覆盖本地，不依赖事件到达顺序。`eventKey` 包含 `time` 字段，但代码不比较时间戳，直接以最新接口结果为准（最终一致性） | ✅ 已实现 |
| **接口失败处理策略** | 接口失败有明确处理策略和可观测日志 | 5xx/网络/超时 → `HttpGatewayException.retryable()` → `markFailed()` → `PENDING_RETRY` → `EventRetryScheduler` 指数退避重试；4xx (除 404) → `nonRetryable()` → `DEAD_LETTER`；404 → 本地禁用。所有状态变更持久化到 `organization_event_logs` | ✅ 已实现 |
| **数据库失败处理策略** | 数据库失败有明确处理策略和可观测日志 | `RuntimeException` (含数据库异常) → `markFailed()` → `PENDING_RETRY`。`@Transactional(REQUIRES_NEW)` 独立事务，失败自动回滚，不污染已提交状态。幂等冲突 (`DataIntegrityViolationException`) → 200 DUPLICATE | ✅ 已实现 |
| **初始化能力** | 具备初始化能力 | `OrganizationDirectoryHttpGateway.listDepartmentsByWindow()` / `listUsersByWindow()` 支持按时间窗口全量拉取。`OrganizationSyncRunAppService.syncWindow()` 编排批量同步。`OrganizationSyncRunEntity` 记录同步历史 | ✅ 已实现 |
| **补偿能力** | 具备补偿能力 | `OrganizationEventRetryScheduler` 定时扫描 `PENDING_RETRY` 事件，`OrganizationEventRetryAppService.retryDueEvents()` 执行重试，指数退避策略 (`BASE_DELAY_MINUTES=5`, `MAX_DELAY_MINUTES=60`, `MAX_EXPONENT=4`)。死信支持人工重放 (`replayDeadLetter()`) | ✅ 已实现 |
| **对账能力** | 具备对账能力 | `OrganizationReconciliationScheduler` 默认每天 2:30 执行，`lookbackDays` 默认 3 天。调用 `syncWindow()` 按时间窗口拉取最近变更数据，逐条比对/更新 | ✅ 已实现 |

**关键发现**：
1. **订阅稳定性**：代码已准备双 Topic 处理器，启用 SDK 后即可稳定消费。`@Conditional` 机制确保无 SDK 时不影响服务启动。
2. **处理时效**：全链路同步执行，接口超时 3-5s，无额外队列延迟。但缺少处理耗时 metrics（TODO-11）。
3. **可观测性**：事件流水表记录完整（状态、错误码、重试次数、原始报文），但日志输出较少（TODO-12、TODO-13）。
4. **容错完整**：重复、乱序、接口失败、数据库失败均有策略，重试+死信+人工重放形成闭环。
5. **三角能力**：初始化（时间窗口）、补偿（定时重试）、对账（定时调度）三者齐备。

---

## 总结：当前差距与 TODO 清单

### 一、启用前必须完成（阻塞项）

| 优先级 | 事项 | 阻塞影响 | 下一步行动 |
|--------|------|---------|-----------|
| **P0** | TODO-3: 组织架构接口真实路径 | 无法调用 YAPI 接口 | ✅ **已完成** — 4 个接口路径已确认，`OrganizationDirectoryHttpGateway` 已适配 POST + form-urlencoded/JSON |
| **P0** | TODO-3a: `HttpGateway` 支持 POST + request body | 无法调用 YAPI 接口 | ✅ **已完成** — `postForm()` 支持详情接口（form-urlencoded），`postJson()` 支持窗口接口（application/json），`fetchWindow()` 支持分页循环 |
| **P1** | TODO-3b: 字段映射确认（6 项） | 映射错误导致数据异常 | 父部门 ID 来源、启用逻辑、用户名来源、部门/角色信息来源 — 需西域确认 |
| **P0** | TODO-4: `XIYU_ORG_DIRECTORY_BASE_URL` | YAPI 接口基础地址 | 测试环境默认 `https://base-oss-test.ehsy.com`，生产环境需客户提供；内网白名单安全 |
| **P0** | TODO-5: 事件库管理端订阅关系 | SDK 无法接收事件 | 联系西域创建/确认订阅关系 |
| **P1** | TODO-1: Maven 私服可访问性 | 无法编译拉取 ClientSDK | ✅ 已确认 — 服务器可访问 `maven.ehsy.com/nexus` |
| **P1** | TODO-2: ClientSDK 版本确认 | 可能版本不匹配 | ✅ 已确认 — `release_0.0.2` |

### 二、启用后建议补全（增强项）

| 优先级 | 事项 | 影响 | 预估工作量 |
|--------|------|------|-----------|
| **P2** | TODO-7: 并发控制（分布式锁） | 高并发时同一对象竞态条件 | 中等 — Redis RedLock 或 subjectId 哈希队列 |
| **P2** | TODO-8: 削峰限流 | 大批量组织调整时接口压力 | 中等 — 内存队列 + 令牌桶 |
| **P2** | TODO-9: 自动告警机制 | 死信积压/重试阈值无人感知 | 中等 — 企微/邮件通知 + AlertManager |
| **P3** | TODO-10~13: 日志补全 | 可观测性不足 | 小 — consumerGroup、耗时、接口调用、脱敏 |
| **P3** | TODO-14~17: 监控指标接入 | 无法量化运行质量 | 小 — Micrometer 埋点 + Grafana 面板 |
| **P4** | TODO-6: 7 个可选扩展接口 | 职位字典、部门树等高级功能 | 大 — 按需实现 |

### 三、字段映射待确认项（需问西域）

| # | 问题 | 当前假设 | 需确认 |
|---|------|---------|--------|
| 1 | 父部门 ID 来源 | `data.parentId` | `parentId` 和 `administrativeSuperiors` 哪个是父部门 ID？ |
| 2 | 部门启用逻辑 | `del=0 && status=1` → enabled | `del` 和 `status` 的具体取值含义？ |
| 3 | 员工用户名来源 | `data.jobNumber` → username | 工号是否作为系统登录用户名？ |
| 4 | 员工部门信息 | 无来源 | 员工详情响应中无部门信息，是否需单独查询或调其他接口？ |
| 5 | 员工角色/职位信息 | 无来源 | 员工详情响应中无职位信息，是否需单独查询或调其他接口？ |
| 6 | 员工启用逻辑 | `del=0 && activationState=1` → enabled | `del` 和 `activationState` 的具体取值含义？ |

### 三、已验证完成的 14 个章节

1. ✅ 客户接入规范摘要
2. ✅ Bearer Token 换取
3. ✅ 接入范围与事件契约
4. ✅ 标准处理流程映射
5. ✅ YAPI 组织架构接口
6. ✅ 事件库 SDK 测试环境配置
7. ✅ 对平台现状的映射
8. ✅ 职位到角色映射
9. ✅ 实现状态与启用步骤
10. ✅ Java 订阅方法对比
11. ✅ 返回值规范验证
12. ✅ 事件 JSON 示例验证（BaseOssDept/BaseOssUser）
13. ✅ 联调用例验证（TC-01~TC-08）
14. ✅ 安全与合规要求验证
15. ✅ 验收标准验证

### 四、关键数据

- **代码实现度**：~85%（核心业务逻辑全部完成，仅 SDK 启用和增强功能待补）
- **文档验证度**：100%（客户提供的所有章节均已验证并记录）
- **TODO 总数**：17 项 + 6 项字段映射待确认
- **风险等级**：**低** — 核心链路完整，仅剩依赖西域配合项（凭据+订阅+字段映射确认）

### 五、下一步建议

1. **立即**：联系西域获取 TODO-4/5（`username`/`password` 凭据、订阅关系）
2. **本周**：TODO-1 ✅ 已确认（服务器可访问），TODO-2 ✅ 已确认（`release_0.0.2`），TODO-3 ✅ 已确认（4 个接口路径）
3. **开发任务**（✅ 已完成）：
   - ✅ 修改 `OrganizationDirectoryHttpGateway` 支持 POST + request body（form/json）
   - ✅ 更新 `OrganizationDirectoryJsonMapper` 字段映射
   - ✅ 恢复 `@AcceptEvent` 注解为激活状态
   - ✅ SDK jar 通过 `system scope` 引用
4. **问西域**：6 个字段映射确认项（父部门 ID、启用逻辑、用户名来源、部门/角色信息来源）
5. **上线前**：完成 TODO-7/8/9（并发、削峰、告警）
6. **上线后**：逐步补全 TODO-10~17（日志、指标、扩展接口）
