# Implementation Plan: 西域对接 — 组织架构SDK接入

**Branch**: `agent/cursor/organization-sdk-integration` | **Date**: 2026-05-21 | **Spec**: [spec.md](./spec.md)

**Input**: 西域对接 — 组织架构同步SDK接入（spec.md）

---

## Summary

基于泊冉 SDK（`com.ehsy.eventlibrary:ClientSDK:release_0.0.2`）实现 `BaseOssDept` 和 `BaseOssUser` 事件订阅，通过动态换取 Bearer token 调用 YAPI 组织架构接口，幂等落库、自动重试与每日低峰对账，移除 HTTP fallback 路径。

---

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**:
- `com.ehsy.eventlibrary:ClientSDK:release_0.0.2` (待引入 Maven 私服)
- Spring Boot 3.3 + JPA + Flyway + RestTemplate
- Micrometer (Actuator 已有)

**Storage**: MySQL 8.0，Flyway 迁移

**Testing**: JUnit 5 + MockMvc + ArchUnit

**Target Platform**: Linux Server (Spring Boot JAR)

**Performance Goals**: 事件 P95 ≤ 3s，YAPI 调用超时 ≤ 5s

**Constraints**: Bearer token 必须动态换取；不维护 HTTP fallback；日志禁止记录 token、手机号、邮箱完整值

**Scale/Scope**: 单租户，内部系统，事件驱动，同步部门 + 员工数据

---

## Constitution Check

| 原则 | 状态 | 说明 |
|---|---|---|
| FP-Java: Pure Core / Imperative Shell | PASS | `OrganizationSyncPolicy`、`OrganizationDirectoryRetryPolicy` 在 `domain`；SDK adapter、HTTP gateway、Scheduler 在 `infrastructure`/`application` |
| Real-API Only | PASS | Mock 路径已删除；SDK jar 到货后引入真实依赖 |
| TDD | PASS | 已有 27 个测试类覆盖 domain/application/controller/infrastructure 各层；FR-012 移除 fallback 进一步收窄测试范围 |
| Split-First / ≤200行 | PASS | 最大 service `OrganizationDirectorySyncAppService` 161 行，`OrganizationEventInboxService` 待确认行数 |
| Boring Proven Patterns | PASS | RestTemplate + JSON + retry policy 均为标准组合 |
| Secrets Management | PASS | 所有 credential 通过 `application.yml` 环境变量注入，不硬编码 |
| DB Migrations | PASS | Flyway schema 迁移脚本在 `migration-mysql/` |

---

## Project Structure

### Source Code (this feature)

```text
backend/src/main/java/com/xiyu/bid/
├── integration/organization/
│   ├── domain/                               # ✅ 已有
│   │   ├── OrganizationSyncPolicy.java       # 幂等键、topic 路由、角色映射
│   │   ├── OrganizationDirectoryRetryPolicy.java  # 指数退避决策
│   │   ├── OrganizationDirectoryResponsePolicy.java # YAPI 响应分类
│   │   └── OrganizationEventNoticeParser.java    # 纯解析（无副作用）
│   ├── application/                          # ✅ 已有
│   │   ├── OrganizationDirectorySyncAppService.java   # 主编排
│   │   ├── OrganizationEventInboxService.java        # 事件 inbox 状态机
│   │   ├── OrganizationEventRetryAppService.java     # 重试逻辑
│   │   ├── OrganizationManualResyncAppService.java    # 手工重同步
│   │   ├── OrganizationSyncRunAppService.java        # 对账运行
│   │   ├── OrganizationIntegrationProperties.java     # 配置绑定
│   │   └── OrganizationTokenService.java            # 🆕 Bearer token 换取
│   ├── infrastructure/
│   │   ├── client/
│   │   │   ├── OrganizationDirectoryHttpGateway.java    # ✅ 已有
│   │   │   ├── OrganizationDirectoryAuthHeaders.java    # ✅ 已有（改用 TokenService）
│   │   │   └── OrganizationDirectoryJsonMapper.java    # ✅ 已有
│   │   ├── sdk/
│   │   │   └── OrganizationEventSdkConsumerAdapter.java # 🆕 SDK @AcceptEvent 接入
│   │   ├── persistence/                     # ✅ 已有
│   │   └── scheduler/                       # ✅ 已有
│   └── controller/                          # ✅ 已有（移除 webhook 入口）
├── crm/                                     # ✅ 已有（CRM clientId/secret 待填）
└── organization/                             # HTTP fallback 路径待清理
    └── infrastructure/ClientSdkAdapter.java  # 🗑️ 注释桩代码待删除

backend/src/main/resources/
├── application.yml                         # 🆕 填入真实 broker/Kafka/ZK 配置
└── db/migration-mysql/                     # ✅ 已有 schema

docs/integration/
├── organization-directory-yapi-mapping.md   # ✅ 已有（YAPI 字段待冻结）
└── organization-directory-runbook.md          # ✅ 已有
```

