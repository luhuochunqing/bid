# Organization Directory Integration Closeout Implementation Plan

> **For Codex:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Current status (2026-05-15):** Implementation is partially completed on the API-only path. Retry, reconciliation, manual resync, YAPI HTTP shell, operations status, and documentation can be implemented now; the real ClientSDK adapter and final YAPI field/auth contract remain blocked until west-side Maven/JAR coordinates, `@AcceptEvent` API, host, token/header names, and field envelope are provided. Do not fake SDK classes or customer endpoints.

**Goal:** Complete the real API-only organization-directory integration by adding the west-side event SDK adapter once its dependency is available, official YAPI authentication and field mapping once frozen, reliable retry, reconciliation, operations entry points, monitoring, and acceptance tests.

**Architecture:** Keep the current `com.xiyu.bid.integration.organization` split. Pure policy stays in `domain` records/classes, application services orchestrate parsing, retry, sync runs, and manual resync, while SDK, HTTP, scheduling, metrics, and JPA remain imperative shell code.

**Tech Stack:** Java 21, Spring Boot 3.3, JPA, Flyway, RestTemplate/MockRestServiceServer, JUnit 5, ArchUnit, Micrometer/Actuator if already available, Vue 3 only for the optional operations card.

---

## Scope

In scope:

- `BaseOssDept` and `BaseOssUser` SDK subscription through west-side ClientSDK.
- Event payload parsing, idempotent event logging, master-data lookup, and local upsert based on `deptId` and `userId`.
- Official YAPI request paths, authentication headers, trace headers, response envelope parsing, and field-level mapping.
- Automatic retry, dead-letter/manual queue state, daily low-traffic reconciliation, and manual resync by `deptId` or `userId`.
- Logs, metrics, admin-facing operations visibility, documentation, and acceptance evidence.

Out of scope:

- OA workflow creation. It is explicitly excluded.
- CRM customer APIs. They need a separate real CRM client module.
- Frontend demo/mock fallback. This plan keeps API-only as the only supported path.

## External Inputs To Freeze Before Coding

- ClientSDK Maven coordinates, private repository URL or JAR delivery process, final `@AcceptEvent` package name, `EventResult` base class name, and required `consumerGroup` naming.
- YAPI endpoint details for:
  - department by `deptId`
  - user by `userId`
  - department time-window list
  - user time-window list
- Authentication contract: token/header/signature names, token lifecycle, source app header, trace header, timestamp/nonce requirements, and production/test host separation.
- Response envelope contract: success code field, data field path, page field names, disabled/not-found semantics, and error-code classification.
- Minimal allowed local fields for user privacy: confirm whether email/mobile are mandatory for login creation or may be blank/masked.
- Production schedule: reconciliation cron, retry max attempts, alert thresholds, and operations owner.

## Existing Assets To Reuse

- Domain event topic mapping: `backend/src/main/java/com/xiyu/bid/integration/organization/domain/OrganizationEventType.java`
- Event parsing and pure policy: `OrganizationEventNoticeJsonReader`, `OrganizationEventNoticeParser`, `OrganizationSyncPolicy`
- Event inbox table and repository: `organization_event_logs`, `OrganizationEventInboxService`
- Local upsert writers: `OrganizationDepartmentSyncWriter`, `OrganizationUserSyncWriter`
- Window sync skeleton: `OrganizationSyncRunAppService`, `OrganizationSyncRunController`
- Generic HTTP gateway skeleton: `OrganizationDirectoryHttpGateway`

## Target Package Layout

