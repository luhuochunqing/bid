# Tasks: 西域组织架构SDK接入

**Input**: Design documents from `/specs/005-org-sdk-integration/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md

**Tests**: 已有 27 个测试类覆盖各层（domain/application/controller/infrastructure），无需生成新测试任务。

**Organization**: 任务按依赖顺序组织。由于已有代码覆盖了 US1/US2/US4/US5/US6 的主体实现，增量工作集中在 US3（TokenService）、SDK 接入（US1+US2 触发点）、HTTP 清理（FR-012）。

## 路径约定

- **后端源码**: `backend/src/main/java/com/xiyu/bid/`
- **后端测试**: `backend/src/test/java/com/xiyu/bid/`
- **Maven**: `backend/pom.xml`
- **配置**: `backend/src/main/resources/application.yml`
- **文档**: `docs/integration/`

---

## Phase 1: 依赖引入（US3/SDK 的前置条件）

**Purpose**: 所有后续任务依赖 SDK 依赖和 TokenService，无上游阻塞。

---

### T001 [P] [US3] 在 pom.xml 添加 Maven 私服配置和 ClientSDK 依赖

**File**: `backend/pom.xml`

**Steps**:
1. 添加 `<repositories>` 配置，指向西域 Maven 私服地址（待西域提供，当前占位 `https://<maven-repo-host>/repository/maven-releases/`）
2. 在 `<properties>` 添加 `<eventlibrary.version>release_0.0.2</eventlibrary.version>`
3. 在 `<dependencies>` 添加 `com.ehsy.eventlibrary:ClientSDK:${eventlibrary.version}`

**验收**: `mvn dependency:resolve -pl .` 成功拉取 ClientSDK（私服就绪后）；若私服未就绪，CI 配置 `<scope>provided</scope>` 使编译不阻塞。

---

### ❌ T002 — 不需要（YAPI 无需 Bearer token）

**结论**: YAPI 部署在 EHSY 内网，基于网络白名单安全，无需 Bearer token 换取。

**现状**:
- `OrganizationDirectoryAuthHeaders` 已实现为 `EHSY-TraceID` / `EHSY-SRCAPP` 模式 ✅
- `OrganizationDirectoryHttpGateway` 已使用 POST + form-urlencoded/JSON ✅
- `OrganizationTokenService`、`OrganizationToken`、`OrganizationTokenCache` **不需要创建**

**验证**: `OrganizationDirectoryHttpGatewayTest` 已覆盖 YAPI 接口调用路径。

---

### T003 [P] [US3] 实现 SDK EventResponseMapper — SDK 响应分类映射

**Files**:
- Create: `backend/src/main/java/com/xiyu/bid/integration/organization/infrastructure/sdk/OrganizationEventSdkResponseMapper.java`
- Create: `backend/src/test/java/com/xiyu/bid/integration/organization/infrastructure/sdk/OrganizationEventSdkResponseMapperTest.java`

**Steps**:
1. 接收 SDK 的 `EventInfoRespDto`（待 SDK jar 到货后确认包名），映射为内部 `ProcessingResult`
2. `code=200` → `SUCCESS`（不重试）
3. `code=500` 或异常 → `RETRYABLE`（触发重试）
4. `code=4xx` → `REJECTED`（不重试）

**验收**: 单元测试覆盖三种返回码路径。

**Dependencies**: T001（ClientSDK 依赖就绪后）

---

## Phase 2: SDK 接入（US1+US2 的触发点）

**Purpose**: 实现 `@AcceptEvent` 消费者，接管 BaseOssDept/BaseOssUser 事件的接收。

---

### T004 [US1+US2] 实现 OrganizationEventSdkConsumerAdapter — SDK 直连接入

**Files**:
- Create: `backend/src/main/java/com/xiyu/bid/integration/organization/infrastructure/sdk/OrganizationEventSdkConsumerAdapter.java`
- Modify: `backend/src/main/java/com/xiyu/bid/integration/organization/application/OrganizationEventAppService.java`（增加 `receiveViaSdk(topic, eventMessage)` 方法）

