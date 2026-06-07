# Change: add-xiyu-org-event-sdk-and-crm-client

## Why
客户对接技术文档（`docs/architecture/泊冉投标系统与西域对接技术相关内容.pdf`）明确要求：组织架构以事件库 SDK 订阅 + 接口回查为主链路；CRM 接入以 HTTPS + Token 方式由投标系统主动调用。当前仓库只有 HTTP 中转灾备入口（组织架构）和单向入向 webhook（CRM），SDK 主链与 CRM 出向客户端均缺失，无法支撑生产联调与上线。本提案为这两块对接定义统一的基线规范，作为后续实现与联调的唯一事实源。

## What Changes
- **ADDED** `organization-event-sync` 能力：定义事件库 `com.ehsy.eventlibrary:ClientSDK` 接入约束、`BaseOssDept`/`BaseOssUser` 订阅契约、回查主数据接口的口径、HTTP 中转灾备入口与 SDK 主链共用的幂等 inbox、上线前全量初始化、日常时间窗补偿对账、失效处置、日志/监控/重试规范。
- **ADDED** `crm-customer-client` 能力：定义 CRM 出向调用客户端口径，覆盖 `applyToken`、`logout`、客户模糊查询、客户负责人查询、菜单树、员工信息、发送消息（企微+站内）7 个接口，统一鉴权、Token 缓存、超时/重试、错误码语义、敏感字段脱敏。
- 本提案**只覆盖组织架构事件订阅与 CRM 出向客户端**，OA 流程创建（`POST /oaWorkflow/createWorkflow` 及回调）**不在本次范围**，按用户要求另起 change。

## Impact
- Affected specs:
  - `organization-event-sync`（新建）
  - `crm-customer-client`（新建）
- Affected code:
  - 后端组织架构模块：`backend/src/main/java/com/xiyu/bid/integration/organization/`
  - 后端 CRM 模块：新增 `backend/src/main/java/com/xiyu/bid/integration/crm/`
  - 配置：`application.yml` 增加 `xiyu.integrations.organization.sdk.*`、`xiyu.integrations.crm.*` 节
  - 依赖：`backend/pom.xml` 引入 `com.ehsy.eventlibrary:ClientSDK:release_0.0.2`（jar 由客户提供后接入私服或本地 lib）
  - 数据库：复用现有 `organization_event_logs`、`organization_departments`、`organization_user_directory` 等表；CRM 客户端为无状态调用，原则上不引入新表，若需 Token 持久缓存再另行评估
- Security:
  - 组织架构：SDK 鉴权（serverRegisterUrl + 服务名 + IP 白名单）、HTTP 中转 HMAC 签名
  - CRM：Authorization Header Token、HTTPS、生产密钥经配置中心或环境变量注入，禁止入库
- 上线依赖（客户侧待办，仍需跟进）：
  - 客户提供 `ClientSDK` jar / 私服坐标
  - 客户提供生产 broker / zk / consumerGroup 命名规则
  - 客户提供组织架构主数据接口的完整 YAPI 字段映射、IP 白名单
  - 客户提供 CRM 7 个接口的生产域名、Token 鉴权方式、错误码字典