```text
backend/src/main/java/com/xiyu/bid/integration/organization/
  domain/
    OrganizationDirectoryLookupContext.java
    OrganizationDirectoryRetryPolicy.java
    OrganizationRetryDecision.java
    OrganizationDirectoryResponsePolicy.java
  application/
    OrganizationEventAppService.java
    OrganizationDirectorySyncAppService.java
    OrganizationEventRetryAppService.java
    OrganizationManualResyncAppService.java
    OrganizationReconciliationAppService.java
  infrastructure/
    client/
      OrganizationDirectoryHttpGateway.java
      OrganizationDirectoryAuthHeaders.java
      OrganizationDirectoryJsonMapper.java
    sdk/
      OrganizationEventSdkConsumerAdapter.java
      OrganizationEventSdkResponseMapper.java
    scheduler/
      OrganizationEventRetryScheduler.java
      OrganizationReconciliationScheduler.java
    metrics/
      OrganizationIntegrationMetrics.java
  controller/
    OrganizationEventWebhookController.java
    OrganizationSyncRunController.java
    OrganizationManualResyncController.java
    OrganizationOperationsController.java
```

## Task 1: Contract Freeze Document

**Files:**

- Create: `docs/integration/organization-directory-yapi-mapping.md`
- Modify: `docs/integration/README.md` if it exists; otherwise add a short link from `docs/API_INTEGRATION.md`

**Step 1: Write the mapping document**

Create a table with these columns:

```markdown
| Capability | Method | Path | Auth | Request Fields | Response Data Path | Local Mapping | Not Found Semantics |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Department detail | TBD | TBD | TBD | deptId | TBD | deptId -> externalDeptId, deptName -> departmentName | disable / pending-confirm |
| User detail | TBD | TBD | TBD | userId | TBD | userId -> externalUserId, userNo -> username | disable / pending-confirm |
| Department window | TBD | TBD | TBD | startAt, endAt, pageNo, pageSize | TBD | list of department snapshots | empty page ends sync |
| User window | TBD | TBD | TBD | startAt, endAt, pageNo, pageSize | TBD | list of user snapshots | empty page ends sync |
```

**Step 2: Record unresolved items**

Add a "Blocking Inputs" section for SDK coordinates and YAPI auth. Do not invent secrets, hosts, or test accounts.

**Step 3: Commit**

```bash
git add docs/integration/organization-directory-yapi-mapping.md docs/API_INTEGRATION.md
git commit -m "docs: freeze organization directory integration contract"
```

## Task 2: Pure Retry And Response Policies

**Files:**

- Create: `backend/src/main/java/com/xiyu/bid/integration/organization/domain/OrganizationDirectoryRetryPolicy.java`
- Create: `backend/src/main/java/com/xiyu/bid/integration/organization/domain/OrganizationRetryDecision.java`
- Create: `backend/src/main/java/com/xiyu/bid/integration/organization/domain/OrganizationDirectoryResponsePolicy.java`
- Test: `backend/src/test/java/com/xiyu/bid/integration/organization/domain/OrganizationDirectoryRetryPolicyTest.java`
- Test: `backend/src/test/java/com/xiyu/bid/integration/organization/domain/OrganizationDirectoryResponsePolicyTest.java`

**Step 1: Write failing retry tests**

```java
@Test
void schedulesExponentialBackoffWithCap() {
    LocalDateTime now = LocalDateTime.parse("2026-05-15T10:00:00");
    OrganizationRetryDecision decision = OrganizationDirectoryRetryPolicy.decide(2, 5, now);

    assertThat(decision.retryable()).isTrue();
    assertThat(decision.nextRetryAt()).isEqualTo(now.plusMinutes(20));
}

@Test
void marksDeadLetterWhenAttemptsExhausted() {
    OrganizationRetryDecision decision = OrganizationDirectoryRetryPolicy.decide(
            5,
            5,
            LocalDateTime.parse("2026-05-15T10:00:00")
    );

    assertThat(decision.retryable()).isFalse();
    assertThat(decision.status()).isEqualTo(OrganizationEventStatus.DEAD_LETTER);
}
```

**Step 2: Add status values**

Modify `OrganizationEventStatus` to include:

```java
PENDING_RETRY,
DEAD_LETTER
```

Keep existing values for migration compatibility.

**Step 3: Implement pure policy**