---

## What Already Exists

经代码审查，以下已完整实现且测试通过：

| 组件 | 状态 | 测试数 |
|---|---|---|
| `OrganizationSyncPolicy` (domain) | ✅ 完整 | `OrganizationSyncPolicyTest` |
| `OrganizationDirectoryRetryPolicy` | ✅ 完整 | `OrganizationDirectoryRetryPolicyTest` |
| `OrganizationDirectoryResponsePolicy` | ✅ 完整 | `OrganizationDirectoryResponsePolicyTest` |
| `OrganizationDirectorySyncAppService` | ✅ 完整 | `OrganizationDirectorySyncAppServiceTest` + `FailureTest` |
| `OrganizationEventInboxService` | ✅ 完整 | `OrganizationEventInboxServiceTest` |
| `OrganizationEventRetryAppService` | ✅ 完整 | `OrganizationEventRetryAppServiceTest` |
| `OrganizationManualResyncAppService` | ✅ 完整 | `OrganizationManualResyncAppServiceTest` |
| `OrganizationSyncRunAppService` | ✅ 完整 | `OrganizationSyncRunAppServiceTest` |
| `OrganizationDirectoryHttpGateway` | ✅ 完整 | `OrganizationDirectoryHttpGatewayTest` |
| `OrganizationDirectoryAuthHeaders` | ✅ 已有（需改造） | — |
| `OrganizationIntegrationProperties` | ✅ 完整 | `OrganizationIntegrationSettingsResolverTest` |
| Retry Scheduler | ✅ 完整 | `OrganizationEventRetrySchedulerTest` |
| Reconciliation Scheduler | ✅ 完整 | `OrganizationReconciliationSchedulerTest` |
| `CrmAuthService` (CRM token) | ✅ 已有（模式参考） | — |
| 文档（runbook + yapi-mapping） | ✅ 完整 | — |

---

## What Needs to Be Built

### 1. OrganizationTokenService — Bearer token 动态换取

**现状**: `OrganizationDirectoryAuthHeaders` 直接读配置中的静态 `authToken`。
**目标**: 改为调用 `applyToken` 接口换取 Bearer token，按 `expiresIn` 自动续期。

设计参考 `CrmAuthService`，模式完全一致（applyToken → cache → auto-renew）。

```text
POST /auth/applyToken
Body: { clientId, clientSecret }
Response: { access_token, expires_in }
```

**实现要点**:
- `OrganizationTokenService` 持有 `RestTemplate` + `OrganizationIntegrationProperties`
- `getValidToken(): String` — token 有效时直接返回，过期前按比例续期
- `applyToken(): OrganizationToken` — 调用 `POST /auth/applyToken`
- 连续失败达到冷却阈值（`tokenCoolDownRetries`）后进入 `cooldown`
- `OrganizationDirectoryAuthHeaders` 改为注入 `OrganizationTokenService`，调用 `getValidToken()` 填入 `Authorization: Bearer {token}`

### 2. OrganizationEventSdkConsumerAdapter — SDK 接入

**现状**: `ClientSdkAdapter` 在 `organization/` 包下，是注释桩。
**目标**: 在 `integration/organization/infrastructure/sdk/` 实现真实接入。

```java
@ConditionalOnProperty(prefix = "xiyu.integrations.organization.event-sdk", name = "enabled", havingValue = "true")
public class OrganizationEventSdkConsumerAdapter {
    @AcceptEvent(eventTopic = "BaseOssDept", consumerGroup = "${xiyu.integrations.organization.event-sdk.consumer-group}")
    public EventInfoRespDto onDeptChanged(String eventMessage) {
        return handle("BaseOssDept", eventMessage);
    }
    @AcceptEvent(eventTopic = "BaseOssUser", consumerGroup = "${xiyu.integrations.organization.event-sdk.consumer-group}")
    public EventInfoRespDto onUserChanged(String eventMessage) {
        return handle("BaseOssUser", eventMessage);
    }
}
```

