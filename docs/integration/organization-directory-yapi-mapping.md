# 组织架构 YAPI 契约映射

本文冻结西域数智化投标管理平台与客户组织架构系统的对接口径。

**最后更新**: 2026-05-21（SDK 接入 + HTTP Fallback 清理完成后）

---

## 架构决策（已冻结）

| 决策 | 结论 | 状态 |
|---|---|---|
| SDK 接入方式 | `@AcceptEvent` 直连，HTTP fallback 已删除（FR-012） | ✅ 冻结 |
| Bearer token 来源 | `POST /auth/applyToken` 动态换取，内存缓存 + 自动续期 | ✅ 冻结 |
| Token 续期策略 | 剩余 10% 生命周期时自动续期；连续失败 3 次进入 60s cooldown | ✅ 冻结 |
| SDK consumer group | 可配置：`XIYU_ORG_EVENT_CONSUMER_GROUP`，默认 `bid-org-consumer-test` | ✅ 冻结 |
| Maven 坐标 | `com.ehsy.eventlibrary:ClientSDK:release_0.0.2` | ✅ 冻结，按客户确认使用 `release_0.0.2` |
| Maven 私服 | `sdk` Maven profile 内默认 `https://maven.ehsy.com/nexus/repository/maven-releases/`，可通过 `-Dehsy.maven.repository.url=...` 覆盖 | ✅ 配置，待 `mvn compile -Psdk` 拉包验证 |
| SDK 接入包 | `infrastructure/sdk/OrganizationEventSdkConsumerAdapter` | ✅ 冻结 |

---

## 事件订阅（已冻结）

| Event Topic | Identifier | Local Entry | SDK Handler |
|---|---|---|---|
| `BaseOssDept` | `data.deptId` | `OrganizationDirectorySyncAppService` | `OrganizationEventSdkConsumerAdapter.onDeptChanged()` |
| `BaseOssUser` | `data.userId` | `OrganizationDirectorySyncAppService` | `OrganizationEventSdkConsumerAdapter.onUserChanged()` |

> 事件 `data` 只作为变更通知标识，不作为主数据 payload。主数据必须通过 YAPI 详情接口回查。

## SDK 注册配置（已冻结）

| SDK 配置项 | 本系统环境变量 | 测试参考值 | 状态 |
|---|---|---|---|
| `client.register.serviceName` | `XIYU_ORG_EVENT_SERVICE_NAME` | `BidSystemOrgConsumer` | ✅ 冻结，生产命名需客户确认 |
| `client.register.serverRegisterUrl` | `XIYU_ORG_EVENT_SERVER_REGISTER_URL` | `http://event-busserver-test.ehsy.com` | ✅ 测试值冻结，生产值由西域提供 |
| `client.register.enableRegister` | `XIYU_ORG_EVENT_ENABLE_REGISTER` | `true` | ✅ 冻结，默认关闭 |
| `client.renewal.initialDelay` | `XIYU_ORG_EVENT_RENEWAL_INITIAL_DELAY` | `3` | ✅ 冻结 |
| `client.renewal.period` | `XIYU_ORG_EVENT_RENEWAL_PERIOD` | `3` | ✅ 冻结 |
| `client.renewal.renewalDuration` | `XIYU_ORG_EVENT_RENEWAL_DURATION_MS` | `3000` | ✅ 冻结，单位毫秒 |

> 生产环境不得直接使用测试 `serverRegisterUrl`、测试 `consumerGroup` 或测试账号。生产值由西域另行提供。

---

## YAPI 鉴权（部分冻结）

| 配置项 | 值 | 状态 |
|---|---|---|
| applyToken 路径 | `/auth/applyToken` | ✅ 冻结 |
| applyToken Body | `{ clientId, clientSecret }` | ✅ 冻结 |
| applyToken Response | `{ code, data: { access_token, expires_in } }` | ✅ 冻结 |
| Bearer token header 名称 | `Authorization`（`Authorization: Bearer {token}`） | ✅ 冻结 |
| authClientId / authClientSecret | 环境变量注入：`XIYU_ORG_DIRECTORY_AUTH_CLIENT_ID/SECRET` | ✅ 冻结 |
| 续期比例 | 剩余生命周期 10% 时续期 | ✅ 冻结 |