```java
public final class OrganizationDirectoryRetryPolicy {
    private static final int BASE_DELAY_MINUTES = 5;
    private static final int MAX_DELAY_MINUTES = 60;

    public static OrganizationRetryDecision decide(int retryCount, int maxAttempts, LocalDateTime now) {
        if (retryCount >= maxAttempts) {
            return OrganizationRetryDecision.deadLetter();
        }
        int multiplier = 1 << Math.min(retryCount, 4);
        int delay = Math.min(BASE_DELAY_MINUTES * multiplier, MAX_DELAY_MINUTES);
        return OrganizationRetryDecision.retryAt(now.plusMinutes(delay));
    }
}
```

**Step 4: Implement response classification**

`OrganizationDirectoryResponsePolicy` maps official YAPI response codes into:

- success with data
- not found or disabled
- retryable remote failure
- non-retryable contract/auth failure

**Step 5: Run tests**

```bash
mvn -f backend/pom.xml test -Dtest=OrganizationDirectoryRetryPolicyTest,OrganizationDirectoryResponsePolicyTest
```

Expected: new tests pass.

**Step 6: Commit**

```bash
git add backend/src/main/java/com/xiyu/bid/integration/organization/domain backend/src/test/java/com/xiyu/bid/integration/organization/domain
git commit -m "feat: add organization retry and response policies"
```

## Task 3: Configuration For SDK, YAPI Auth, Retry, And Reconciliation

**Files:**

- Modify: `backend/src/main/java/com/xiyu/bid/integration/organization/application/OrganizationIntegrationProperties.java`
- Modify: `backend/src/main/resources/application.yml`
- Test: `backend/src/test/java/com/xiyu/bid/integration/organization/application/OrganizationIntegrationSettingsResolverTest.java`

**Step 1: Write failing configuration binding test**

Assert these fields bind from Spring properties:

```yaml
xiyu:
  integrations:
    organization:
      enabled: true
      event-sdk:
        enabled: true
        consumer-group: bid-org-consumer-test
      directory:
        base-url: <YAPI_BASE_URL>
        auth-token: <AUTH_TOKEN>
        source-app: <SOURCE_APP>
        trace-header-name: EHSY-TraceID
        source-header-name: EHSY-SRCAPP
        connect-timeout-ms: 3000
        read-timeout-ms: 5000
      retry:
        enabled: true
        max-attempts: 5
        batch-size: 50
      reconciliation:
        enabled: true
        cron: "0 30 2 * * *"
        lookback-days: 3
```

**Step 2: Add nested property classes**

Add `EventSdk`, `Retry`, and `Reconciliation` nested classes. Extend `Directory` with auth/header fields but keep defaults blank so local tests do not leak secrets.

**Step 3: Update application.yml**

Use environment variables only:

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
        auth-token: ${XIYU_ORG_DIRECTORY_AUTH_TOKEN:}
        source-app: ${XIYU_ORG_DIRECTORY_SOURCE_APP:}
```

**Step 4: Run tests**

```bash
mvn -f backend/pom.xml test -Dtest=OrganizationIntegrationSettingsResolverTest
```

**Step 5: Commit**

```bash
git add backend/src/main/java/com/xiyu/bid/integration/organization/application/OrganizationIntegrationProperties.java backend/src/main/resources/application.yml backend/src/test/java/com/xiyu/bid/integration/organization/application/OrganizationIntegrationSettingsResolverTest.java
git commit -m "feat: configure organization sdk and directory integration"
```

## Task 4: Official YAPI HTTP Gateway

**Files:**

- Create: `backend/src/main/java/com/xiyu/bid/integration/organization/domain/OrganizationDirectoryLookupContext.java`
- Create: `backend/src/main/java/com/xiyu/bid/integration/organization/infrastructure/client/OrganizationDirectoryAuthHeaders.java`
- Modify: `backend/src/main/java/com/xiyu/bid/integration/organization/application/OrganizationDirectoryGateway.java`
- Modify: `backend/src/main/java/com/xiyu/bid/integration/organization/infrastructure/client/OrganizationDirectoryHttpGateway.java`
- Modify: `backend/src/main/java/com/xiyu/bid/integration/organization/infrastructure/client/OrganizationDirectoryJsonMapper.java`
- Test: `backend/src/test/java/com/xiyu/bid/integration/organization/infrastructure/client/OrganizationDirectoryHttpGatewayTest.java`

**Step 1: Write failing header/auth test**

```java
server.expect(requestTo("<YAPI_BASE_URL>/users/<USER_ID>"))
        .andExpect(header("EHSY-TraceID", "trace-1"))
        .andExpect(header("EHSY-SRCAPP", "<SOURCE_APP>"))
        .andExpect(header("Authorization", "<AUTH_TOKEN>"))
        .andRespond(withSuccess(successUserJson(), MediaType.APPLICATION_JSON));