- `@AcceptEvent` 包名和 `EventResult` 类型待 SDK jar 到货后确认
- handler 委托 `OrganizationEventAppService.receiveViaSdk(topic, eventMessage)`
- 返回 `EventInfoRespDto`（`EventResult` 子类），`code=200` 成功，`code=500` 重试

### 3. Maven pom.xml — SDK 依赖引入

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

- 需西域提供 Maven 私服地址（`<repository>` 配置）
- SDK 到货前 CI 可配置 `<scope>provided</scope>` 或单独 Maven profile

### 4. HTTP Fallback 清理

**待移除**:
- `organization/infrastructure/ClientSdkAdapter.java` — 整文件删除
- `organization/application/EventSyncService.java` — 整文件删除（如果只服务 HTTP fallback）
- `integration/organization/controller/OrganizationEventWebhookController` — HTTP 入口废弃
- `DisabledOrganizationDirectoryGateway.java` — HTTP fallback bean
- `integration/organization/infrastructure/persistence/OrganizationEventWebhookSignatureVerifier` — HMAC 校验废弃
- 路由：`/api/integrations/organization/events` — 从 `application.yml` 中删除
- 环境变量 `XIYU_ORG_WEBHOOK_SECRET` — 标记废弃但暂保留（向后兼容）

**待确认**: `OrganizationEventWebhookController` 是否还有联调测试用途？如是，保留 controller 但降级为 `dev` profile。

### 5. application.yml — 配置填入真实值

```yaml
xiyu:
  integrations:
    organization:
      enabled: ${XIYU_ORG_SYNC_ENABLED:false}
      event-sdk:
        enabled: ${XIYU_ORG_EVENT_SDK_ENABLED:false}
        consumer-group: ${XIYU_ORG_EVENT_CONSUMER_GROUP:bid-org-consumer-test}
      directory:
        base-url: ${XIYU_ORG_DIRECTORY_BASE_URL:}
        # Bearer token 改由 OrganizationTokenService 动态换取，不再读 authToken
        # authToken 字段保留但标记废弃
      retry:
        enabled: ${XIYU_ORG_RETRY_ENABLED:true}
        max-attempts: ${XIYU_ORG_RETRY_MAX_ATTEMPTS:5}
        batch-size: ${XIYU_ORG_RETRY_BATCH_SIZE:50}
        fixed-delay-ms: ${XIYU_ORG_RETRY_FIXED_DELAY_MS:60000}
      reconciliation:
        enabled: ${XIYU_ORG_RECONCILIATION_ENABLED:false}
        cron: ${XIYU_ORG_RECONCILIATION_CRON:0 30 2 * * *}
        lookback-days: ${XIYU_ORG_RECONCILIATION_LOOKBACK_DAYS:3}
```

### 6. YAPI 字段映射冻结

文档 `organization-directory-yapi-mapping.md` 中以下项待西域确认后更新：
- `BaseOssDept` 事件 key 字段名（`deptId` 还是其他）
- 员工 `externalUserId` 来源字段（`userId` vs `id`）
- 禁用/查无语义（直接禁用 vs pending-confirm）
- 鉴权 header 名称（`Authorization: Bearer` 还是 `EHSY-Token`）

---

## Task 1: OrganizationTokenService

**Files**:
- Create: `backend/src/main/java/com/xiyu/bid/integration/organization/application/OrganizationTokenService.java`
- Create: `backend/src/main/java/com/xiyu/bid/integration/organization/domain/OrganizationToken.java`
- Create: `backend/src/main/java/com/xiyu/bid/integration/organization/domain/OrganizationTokenCache.java`
- Create: `backend/src/test/java/com/xiyu/bid/integration/organization/application/OrganizationTokenServiceTest.java`
- Modify: `backend/src/main/java/com/xiyu/bid/integration/organization/infrastructure/client/OrganizationDirectoryAuthHeaders.java`

**Steps**:

1. `OrganizationToken` record: `accessToken`, `expiresInSeconds`, `acquiredAt`
2. `OrganizationTokenCache`: 内存缓存 + 过期判断（参考 `CrmTokenCache`）
3. `OrganizationTokenService`: `getValidToken()` 调用 `applyToken` → 缓存；连续失败 `≥ tokenCoolDownRetries` 后进入 `cooldown`
4. `OrganizationDirectoryAuthHeaders`: 注入 `OrganizationTokenService`，用 `getValidToken()` 填 `Authorization: Bearer {token}`，不再读 `authToken` 配置

**验收**: `OrganizationTokenServiceTest` 覆盖 token 有效 / 过期续期 / cooldown 三种路径。

---

## Task 2: SDK Adapter