---

## YAPI 映射表（待西域确认）

> 已知字段已冻结；待确认字段标注 `[?]`。

| Capability | Method | Path | Auth | Request Fields | Response Data Path | Local Mapping | Not Found Semantics |
|---|---|---|---|---|---|---|---|
| Department detail | GET | `/departments/{deptId}` | Bearer token | `deptId` | `data` | `deptId` -> `externalDeptId`, `deptName` -> `departmentName` | [?] disable vs pending-confirm |
| User detail | GET | `/users/{userId}` | Bearer token | `userId` | `data` | `userId` -> `externalUserId`, [?]`loginId` -> `username` | [?] disable vs pending-confirm |
| Department window | GET | `/departments/window` | Bearer token | `startAt`, `endAt`, `pageNo`, `pageSize` | `data.list` | list of snapshots | empty page ends sync |
| User window | GET | `/users/window` | Bearer token | `startAt`, `endAt`, `pageNo`, `pageSize` | `data.list` | list of snapshots | empty page ends sync |

---

## 字段映射

### 部门（Department）

| Domain Field | Remote Field | Local Field | Status | Notes |
|---|---|---|---|---|
| 幂等主键 | `deptId` | `externalDeptId` | ✅ 冻结 | |
| 部门名称 | `deptName` | `departmentName` | ✅ 冻结 | |
| 父部门引用 | `parentId` [?] | `parentExternalDeptId` | [?] pending confirm | 字段名待确认 |
| 禁用语义 | [?] | `disabled` | [?] pending confirm | 直接禁用 vs pending-confirm |

### 员工（User）

| Domain Field | Remote Field | Local Field | Status | Notes |
|---|---|---|---|---|
| 幂等主键 | `userId` | `externalUserId` | ✅ 冻结 | |
| 用户名 | `loginId` [?] | `username` | [?] pending confirm | 字段名待确认 |
| 全名 | `fullName` [?] | `fullName` | [?] pending confirm | 字段名待确认 |
| 主部门 | [?] | `departmentCode` | [?] pending confirm | 字段名 + 主/兼职语义 |
| 禁用语义 | [?] | `disabled` | [?] pending confirm | 直接禁用 vs pending-confirm |

---

## SDK 待验证项（SDK jar 到货后验证）

| 项目 | 当前状态 | 待验证 |
|---|---|---|
| `@AcceptEvent` 注解包名 | 推测 `com.ehsy.eventlibrary.annotations` | SDK jar 验证 |
| `EventInfoRespDto` 类型 | 推测 `EventResult` 子类 | SDK jar 验证 |
| SDK 注册机制 | `@AcceptEvent` 自动发现 | SDK jar 验证 |
| SDK 启动超时 | SDK 启动后 30s 内完成注册（SC-001） | 联调验证 |

---

## 待西域提供（Blocking）

| 项目 | 影响的 Task | 状态 |
|---|---|---|
| Maven 私服拉包验证 | pom.xml `sdk` profile repository URL | ⏳ 仓库入口已提供；当前 HTTPS 握手被远端关闭，待网络白名单/协议/仓库路径确认 |
| YAPI base URL | `application.yml` | ⏳ 待提供 |
| applyToken clientId + clientSecret | `OrganizationTokenService` | ⏳ 待提供 |
| SDK 生产注册地址、Kafka broker list + ZK servers | SDK 连接配置 | ⏳ 测试值已有，生产值待提供 |
| YAPI 详情接口完整路径 | `organization-directory-yapi-mapping.md` | ⏳ 待提供 |

---

## 历史：HTTP Fallback 清理记录

- HTTP webhook 入口（`/api/integrations/organization/events`）已删除
- `OrganizationWebhookSignatureVerifier`（HMAC 校验）已删除
- `DisabledOrganizationDirectoryGateway` 已删除
- `SecurityConfig` 中 webhook 路由白名单已移除
- 旧 `organization/` 包（`EventSyncService`、`ClientSdkAdapter`、`FullInitService` 等）已删除