```

**Step 2: Change gateway contract**

```java
Optional<OrganizationUserSnapshot> fetchUserByUserId(String userId, OrganizationDirectoryLookupContext context);
Optional<OrganizationDepartmentSnapshot> fetchDepartmentByDeptId(String deptId, OrganizationDirectoryLookupContext context);
List<OrganizationUserSnapshot> listUsersByWindow(LocalDateTime startAt, LocalDateTime endAt, OrganizationDirectoryLookupContext context);
List<OrganizationDepartmentSnapshot> listDepartmentsByWindow(LocalDateTime startAt, LocalDateTime endAt, OrganizationDirectoryLookupContext context);
```

**Step 3: Implement header builder**

`OrganizationDirectoryAuthHeaders` reads properties and returns `HttpHeaders`. It must include trace/source headers and auth token only when configured.

**Step 4: Replace `getForObject`**

Use `RestTemplate.exchange` so headers are always sent. Preserve 404-to-empty only if YAPI confirms 404 means not found.

**Step 5: Tighten field mapping**

Update `OrganizationDirectoryJsonMapper` to prefer official YAPI field names first, then retain current aliases as compatibility only. Do not persist unnecessary personal fields.

**Step 6: Run tests**

```bash
mvn -f backend/pom.xml test -Dtest=OrganizationDirectoryHttpGatewayTest
```

**Step 7: Commit**

```bash
git add backend/src/main/java/com/xiyu/bid/integration/organization backend/src/test/java/com/xiyu/bid/integration/organization/infrastructure/client
git commit -m "feat: call organization directory yapi with auth headers"
```

## Task 5: SDK Adapter With `@AcceptEvent`

**Files:**

- Modify: `backend/pom.xml`
- Create: `backend/src/main/java/com/xiyu/bid/integration/organization/infrastructure/sdk/OrganizationEventSdkConsumerAdapter.java`
- Create: `backend/src/main/java/com/xiyu/bid/integration/organization/infrastructure/sdk/OrganizationEventSdkResponseMapper.java`
- Modify: `backend/src/main/java/com/xiyu/bid/integration/organization/application/OrganizationEventAppService.java`
- Test: `backend/src/test/java/com/xiyu/bid/integration/organization/infrastructure/sdk/OrganizationEventSdkResponseMapperTest.java`
- Test: `backend/src/test/java/com/xiyu/bid/integration/organization/application/OrganizationEventAppServiceTest.java`

**Step 1: Confirm SDK dependency**

Do not commit a fake SDK. Add the real dependency only after west-side delivery is confirmed:

```xml
<dependency>
  <groupId>com.ehsy.eventlibrary</groupId>
  <artifactId>ClientSDK</artifactId>
  <version>${eventlibrary.version}</version>
</dependency>
```

If the dependency is not reachable from CI, create a dedicated Maven profile and document the exact CI setting needed before enabling production deployment.

**Step 2: Write response mapper tests**

Assert application response `code=200` maps to SDK result success and `code=500` maps to retryable failure.

**Step 3: Implement adapter**

```java
@Service
@ConditionalOnProperty(prefix = "xiyu.integrations.organization.event-sdk", name = "enabled", havingValue = "true")
public class OrganizationEventSdkConsumerAdapter {
    private final OrganizationEventAppService appService;
    private final OrganizationEventSdkResponseMapper responseMapper;

