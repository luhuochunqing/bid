# Feature Specification: CRM BaseUrl 配置重构

**Feature Branch**: `010-crm-baseurl-config`

**Created**: 2026-05-27

**Status**: US1+US2 Completed, US3 Deferred (2026-05-28)

**Input**: User description: "CRM BaseUrl 配置重构：1. 拆分 BaseUrl 配置：按服务域拆分为 authBaseUrl、customerBaseUrl、messageBaseUrl 2. 对接 YAPI 真实路径：替换占位路径 3. 接入系统设置：将关键运行时参数纳入 Settings 表管理"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 多域名 CRM 接口调用 (Priority: P1)

作为系统管理员，我希望 CRM 相关接口能够正确路由到不同的域名，以便对接真实的 CRM 多服务环境。

**Why this priority**: 当前所有 CRM 接口共用单一 baseUrl，无法对接客户真实的 3 个不同域名环境（鉴权/组织架构、客户查询、消息推送），导致联调失败。

**Independent Test**: 可通过检查 `CrmProperties` 配置项是否包含 3 个独立的 baseUrl 字段来验证。

**Acceptance Scenarios**:

1. **Given** 系统配置了 3 个不同的 CRM BaseUrl，**When** 调用 applyToken 接口，**Then** 请求应路由到 authBaseUrl
2. **Given** 系统配置了 3 个不同的 CRM BaseUrl，**When** 调用客户模糊查询接口，**Then** 请求应路由到 customerBaseUrl
3. **Given** 系统配置了 3 个不同的 CRM BaseUrl，**When** 调用发送企微消息接口，**Then** 请求应路由到 messageBaseUrl

---

### User Story 2 - YAPI 真实路径对接 (Priority: P1)

作为开发人员，我希望 CRM 客户端使用真实的 YAPI 接口路径，而非占位路径，以便与客户的 CRM 系统正确联调。

**Why this priority**: 当前代码中的 `/auth/applyToken`、`/customer/search` 等路径是占位符，与客户 YAPI 文档中的实际路径不一致，会导致 404 错误。

**Independent Test**: 可通过检查 `CrmHttpClient` 调用的路径是否与 YAPI 文档一致来验证。

**Acceptance Scenarios**:

1. **Given** CRM 客户端初始化完成，**When** 调用 applyToken，**Then** 请求路径应与 YAPI project/406/api/23352 一致
2. **Given** CRM 客户端初始化完成，**When** 调用客户模糊查询，**Then** 请求路径应与 YAPI project/509/api/25338 一致
3. **Given** CRM 客户端初始化完成，**When** 调用发送消息，**Then** 请求路径应与 YAPI project/557/api/35649 一致

---

### User Story 3 - 运行时参数可配置 (Priority: P2)

作为系统管理员，我希望在系统设置页面管理 CRM 运行时参数（Token TTL、重试策略等），而无需修改代码或重启服务。

**Why this priority**: 客户确认需要将 Token 缓存 TTL、重试策略等运行时参数纳入配置管理，提高运维灵活性。

**Independent Test**: 可通过检查 Settings 表是否包含 CRM 相关配置项，以及前端是否有对应的配置界面来验证。

**Acceptance Scenarios**:

1. **Given** 管理员登录系统设置页面，**When** 查看系统集成 → CRM 配置，**Then** 应能看到 Token TTL、重试次数、超时时间等参数
2. **Given** 管理员修改了 CRM Token TTL 配置，**When** 保存后，**Then** 新配置应立即生效，无需重启服务
3. **Given** CRM 配置项已保存到 Settings 表，**When** 系统调用 CRM 接口，**Then** 应使用 Settings 中的配置值

## Functional Requirements

### FR1: BaseUrl 拆分
- **FR1.1**: `CrmProperties` MUST 包含 3 个独立的 BaseUrl 配置项：`authBaseUrl`、`customerBaseUrl`、`messageBaseUrl`
- **FR1.2**: `CrmAuthService` MUST 使用 `authBaseUrl` 进行 Token 申请和登出
- **FR1.3**: `CrmCustomerService` MUST 使用 `customerBaseUrl` 进行客户查询
- **FR1.4**: `CrmMenuService` 和 `CrmEmployeeService` MUST 使用 `authBaseUrl`
- **FR1.5**: `CrmMessageService` MUST 使用 `messageBaseUrl`
- **FR1.6**: 向后兼容：如果未配置拆分后的 BaseUrl，应回退到旧的 `baseUrl` 配置

### FR2: YAPI 真实路径
- **FR2.1**: `CrmAuthService.applyToken()` MUST 调用 YAPI project/406/api/23352 对应的路径
- **FR2.2**: `CrmAuthService.logout()` MUST 调用 YAPI project/406/api/23370 对应的路径
- **FR2.3**: `CrmCustomerService.searchCustomers()` MUST 调用 YAPI project/509/api/25338 对应的路径
- **FR2.4**: `CrmCustomerService.getCustomerContacts()` MUST 调用 YAPI project/509/api/25259 对应的路径
- **FR2.5**: `CrmMenuService.getMenuTree()` MUST 调用 YAPI project/406/api/35642 对应的路径
- **FR2.6**: `CrmEmployeeService.getEmployeeByToken()` MUST 调用 YAPI project/406/api/23358 对应的路径
- **FR2.7**: `CrmMessageService.sendMessages()` MUST 调用 YAPI project/557/api/35649 对应的路径

### FR3: 系统设置集成
- **FR3.1**: Settings 表/实体 MUST 支持 CRM 运行时参数存储
- **FR3.2**: 可配置参数包括：`tokenCacheTtlSeconds`、`maxRetries`、`retryBaseDelayMs`、`connectTimeoutMs`、`readTimeoutMs`
- **FR3.3**: `CrmProperties` MUST 支持从 Settings 读取配置（优先级高于 application.yml）
- **FR3.4**: 前端系统设置页面 MUST 增加 CRM 配置卡片
- **FR3.5**: 配置变更 MUST 实时生效（通过 `@RefreshScope` 或事件机制）

## Success Criteria

1. **多域名路由正确性**：3 个不同功能的 CRM 接口分别路由到对应的 BaseUrl，单元测试覆盖率 ≥ 80%
2. **YAPI 路径一致性**：所有 7 个 CRM 接口路径与 YAPI 文档一致，联调通过
3. **配置实时生效**：修改 Settings 中的 CRM 参数后，5 秒内生效，无需重启
4. **向后兼容**：未升级配置的旧环境仍能正常运行

## Key Entities

- `CrmProperties`：CRM 配置属性类
- `Settings`：系统设置实体
- `CrmHttpClient`：CRM HTTP 客户端

## Assumptions

- 客户提供的 YAPI 路径是稳定的，如有变更需另行更新
- **YAPI 路径需联调前确认**：当前仅记录 YAPI 项目/接口 ID，真实 HTTP Path 需查看 YAPI 文档或联调时确认
- Settings 表已有读写能力（参考现有系统设置实现）
- 生产环境使用 HTTPS，开发环境允许 HTTP

## Clarifications

### Session 2026-05-27

- Q: YAPI 真实路径的具体值是什么格式？ → A: 需要查看 YAPI 文档确认每个接口的具体 Path 字段（Option C）

## Dependencies

- 现有 `CrmAuthService`、`CrmCustomerService` 等服务的接口签名保持不变
- 现有 `Settings` 表结构需扩展或复用已有 JSON 配置字段