**Steps**:
1. 在 `OrganizationEventAppService` 增加 `receiveViaSdk(String topic, String eventMessage)` 方法，委托现有 `receiveWebhook(topic, eventMessage)`
2. 实现 `OrganizationEventSdkConsumerAdapter`：
   - `@ConditionalOnProperty(prefix = "xiyu.integrations.organization.event-sdk", name = "enabled", havingValue = "true")`
   - `@AcceptEvent(eventTopic = "BaseOssDept", consumerGroup = "${xiyu.integrations.organization.event-sdk.consumer-group}")`
   - `@AcceptEvent(eventTopic = "BaseOssUser", consumerGroup = "${xiyu.integrations.organization.event-sdk.consumer-group}")`
   - handler 调用 `OrganizationEventAppService.receiveViaSdk()`
   - 返回 `EventInfoRespDto`（`EventResult` 子类），`code=200` 成功，`code=500` 重试
3. `@AcceptEvent` 包名和 `EventResult` 类型待 SDK jar 到货后验证并调整

**验收**: SDK 启动后 30 秒内完成服务注册（SC-001）。

**Dependencies**: T001（ClientSDK 依赖就绪后）、T003

---

### T005 [P] [US1+US2] application.yml 配置填入真实值

**Files**:
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/main/resources/application-dev.yml`

**Steps**:
1. 填入 SDK 配置：`xiyu.integrations.organization.event-sdk.enabled`、`consumer-group`
2. 填入 YAPI 配置：`base-url`、`clientId`、`clientSecret`
3. 移除 `webhookSecret` 配置（HTTP fallback 清理后）
4. Bearer token 改由 `OrganizationTokenService` 动态换取，`authToken` 配置保留但标记废弃

**验收**: 本地联调启动无配置缺失告警。

---

## Phase 3: HTTP Fallback 清理（FR-012）

**Purpose**: 移除 HTTP webhook 入口和旧桩代码，统一为 SDK 唯一路径。

---

### T006 [FR-012] 删除 HTTP Fallback 路径

**Files to Delete**:
- `backend/src/main/java/com/xiyu/bid/organization/infrastructure/ClientSdkAdapter.java`
- `backend/src/main/java/com/xiyu/bid/organization/application/EventSyncService.java`
- `backend/src/main/java/com/xiyu/bid/organization/infrastructure/persistence/OrganizationEventWebhookSignatureVerifier.java`
- `backend/src/main/java/com/xiyu/bid/integration/organization/infrastructure/persistence/OrganizationEventWebhookSignatureVerifier.java`
- `backend/src/main/java/com/xiyu/bid/integration/organization/infrastructure/DisabledOrganizationDirectoryGateway.java`

**Files to Modify**:
- `backend/src/main/java/com/xiyu/bid/integration/organization/controller/OrganizationEventWebhookController.java` — 整文件删除（controller 入口废弃）
- `backend/src/main/resources/application.yml` — 移除 `/api/integrations/organization/events` 路由和 `webhookSecret` 配置

**Steps**:
1. 删除上述文件
2. 确认 `organization/` 包下无其他引用后，删除整个 `backend/src/main/java/com/xiyu/bid/organization/` 目录
3. 删除 `OrganizationEventWebhookSignatureVerifierTest.java`（若存在）
4. 确认无 import 残留：`grep -r "organization/infrastructure/ClientSdkAdapter" backend/src/`

**验收**: `mvn test` 全量通过，`FPJavaArchitectureTest` 无新增违规。

**Dependencies**: T004（SDK 接入就绪后可安全删除）

---

## Phase 4: 文档冻结与验收

**Purpose**: 收口 YAPI 字段映射和端到端验证。

---

### T007 [P] 冻结 YAPI 字段映射文档

**Files**:
- Modify: `docs/integration/organization-directory-yapi-mapping.md`

**Steps**:
1. 冻结已知字段：部门 `deptId`、`deptName`、`parentId`，员工 `userId`、`loginId`、`fullName`
2. 标记待确认项（带 `[?]` 前缀）：
   - `BaseOssDept` 事件 key 字段名（`deptId` vs 其他）
   - 员工 `externalUserId` 来源字段
   - 鉴权 header 名称（`Authorization: Bearer` vs `EHSY-Token`）

**Dependencies**: T005（application.yml 就绪后）

---

### T008 端到端验收

**Steps**:
1. `mvn test -Dtest='com.xiyu.bid.integration.organization..*Test'` — 全绿
2. `mvn test -Dtest=FPJavaArchitectureTest,MaintainabilityArchitectureTest` — 全绿
3. `npm run build` — 前端构建通过
4. 本地联调：SDK 模拟发送 `BaseOssDept` / `BaseOssUser` 事件，验证本地部门/员工表 upsert
5. 验证幂等：重放同一事件两次，第二次返回成功且不写库
6. 验证重试：模拟 YAPI 5xx，验证 `PENDING_RETRY` → 重试 → `PROCESSED`
7. 验证 token 换取：`OrganizationTokenService` 日志中出现 "token acquired"，后续请求带 Bearer token

**Dependencies**: T002、T004、T006

---

## Phase 5: 待西域提供（不阻塞开发，可并行处理）

**Purpose**: 以下项依赖西域提供，不在任务主线上阻塞开发进度。

| 待提供项 | 影响的 Task |
|---|---|
| Maven 私服地址 | T001 |
| YAPI base URL | T005 |
| applyToken clientId + clientSecret | T002、T005 |
| Kafka broker list + ZK servers | T004 |
| `@AcceptEvent` 注解包名（SDK jar 到货后验证） | T004 |
| `EventResult` 类型（SDK jar 到货后验证） | T004 |
| YAPI applyToken 接口路径确认 | T002 |
| Bearer token header 名称确认 | T002 |

---

## Dependencies & Execution Order

### Phase Dependencies

```
Phase 1 (T001–T003)        ← 无外部依赖，可立即开始
Phase 2 (T004–T005)        ← 依赖 Phase 1（T001 ClientSDK）
Phase 3 (T006)              ← 依赖 Phase 2（T004 SDK 接入）
Phase 4 (T007–T008)         ← 依赖 Phase 2–3（T005 配置 + T006 清理）
Phase 5                      ← 外部依赖，不在主线上
```

### Task Dependencies

```
T001 ─┬─→ T002 ──→ T008
      ├─→ T003 ──→ T004 ──→ T005 ──→ T007
      │                               ↓
      └──────────────────────────────→ T006 ──→ T008
```

### Parallel Opportunities

- T001（T001 Maven）完成前，T002（T002 TokenService）和 T003（T003 EventResponseMapper）可并行准备测试和实现代码（不依赖 ClientSDK 编译）
- T007（文档冻结）可与 T006（HTTP 清理）并行
- Phase 5（西域待提供项）全程可并行推进

---

## MVP Scope（增量交付）

**最小可发布增量**：T001 + T002 + T004 + T006 + T008

覆盖：
- Bearer token 动态换取（US3）
- SDK 事件订阅（US1 + US2）
- HTTP fallback 移除（FR-012）

---

## Notes

- `[P]` = 可并行（不同文件，无依赖）
- `[US1]` = 归属用户故事 1（部门变更同步）
- `[US2]` = 归属用户故事 2（员工变更同步）
- `[US3]` = 归属用户故事 3（YAPI 鉴权）
- `[FR-012]` = 功能需求 FR-012（HTTP Fallback 清理）
- 已有 27 个测试类覆盖各层，无需生成新测试任务
- 最大 blocker：Maven 私服地址（com.ehsy.eventlibrary:ClientSDK 依赖）