    @AcceptEvent(eventTopic = "BaseOssDept", consumerGroup = "${xiyu.integrations.organization.event-sdk.consumer-group}")
    public EventInfoRespDto onDeptChanged(String eventMessage) {
        return responseMapper.toSdkResult(appService.receiveSdkEvent("BaseOssDept", eventMessage));
    }

    @AcceptEvent(eventTopic = "BaseOssUser", consumerGroup = "${xiyu.integrations.organization.event-sdk.consumer-group}")
    public EventInfoRespDto onUserChanged(String eventMessage) {
        return responseMapper.toSdkResult(appService.receiveSdkEvent("BaseOssUser", eventMessage));
    }
}
```

Adjust annotation syntax if the actual SDK does not support property placeholders.

**Step 4: Keep HTTP webhook as a controlled fallback**

`OrganizationEventWebhookController` remains for local/dev relay and test harness only. It must delegate to the same `receiveSdkEvent` path so behavior is identical.

**Step 5: Run tests**

```bash
mvn -f backend/pom.xml test -Dtest=OrganizationEventSdkResponseMapperTest,OrganizationEventAppServiceTest,OrganizationEventWebhookControllerTest
```

**Step 6: Commit**

```bash
git add backend/pom.xml backend/src/main/java/com/xiyu/bid/integration/organization backend/src/test/java/com/xiyu/bid/integration/organization
git commit -m "feat: consume organization events through west sdk"
```

## Task 6: Event Processing Refactor For Retryable Reprocessing

**Files:**

- Modify: `backend/src/main/java/com/xiyu/bid/integration/organization/application/OrganizationDirectorySyncAppService.java`
- Modify: `backend/src/main/java/com/xiyu/bid/integration/organization/application/OrganizationEventInboxService.java`
- Modify: `backend/src/main/java/com/xiyu/bid/integration/organization/infrastructure/persistence/repository/OrganizationEventLogRepository.java`
- Test: `backend/src/test/java/com/xiyu/bid/integration/organization/application/OrganizationDirectorySyncAppServiceFailureTest.java`
- Test: `backend/src/test/java/com/xiyu/bid/integration/organization/application/OrganizationEventInboxServiceTest.java`

**Step 1: Write failing retry test**

Create a failed event row with raw payload and `nextRetryAt <= now`. Assert retry processing does not treat it as duplicate and does call the gateway/writer once.

**Step 2: Split reserve from process**

Add two application paths:

- `receiveNewEvent(expectedTopic, rawPayload)` reserves idempotency first.
- `reprocessReservedEvent(eventKey)` loads existing failed row and calls lookup/write without calling `reserve` again.

**Step 3: Store retry metadata**

When a retryable failure occurs:

- status becomes `PENDING_RETRY`
- `retryCount` increments
- `nextRetryAt` comes from `OrganizationDirectoryRetryPolicy`
- error code/message are updated

When attempts are exhausted:

- status becomes `DEAD_LETTER`
- `nextRetryAt` becomes null
- message includes the last compact error

**Step 4: Run tests**

```bash
mvn -f backend/pom.xml test -Dtest=OrganizationDirectorySyncAppServiceFailureTest,OrganizationEventInboxServiceTest
```

**Step 5: Commit**

```bash
git add backend/src/main/java/com/xiyu/bid/integration/organization backend/src/test/java/com/xiyu/bid/integration/organization/application
git commit -m "feat: support retryable organization event reprocessing"
```

## Task 7: Automatic Retry Scheduler

**Files:**

- Create: `backend/src/main/java/com/xiyu/bid/integration/organization/application/OrganizationEventRetryAppService.java`
- Create: `backend/src/main/java/com/xiyu/bid/integration/organization/infrastructure/scheduler/OrganizationEventRetryScheduler.java`
- Modify: `backend/src/main/java/com/xiyu/bid/integration/organization/infrastructure/persistence/repository/OrganizationEventLogRepository.java`
- Test: `backend/src/test/java/com/xiyu/bid/integration/organization/application/OrganizationEventRetryAppServiceTest.java`
- Test: `backend/src/test/java/com/xiyu/bid/integration/organization/infrastructure/scheduler/OrganizationEventRetrySchedulerTest.java`

**Step 1: Write failing batch test**

Assert only due `PENDING_RETRY` rows are selected, batch size is respected, and successful retries become `PROCESSED`.

**Step 2: Add repository query**

Use a pageable query:

```java
List<OrganizationEventLogEntity> findByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
        OrganizationEventStatus status,
        LocalDateTime now,
        Pageable pageable
);
```

If production runs multiple instances, add a claim step that updates status to `PROCESSING` before calling the external API.

**Step 3: Add scheduler**

```java
@Scheduled(fixedDelayString = "${xiyu.integrations.organization.retry.fixed-delay-ms:60000}")
public void retryDueEvents() {
    if (properties.getRetry().isEnabled()) {
        retryAppService.retryDueEvents(LocalDateTime.now());
    }
}
```

**Step 4: Run tests**

```bash
mvn -f backend/pom.xml test -Dtest=OrganizationEventRetryAppServiceTest,OrganizationEventRetrySchedulerTest
```

**Step 5: Commit**

```bash
git add backend/src/main/java/com/xiyu/bid/integration/organization backend/src/test/java/com/xiyu/bid/integration/organization
git commit -m "feat: retry failed organization events automatically"
```

## Task 8: Scheduled Reconciliation And Manual Resync

**Files:**

- Create: `backend/src/main/java/com/xiyu/bid/integration/organization/application/OrganizationManualResyncAppService.java`
- Create: `backend/src/main/java/com/xiyu/bid/integration/organization/infrastructure/scheduler/OrganizationReconciliationScheduler.java`
- Create: `backend/src/main/java/com/xiyu/bid/integration/organization/controller/OrganizationManualResyncController.java`
- Modify: `backend/src/main/java/com/xiyu/bid/integration/organization/application/OrganizationSyncRunAppService.java`
- Modify: `backend/src/main/java/com/xiyu/bid/integration/organization/controller/OrganizationSyncRunController.java`
- Test: `backend/src/test/java/com/xiyu/bid/integration/organization/application/OrganizationManualResyncAppServiceTest.java`
- Test: `backend/src/test/java/com/xiyu/bid/integration/organization/application/OrganizationSyncRunAppServiceTest.java`
- Test: `backend/src/test/java/com/xiyu/bid/integration/organization/controller/OrganizationManualResyncControllerTest.java`

**Step 1: Write failing manual resync tests**

Assert:

- `POST /api/integrations/organization/resync/departments/{deptId}` calls detail lookup and upserts only that department.
- `POST /api/integrations/organization/resync/users/{userId}` calls detail lookup and upserts only that user.
- Missing remote data disables local record or records pending-confirm based on confirmed YAPI semantics.

**Step 2: Implement app service**

Manual resync should create an `organization_sync_runs` row with:

- `runType = MANUAL_DEPARTMENT_RESYNC` or `MANUAL_USER_RESYNC`
- `triggeredBy = current authenticated username`
- one `organization_sync_items` row with success/failure details

**Step 3: Add scheduled reconciliation**

Daily low-traffic scheduler calls:

```java
syncRunAppService.syncWindow("oss", now.minusDays(lookbackDays), now, "RECONCILIATION");
```

Use `xiyu.integrations.organization.reconciliation.enabled=false` by default.

**Step 4: Run tests**

```bash
mvn -f backend/pom.xml test -Dtest=OrganizationManualResyncAppServiceTest,OrganizationSyncRunAppServiceTest,OrganizationManualResyncControllerTest
```

**Step 5: Commit**

```bash
git add backend/src/main/java/com/xiyu/bid/integration/organization backend/src/test/java/com/xiyu/bid/integration/organization
git commit -m "feat: add organization reconciliation and manual resync"
```

## Task 9: Operations Visibility And Metrics

**Files:**

- Create: `backend/src/main/java/com/xiyu/bid/integration/organization/infrastructure/metrics/OrganizationIntegrationMetrics.java`
- Create: `backend/src/main/java/com/xiyu/bid/integration/organization/controller/OrganizationOperationsController.java`
- Modify: `src/views/System/settings/SystemIntegrationPanel.vue`
- Create or modify: `src/api/systemIntegrations.js`
- Test: `backend/src/test/java/com/xiyu/bid/integration/organization/controller/OrganizationOperationsControllerTest.java`
- Test: relevant frontend Vitest file under `src/views/System/settings`

**Step 1: Write backend status test**

Endpoint:

```text
GET /api/integrations/organization/operations/status
```

Response should include:

- enabled flags
- SDK enabled flag
- last successful event time
- failed/pending/dead-letter counts
- last sync run summary

**Step 2: Add Micrometer metrics**

Track:

- `organization.event.consumed`
- `organization.event.processed`
- `organization.event.failed`
- `organization.event.retry.succeeded`
- `organization.event.retry.dead_letter`
- `organization.directory.http.latency`
- `organization.directory.http.failure`

Only log masked personal fields.

**Step 3: Replace Coming Soon card**

Replace only the organization card in `SystemIntegrationPanel.vue` with a real compact operations card:

- enabled status
- pending retry count
- last sync run
- buttons for window sync and manual resync

Keep CRM as Coming Soon.

**Step 4: Run tests**

```bash
mvn -f backend/pom.xml test -Dtest=OrganizationOperationsControllerTest
npm test -- src/views/System/settings
```

**Step 5: Commit**

```bash
git add backend/src/main/java/com/xiyu/bid/integration/organization src/views/System/settings src/api
git commit -m "feat: expose organization integration operations status"
```

## Task 10: Flyway And Data Model Hardening

**Files:**

- Create: `backend/src/main/resources/db/migration/V_next__organization_retry_operations.sql`
- Create: `backend/src/main/resources/db/migration-mysql/V_next__organization_retry_operations.sql`
- Create matching rollback files under `backend/src/main/resources/db/rollback/`
- Modify: `backend/src/main/java/com/xiyu/bid/integration/organization/infrastructure/persistence/entity/OrganizationEventLogEntity.java`
- Modify: `backend/src/main/java/com/xiyu/bid/integration/organization/infrastructure/persistence/entity/OrganizationSyncRunEntity.java`

**Step 1: Add schema for operations**

Add only columns not already present, for example:

```sql
ALTER TABLE organization_event_logs ADD COLUMN retry_locked_at TIMESTAMP NULL;
ALTER TABLE organization_event_logs ADD COLUMN retry_owner VARCHAR(100);
ALTER TABLE organization_sync_runs ADD COLUMN window_start_at TIMESTAMP NULL;
ALTER TABLE organization_sync_runs ADD COLUMN window_end_at TIMESTAMP NULL;
```

Use the actual next Flyway version after checking existing migrations.

**Step 2: Add indexes**

```sql
CREATE INDEX idx_org_event_logs_retry_claim
  ON organization_event_logs(status, next_retry_at, retry_locked_at);
