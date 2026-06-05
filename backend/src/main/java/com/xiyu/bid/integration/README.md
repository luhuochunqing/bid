# integration 模块（系统集成）

> 一旦我所属的文件夹有所变化，请更新我。

## 职责

负责第三方系统集成的领域逻辑、凭证管理、连通性探测和客户事件接入。当前支持企业微信（WeCom）集成，并通过 SDK 直连接入西域组织架构事件；泛微 OA 流程触发与回调验签的第一版适配位于 `../workflowform/infrastructure/oa`，由流程表单中心持有用例边界，详见 `docs/WORKFLOW_FORM_CENTER.md` 和 `.wiki/pages/workflow-form-center.md`。遵循六边形架构：domain 定义纯业务规则，application 编排用例，infrastructure 实现外部 I/O，controller 暴露 HTTP 接口。

## 目录结构

```
integration/
├── application/          # 用例编排：连通性探测、凭证加密
│   ├── WeComConnectivityProbe.java
│   ├── WeComCredentialCipher.java
│   └── WeComMockConnectivityProbe.java
├── controller/           # HTTP 控制器（/api/admin/integrations/wecom）
├── domain/               # 纯业务模型，无框架依赖
│   ├── ValidationResult.java
│   ├── WeComConnectivityResult.java
│   ├── WeComCredential.java
│   └── WeComCredentialValidation.java
├── dto/                  # 请求/响应 DTO
│   ├── WeComConnectivityResponse.java
│   ├── WeComIntegrationRequest.java
│   └── WeComIntegrationResponse.java
└── infrastructure/       # JPA 持久化实现
    └── persistence/
        ├── entity/WeComIntegrationEntity.java
        └── repository/WeComIntegrationJpaRepository.java
└── organization/         # 客户组织架构事件库接入（SDK 直连）
    ├── domain/           # 纯核心：事件通知校验、topic 分类、角色映射、同步计划
    ├── application/      # 应用编排：幂等、主数据回查、用户/部门落库协调
    │   ├── OrganizationIntegrationProperties.java
    │   └── OrganizationEventSdkConsumerPort.java
    ├── infrastructure/
    │   ├── sdk/         # SDK 适配（@AcceptEvent 直连）
    │   │   ├── OrganizationEventSdkConsumerAdapter.java
    │   │   └── OrganizationEventSdkResponseMapper.java
    │   ├── client/      # HTTP 网关（YAPI 调用）
    │   └── persistence/ # 组织部门与事件 inbox 持久化
    ├── controller/       # 运维 API（sync run / operations / manual resync）
    └── dto/             # 请求/响应 DTO
```

## 组织架构事件接入口径

- 纯核心：`organization/domain` 只接收显式输入并返回显式结果，不读写数据库、时间、日志或外部 SDK。
- 最新契约：事件库只通知变化，`BaseOssDept` 事件只读取 `data.deptId`，`BaseOssUser` 事件只读取 `data.userId`；事件 `data` 不再作为用户/部门主数据 payload。
- 回查主数据：应用服务收到事件后必须通过客户组织架构主数据接口按 `deptId` / `userId` 回查详情，再写入平台部门、用户、角色映射和数据权限读模型。
- 事件字段：事件日志保留 `traceId`、`spanId`、`parentId`、`eventSource`、`eventTopic`、`time`、`key`、`data.deptId` / `data.userId`，用于幂等、追踪和重放。
- 副作用边界：`OrganizationEventSdkConsumerAdapter` 通过 `@AcceptEvent` 接收 SDK 事件；`OrganizationEventAppService.receiveViaSdk()` 委托 `OrganizationDirectorySyncAppService`；`OrganizationDirectoryHttpGateway` 调用 YAPI；`OrganizationUserSyncWriter` / `OrganizationDepartmentSyncWriter` 负责实体写入；JPA repository 负责事件 inbox、部门和用户状态持久化。
- 安全边界：SDK 路径使用 `@ConditionalOnProperty(xiyu.integrations.organization.event-sdk.enabled=true)` 启停；YAPI 接口基于内网 IP 白名单安全，无需 Bearer Token；HTTP webhook 路径已删除（FR-012），不再维护 HMAC 签名校验。
- 启停开关：`xiyu.integrations.organization.enabled=false` 时，签名通过的事件也会被拒绝并记录为 `REJECTED`，便于生产紧急止血。
- 幂等策略：事件进入业务处理前先写入 `organization_event_logs` 的 `PROCESSING` 占位；重复事件稳定返回成功且标记 duplicate。
- 角色策略：未知外部角色默认降级为 `staff`；只有显式配置 allowlist 的外部角色编码才会映射到 `manager/admin`，避免 webhook 自动提权。
- 保留策略：`xiyu.integrations.organization.event-log-retention-days` 默认 90 天，定时任务按 `received_at` 清理过期事件日志；配置为 `0` 或负数可暂停清理。
- SDK 口径：`com.ehsy.eventlibrary:ClientSDK:release_0.0.2` 通过 `sdk` Maven profile 按需引入（`mvn compile -Psdk`）；SDK 注册参数使用客户文档要求的 `client.register.*` / `client.renewal.*` 配置；`OrganizationEventSdkConsumerAdapter` 接收 `@AcceptEvent` 事件并委托 `OrganizationEventAppService.receiveViaSdk()`；SDK jar 未到货前 adapter 包含占位桩代码，待 jar 就绪后补充 `@AcceptEvent` 注解参数。
- 交付文档：YAPI 契约映射见 `docs/integration/organization-directory-yapi-mapping.md`；部署、重试、对账、手工重同步和 TC-01 到 TC-08 验收见 `docs/integration/organization-directory-runbook.md`。
- 交付文档：YAPI 契约映射见 `docs/integration/organization-directory-yapi-mapping.md`；部署、重试、对账、手工重同步和 TC-01 到 TC-08 验收见 `docs/integration/organization-directory-runbook.md`。
- 外部待办：Maven 私服默认在 `sdk` profile 内按 `https://maven.ehsy.com/nexus/repository/maven-releases/` 配置，必要时可用 `-Dehsy.maven.repository.url=...` 覆盖；SDK 版本固定为 `release_0.0.2`；仍需验证 `com.ehsy.eventlibrary:ClientSDK` jar 可拉取、`@AcceptEvent` 注解参数、YAPI base URL。
- 废弃语义：不得新增或恢复 `org.user.upsert`、`org.department.upsert` 这类直读 payload 的组织主数据语义。

## 泛微 OA 接入口径

- 泛微 OA 不放在通用 `integration` 用例层直接驱动业务，而是由 `workflowform` 模块通过 `OaWorkflowGateway` 端口发起流程。
- `MockOaWorkflowGateway` 只作为受 profile/config 控制的联调占位；真实泛微 HTTP 适配器保留在 `WeaverOaWorkflowGateway`，等待客户接口资料补齐。
- 回调入口为 `/api/integrations/oa/weaver/callback`，由 `OaCallbackVerifier` 做 secret、时间窗和签名校验，再交给流程表单应用服务做幂等和业务应用。
- 审批事实源是 OA，本系统只保存表单实例、OA 实例号、审批结果和业务应用状态。