**Files**:
- Modify: `backend/pom.xml`
- Create: `backend/src/main/java/com/xiyu/bid/integration/organization/infrastructure/sdk/OrganizationEventSdkConsumerAdapter.java`
- Create: `backend/src/main/java/com/xiyu/bid/integration/organization/infrastructure/sdk/OrganizationEventSdkResponseMapper.java`
- Modify: `backend/src/main/java/com/xiyu/bid/integration/organization/application/OrganizationEventAppService.java` (增加 `receiveViaSdk` 方法)
- Create: `backend/src/test/java/com/xiyu/bid/integration/organization/infrastructure/sdk/OrganizationEventSdkResponseMapperTest.java`
- Delete: `backend/src/main/java/com/xiyu/bid/organization/infrastructure/ClientSdkAdapter.java`

**Steps**:

1. 添加 Maven 私服配置 + `ClientSDK` 依赖
2. 写 `OrganizationEventSdkResponseMapper` 测试（200 → success，500 → retryable）
3. 实现 `OrganizationEventSdkConsumerAdapter` — 注入 `OrganizationEventAppService`，按 `@AcceptEvent` 注册
4. `OrganizationEventAppService` 增加 `receiveViaSdk(topic, eventMessage)` 委托 `receiveWebhook`
5. 删除旧 `ClientSdkAdapter` 注释桩
6. 注解参数待 SDK jar 到货后验证并调整 `@AcceptEvent` 参数

---

## Task 3: HTTP Fallback 清理

**Files**:
- Delete: `backend/src/main/java/com/xiyu/bid/organization/` 整包（ClientSdkAdapter + EventSyncService + OrganizationSyncProperties）
- Modify: `backend/src/main/java/com/xiyu/bid/integration/organization/controller/OrganizationEventWebhookController.java` — 降级为 dev profile 或删除
- Delete: `DisabledOrganizationDirectoryGateway.java`
- Delete: `OrganizationWebhookSignatureVerifier.java` + `OrganizationEventWebhookSignatureVerifierTest.java`
- Modify: `backend/src/main/resources/application.yml` — 移除 `webhookSecret` 配置

**验收**: `mvn test` 全量通过，架构门禁（`FPJavaArchitectureTest`）无新增违规。

---

## Task 4: application.yml 配置补全 + YAPI 字段冻结

**Files**:
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/main/resources/application-dev.yml`（本地联调配置）
- Modify: `docs/integration/organization-directory-yapi-mapping.md`（冻结已知字段，标记待确认项）

---

## Task 5: 端到端验证

**Steps**:

1. `mvn test -Dtest='com.xiyu.bid.integration.organization..*Test'` — 全绿
2. `mvn test -Dtest=FPJavaArchitectureTest,MaintainabilityArchitectureTest` — 全绿
3. `npm run build` — 前端构建通过
4. 本地联调：启动后端，用 SDK 模拟发送 `BaseOssDept` / `BaseOssUser` 事件，验证本地部门/员工表 upsert
5. 验证幂等：重放同一事件两次，第二次返回成功且不写库
6. 验证重试：模拟 YAPI 5xx，验证 `PENDING_RETRY` → 重试 → `PROCESSED`
7. 验证 token 换取：`OrganizationTokenService` 日志中出现 "token acquired"，后续请求带 Bearer token

---

## Complexity Tracking

> 无 Constitution 违规，无需记录。

---

## Dependencies & Blockers

| 依赖项 | 来源 | 状态 | 备注 |
|---|---|---|---|
| `com.ehsy.eventlibrary:ClientSDK` Maven 坐标 | 泊冉 SDK 文档 PDF §4.1 | ⏳ 待西域提供私服地址 | 版本 `release_0.0.2` 已明确 |
| YAPI applyToken 接口路径 | 泊冉 SDK 文档 PDF | ⏳ 待确认 | 假设 `/auth/applyToken` |
| YAPI base URL | 泊冉 SDK 文档 PDF §7 | ⏳ 待西域提供 | 测试环境 `https://yapi.ehsy.com` |
| Kafka broker list + ZK servers | 泊冉 SDK 文档 PDF §4.2 | ⏳ 待西域提供 | 测试 `kafka-01.test.ehsy.com:9094` |
| Bearer token header 名称 | 泊冉 SDK 文档 PDF §7.3 | ⏳ 待确认 | 假设 `Authorization: Bearer` |
| CRM clientId + clientSecret | CRM 集成 | ⏳ 待西域提供 | 独立于组织架构集成 |