```

**Step 3: Run migration tests**

```bash
mvn -f backend/pom.xml test -Dtest=*Migration*
```

If this repository has no migration-specific tests, run:

```bash
mvn -f backend/pom.xml test -Dtest=OrganizationEventInboxServiceTest
```

**Step 4: Commit**

```bash
git add backend/src/main/resources/db backend/src/main/java/com/xiyu/bid/integration/organization/infrastructure/persistence/entity
git commit -m "feat: harden organization retry persistence"
```

## Task 11: Documentation And Deployment Runbook

**Files:**

- Create: `docs/integration/organization-directory-runbook.md`
- Modify: `TODO.md`
- Modify: `CHANGELOG.md`

**Step 1: Document runtime configuration**

Include:

- required environment variables
- SDK consumer group naming
- event topics
- YAPI endpoint names
- retry behavior
- reconciliation schedule
- manual recovery commands
- alert thresholds

**Step 2: Document acceptance checklist**

Use the PDF acceptance cases:

- TC-01 SDK startup registration
- TC-02 department event
- TC-03 user event
- TC-04 duplicate event
- TC-05 timeout/5xx retry
- TC-06 missing field
- TC-07 initialization
- TC-08 reconciliation

**Step 3: Update TODO**

Mark organization-directory implementation as in progress or done only after tests and west-side UAT evidence exist. Do not mark CRM done.

**Step 4: Commit**

```bash
git add docs/integration/organization-directory-runbook.md TODO.md CHANGELOG.md
git commit -m "docs: add organization integration runbook"
```

## Task 12: Final Verification And Self Review

**Files:**

- No production files unless review finds issues.

**Step 1: Run focused backend tests**

```bash
mvn -f backend/pom.xml test -Dtest='com.xiyu.bid.integration.organization..*Test'
```

Expected: all organization integration tests pass.

**Step 2: Run architecture gates**

```bash
mvn -f backend/pom.xml test -Dtest=FPJavaArchitectureTest,MaintainabilityArchitectureTest
```

Expected: pass. Pure retry/response policy remains in `domain`; SDK, HTTP, schedulers, metrics remain outside pure core.

**Step 3: Run full backend tests**

```bash
mvn -f backend/pom.xml test
```

Expected: pass or report unrelated existing baseline failures explicitly.

**Step 4: Run frontend build if operations card changed**

```bash
npm run build
```

Expected: pass.

**Step 5: Run real API smoke in Codex worktree**

```bash
export XIYU_DEV_CONFIRMED=1
npm run agent:up
npm run agent:status
```

Then trigger one department event and one user event in the agreed test environment. Verify local tables and operations status.

**Step 6: Self-review checklist**

- Event payload `data` is not treated as master data.
- Same event duplicate returns success and does not duplicate writes.
- Failed event is retryable and not swallowed as duplicate.
- YAPI calls include trace/source/auth headers.
- Personal fields are not over-logged.
- Manual resync works by `deptId` and `userId`.
- CRM and OA remain out of scope.

**Step 7: Final commit**

```bash
git status --short
git commit -m "feat: complete organization directory integration"
```

## Rollout Plan

1. After the ClientSDK and YAPI contract are frozen, deploy to test with `XIYU_ORG_SYNC_ENABLED=true`, SDK enabled, test consumer group, and the customer-provided YAPI test host.
2. Run TC-01 through TC-08 with west-side integration owner present.
3. Import or reconcile the initial data window before enabling production event consumption.
4. Enable production SDK consumer group during a controlled low-traffic window.
5. Monitor event success rate, processing latency, pending retry count, and directory HTTP failures for the first 48 hours.

## Acceptance Criteria

- SDK subscription for `BaseOssDept` and `BaseOssUser` is active in test and production configuration after west-side ClientSDK delivery.
- Department and user events trigger YAPI detail lookup and local upsert/disable behavior.
- Duplicate, malformed, failed, and out-of-order events have deterministic status and logs.
- Retry job recovers transient YAPI/database failures and moves exhausted items to dead letter.
- Daily reconciliation can sync the last 1-3 days and write run/item evidence.
- Operations can manually resync a single `deptId` or `userId`.
- Backend organization tests, architecture gates, and, if touched, frontend build pass.

## Pure Core And Imperative Shell

Pure core:

- `OrganizationEventNoticeParser`
- `OrganizationSyncPolicy`
- `OrganizationDirectoryRetryPolicy`
- `OrganizationDirectoryResponsePolicy`

Imperative shell:

- SDK adapter
- HTTP gateway
- JPA repositories and writers
- scheduled retry/reconciliation jobs
- REST controllers
- metrics/logging
- frontend operations card
