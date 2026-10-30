# 案例来源生产安全回填 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建默认只读、只补全空来源、产生可核验审计与守卫式回滚 SQL 的案例来源回填程序，并对生产执行只读预演。

**Architecture:** 将“扫描并作出决策”与“写入”分离。应用服务返回不可变 `BackfillPlan`；preview 只生成审计工件，apply 必须校验同一预演清单摘要后才调用带全空字段和业务快照守卫的持久化端口。CLI 默认 preview，旧清空型参数明确拒绝。

**Tech Stack:** Java 21、Spring Boot 3.2、JPA、Jackson、JUnit 5、AssertJ、Mockito、Maven。

## Global Constraints

- 默认模式为 `preview`，preview 路径不得调用任何更新方法。
- 仅来源五字段全部为空的 ACTIVE 案例可成为 `FILLABLE`。
- 任一来源字段已有值、无法唯一定位或标书不可用时保持原值。
- 禁止评分项标题、正文首句或固定字符串来源兜底。
- apply 必须校验 preview 批次 ID、候选数和 SHA-256，且使用数据库条件更新。
- 当前任务只授权生产 preview，不授权 apply、部署或生产写入。

---

### Task 1: 纯规划器只产生“补齐或保持”决策

**Files:**
- Create: `backend/src/main/java/com/xiyu/bid/casework/application/service/CaseProvenanceBackfillPlan.java`
- Modify: `backend/src/main/java/com/xiyu/bid/casework/application/service/CaseProvenanceBackfillAppService.java`
- Modify: `backend/src/main/java/com/xiyu/bid/casework/application/port/CaseProvenanceBackfillPort.java`
- Test: `backend/src/test/java/com/xiyu/bid/casework/application/service/CaseProvenanceBackfillAppServiceTest.java`
- Test: `backend/src/test/java/com/xiyu/bid/casework/application/service/CaseProvenanceBackfillBoundaryTest.java`

**Interfaces:**
- Produces: `CaseProvenanceBackfillPlan preview()`.
- Produces: `DecisionType { FILLABLE, SKIP_EXISTING, UNRESOLVED, PROJECT_UNAVAILABLE, ORPHAN }`.
- Produces: `Decision` containing candidate identity, decision type, reason, proposed provenance and redacted evidence hash.
- Preserves: candidate queries include every ACTIVE record so `SKIP_EXISTING` is auditable.

- [x] **Step 1: Write RED tests for preview safety**

Add tests asserting: a fully empty candidate with a unique match is `FILLABLE`; a candidate with any one of the five fields populated is `SKIP_EXISTING`; an unmatched empty candidate is `UNRESOLVED`; preview never calls `saveAll`; and the sum of decision counts equals scanned cases.

- [x] **Step 2: Run RED tests**

Run: `cd backend && mvn test -Dtest=CaseProvenanceBackfillAppServiceTest,CaseProvenanceBackfillBoundaryTest`

Expected: compilation failure because `CaseProvenanceBackfillPlan` and `preview()` do not exist.

- [x] **Step 3: Implement the immutable plan and preview**

Create focused records/enums in `CaseProvenanceBackfillPlan`. Refactor the service so preview scans all ACTIVE candidates, classifies existing provenance before loading documents, groups only empty-source candidates by project, and returns decisions without persistence. Remove all code paths that create null-valued clearing updates.

- [x] **Step 4: Run Task 1 tests GREEN**

Run: `cd backend && mvn test -Dtest=CaseProvenanceBackfillAppServiceTest,CaseProvenanceBackfillBoundaryTest`

Expected: zero failures and zero errors.

- [x] **Step 5: Commit Task 1**

Commit: `refactor(casework): plan provenance backfill without writes`

### Task 2: 生成脱敏审计、清单摘要和守卫式回滚 SQL

**Files:**
- Create: `backend/src/main/java/com/xiyu/bid/casework/application/service/CaseProvenanceBackfillArtifactWriter.java`
- Create: `backend/src/test/java/com/xiyu/bid/casework/application/service/CaseProvenanceBackfillArtifactWriterTest.java`

**Interfaces:**
- Consumes: `CaseProvenanceBackfillPlan`.
- Produces: `ArtifactReceipt(batchId, candidateCount, manifestSha256, jsonlPath, csvPath, rollbackSqlPath)`.
- Produces: UTF-8 JSONL/CSV and SQL files under an explicit output directory.

- [x] **Step 1: Write RED artifact contract tests**

Use `@TempDir` and a fixed plan. Assert JSONL and CSV contain identical case IDs/decision types, never contain the full response text, include only a bounded evidence excerpt plus SHA-256, and the manifest digest is stable for the same ordered decisions. Assert rollback SQL updates only the five source columns, has `WHERE id = ... AND status = 'ACTIVE'`, and guards every current value written by the batch before restoring NULL.

- [x] **Step 2: Run RED artifact tests**

Run: `cd backend && mvn test -Dtest=CaseProvenanceBackfillArtifactWriterTest`

Expected: compilation failure because the writer does not exist.

- [x] **Step 3: Implement deterministic artifact generation**

Write decisions sorted by case ID. Use Jackson for JSONL, RFC-4180 escaping for CSV, `MessageDigest` SHA-256 over the canonical manifest, and SQL string literal escaping. Create files with `CREATE_NEW`; reject a nonempty existing batch directory.

- [x] **Step 4: Run Task 2 tests GREEN**

Run: `cd backend && mvn test -Dtest=CaseProvenanceBackfillArtifactWriterTest`

Expected: zero failures and zero errors.

- [x] **Step 5: Commit Task 2**

Commit: `feat(casework): emit auditable provenance backfill artifacts`

### Task 3: apply 必须绑定 preview 清单并使用全空来源守卫

**Files:**
- Modify: `backend/src/main/java/com/xiyu/bid/casework/application/service/CaseProvenanceBackfillAppService.java`
- Modify: `backend/src/main/java/com/xiyu/bid/casework/application/port/CaseProvenanceBackfillPort.java`
- Modify: `backend/src/main/java/com/xiyu/bid/casework/infrastructure/CaseProvenanceBackfillAdapter.java`
- Modify: `backend/src/main/java/com/xiyu/bid/casework/infrastructure/KnowledgeCaseRepository.java`
- Test: `backend/src/test/java/com/xiyu/bid/casework/application/service/CaseProvenanceBackfillBoundaryTest.java`
- Test: `backend/src/test/java/com/xiyu/bid/casework/infrastructure/CaseProvenanceBackfillAdapterTest.java`

**Interfaces:**
- Produces: `ApplyResult apply(BackfillPlan plan, PreviewReceipt expected)`.
- Consumes: batch ID, FILLABLE count and manifest SHA-256 from preview.
- Persists: only `FILLABLE` updates whose ACTIVE/business snapshot/source-all-null conditions still hold.

- [x] **Step 1: Write RED apply and database-guard tests**

Assert apply rejects mismatched batch ID/count/digest before any write; valid receipt writes only `FILLABLE`; repository update contains all five `IS NULL` predicates plus existing ACTIVE and snapshot predicates; update count other than one aborts the per-project transaction.

- [x] **Step 2: Run RED apply tests**

Run: `cd backend && mvn test -Dtest=CaseProvenanceBackfillBoundaryTest,CaseProvenanceBackfillAdapterTest`

Expected: compilation/assertion failure because receipt validation and all-null guard do not exist.

- [x] **Step 3: Implement apply validation and guarded persistence**

Compute the canonical digest from the freshly generated plan and compare it using constant-time byte comparison. Reject empty batch IDs and nonmatching counts. Change the repository update query to require all five source fields NULL. Keep project-level `REQUIRES_NEW` transaction and throw on any update count other than one.

- [x] **Step 4: Run Task 3 tests GREEN**

Run: `cd backend && mvn test -Dtest=CaseProvenanceBackfillBoundaryTest,CaseProvenanceBackfillAdapterTest`

Expected: zero failures and zero errors.

- [x] **Step 5: Commit Task 3**

Commit: `feat(casework): guard provenance backfill apply`

### Task 4: 安全 CLI 与生产只读预演

**Files:**
- Modify: `backend/src/main/java/com/xiyu/bid/bootstrap/CaseProvenanceBackfillCommandLineRunner.java`
- Test: `backend/src/test/java/com/xiyu/bid/bootstrap/CaseProvenanceBackfillCommandLineRunnerTest.java`
- Modify: `backend/src/main/java/com/xiyu/bid/bootstrap/README.md`
- Modify: `backend/src/main/java/com/xiyu/bid/casework/README.md`
- Modify: `docs/exec-plans/active/case-provenance-safe-backfill-implementation.md`

**Interfaces:**
- Preview flag: `--case-provenance-backfill=preview`.
- Apply flags: `--case-provenance-backfill=apply`, batch ID, candidate count, manifest SHA-256 and artifact directory.
- Legacy flag: `--recompute-case-provenance=true` terminates with a safety error.

- [x] **Step 1: Write RED CLI tests**

Assert no flag performs no work; preview calls only `preview()` and artifact writer; legacy flag throws a message naming the safe preview flag; apply without every receipt field throws before preview or persistence.

- [x] **Step 2: Run RED CLI tests**

Run: `cd backend && mvn test -Dtest=CaseProvenanceBackfillCommandLineRunnerTest`

Expected: assertion/compilation failure because the new CLI contract does not exist.

- [x] **Step 3: Implement CLI parsing and fail-closed validation**

Keep parsing local and deterministic. Default artifact root to an explicit argument requirement for production; preview requires `--case-provenance-artifact-dir=<absolute-path>`. Do not accept relative paths. Log only counts, batch ID, digest and artifact paths.

- [x] **Step 4: Run focused and architecture verification**

Run:

```bash
cd backend
mvn test -Dtest=CaseProvenanceBackfillAppServiceTest,CaseProvenanceBackfillBoundaryTest,CaseProvenanceBackfillArtifactWriterTest,CaseProvenanceBackfillAdapterTest,CaseProvenanceBackfillCommandLineRunnerTest
mvn test -Dtest=ArchitectureTest,FPJavaArchitectureTest,MaintainabilityArchitectureTest
```

Expected: both commands pass with zero failures and zero errors.

- [x] **Step 5: Execute production read-only preview**

Use the deployed production configuration or an approved production execution host. Capture before/after read-only counts of ACTIVE cases and non-null provenance fields. Run preview with an absolute protected artifact directory. Verify no update SQL is emitted, before/after counts are identical, and audit decision counts close to the scanned total. If production credentials or original document access are unavailable, stop and report the exact missing prerequisite; do not substitute test data.

- [x] **Step 6: Commit Task 4 and preview evidence**

Commit implementation/docs only. Keep production audit artifacts outside Git because they contain business identifiers; record only aggregate counts and SHA-256 in the plan evidence section.

## Production preview evidence (2026-10-30)

- Environment: `ENV=prod`, host `winbid-01` / `172.16.10.149`; no deployment, service restart or apply.
- Source commit: `efd8e5389`; final preview batch: `prod-preview-20261030-c435d33f5-r3`.
- Before/after database aggregates were identical: ACTIVE `344`, all five provenance fields empty `320`, any provenance present `24`.
- Decision closure: `PROJECT_UNAVAILABLE=300`, `UNRESOLVED=20`, `SKIP_EXISTING=24`, `FILLABLE=0`; total `344`.
- Manifest SHA-256: `adbd0252066b1c8b4f79d51fe251afb9af045542c4db05ae240b15e35ea04c5d`.
- Artifact SHA-256: JSONL `e5310a01d8fa4bc652ebc442b4592585974b26431b2eb5ed949884eb7454a509`; CSV `a3e433465cad288ba8203b7a96e57c8518fd374f1e3e6e0a5bebae62f2a4fe4d`; rollback SQL `ee0644e3f27e8a625011033d3aebe01e69d888db813e648accaac27d4ee6c461`.
- Rollback SQL contains zero UPDATE statements and ends in `ROLLBACK`, matching zero fillable candidates.
- Final preview log contains no organization event SDK, tender event SDK or history replay startup; process exited normally. Production backend remained `active` and health was `UP`.
- An earlier preview attempt generated the same decision manifest but exposed an unrelated `ApplicationReadyEvent` SDK side effect after artifact creation. The maintenance safety properties now force-disable those integrations; only the corrected `r3` batch is acceptance evidence.

---

### Task 5: Canonical Manifest 必须绑定批次且无编码歧义

**Files:**
- Modify: `backend/src/main/java/com/xiyu/bid/casework/application/service/CaseProvenanceBackfillArtifactWriter.java`
- Modify: `backend/src/test/java/com/xiyu/bid/casework/application/service/CaseProvenanceBackfillArtifactWriterTest.java`
- Modify: `backend/src/test/java/com/xiyu/bid/casework/application/service/CaseProvenanceBackfillApplyGuardTest.java`

**Interfaces:**
- Produces: `String manifestSha256(String batchId, CaseProvenanceBackfillPlan plan)`.
- Consumes: 与审计目录一致的显式 `batchId`。
- Guarantees: 固定字段顺序的 canonical JSON；字符串中的 `|`、换行与 `null` 不产生结构碰撞。

- [x] **Step 1: Write RED tests for batch binding and unambiguous encoding**

新增测试：相同计划配不同 batch ID 必须得到不同摘要；构造含 `|`、换行和字符串 `"null"` 的不同计划，摘要必须不同；Apply 使用其他批次 ID 与原摘要时必须零写入失败。

- [x] **Step 2: Run RED tests**

Run: `cd backend && mvn test -Dtest=CaseProvenanceBackfillArtifactWriterTest,CaseProvenanceBackfillApplyGuardTest`

Expected: FAIL，因为现有 `manifestSha256(plan)` 不绑定批次且使用分隔符拼接。

- [x] **Step 3: Implement canonical JSON digest**

用 Jackson 序列化固定顺序的 `LinkedHashMap` 清单：顶层包含 `batchId` 和 `decisions`；每个 decision 显式包含 case/project/type/evidence/proposed 五字段。摘要只接受 `(batchId, plan)`，删除无批次重载，ArtifactReceipt 与 Apply 共用同一实现。

- [x] **Step 4: Run tests GREEN**

Run: `cd backend && mvn test -Dtest=CaseProvenanceBackfillArtifactWriterTest,CaseProvenanceBackfillApplyGuardTest`

Expected: PASS，0 failures / 0 errors。

### Task 6: Apply CLI 必须重新扫描并校验完整 Preview 凭证

**Files:**
- Modify: `backend/src/main/java/com/xiyu/bid/bootstrap/CaseProvenanceBackfillCommandLineRunner.java`
- Modify: `backend/src/main/java/com/xiyu/bid/XiyuBidApplication.java`
- Modify: `backend/src/main/java/com/xiyu/maintenance/CaseProvenancePreviewApplication.java`
- Modify: `backend/src/test/java/com/xiyu/bid/bootstrap/CaseProvenanceBackfillCommandLineRunnerTest.java`
- Modify: `backend/src/test/java/com/xiyu/bid/XiyuBidApplicationTest.java`

**Interfaces:**
- Apply flags: `--case-provenance-backfill=apply`, `--case-provenance-artifact-dir=<absolute-root>`, `--case-provenance-batch-id=<id>`, `--case-provenance-candidate-count=<n>`, `--case-provenance-manifest-sha256=<64-hex>`.
- Produces: 重新执行 `preview()` 后调用 `apply(freshPlan, PreviewReceipt)`；任一参数缺失或非法时零写入。

- [x] **Step 1: Write RED CLI tests**

新增测试：合法 Apply 参数必须先重新 preview 再 apply；缺数量、缺摘要、相对目录、非法 SHA、其他模式均在 preview/apply 前失败；启动根只把显式 preview/apply 路由到 maintenance 上下文。

- [x] **Step 2: Run RED tests**

Run: `cd backend && mvn test -Dtest=CaseProvenanceBackfillCommandLineRunnerTest,XiyuBidApplicationTest`

Expected: FAIL，当前 CLI 拒绝 apply，启动根只识别 preview。

- [x] **Step 3: Implement fail-closed Apply parsing**

解析数值时捕获格式异常并转为明确 `IllegalStateException`。Apply 必须检查批次目录为 `<artifactRoot>/<batchId>` 且 `audit.jsonl`、`audit.csv`、`rollback.sql` 均存在，再重新生成计划并使用人工提供的批次/数量/摘要调用服务；不得从审计文件反序列化拟写值。

- [x] **Step 4: Run tests GREEN**

Run: `cd backend && mvn test -Dtest=CaseProvenanceBackfillCommandLineRunnerTest,XiyuBidApplicationTest`

Expected: PASS，0 failures / 0 errors。

### Task 7: Apply 使用单批次事务并禁止部分成功

**Files:**
- Modify: `backend/src/main/java/com/xiyu/bid/casework/application/service/CaseProvenanceBackfillAppService.java`
- Modify: `backend/src/main/java/com/xiyu/bid/casework/application/port/CaseProvenanceBackfillPort.java`
- Modify: `backend/src/main/java/com/xiyu/bid/casework/infrastructure/CaseProvenanceBackfillAdapter.java`
- Modify: `backend/src/test/java/com/xiyu/bid/casework/application/service/CaseProvenanceBackfillApplyGuardTest.java`
- Modify: `backend/src/test/java/com/xiyu/bid/casework/infrastructure/CaseProvenanceBackfillAdapterMysqlTest.java`

**Interfaces:**
- Produces: `void saveAllIfProvenanceEmpty(List<Update> updates)` 作为一个批次事务。
- Guarantees: 任一 UPDATE 影响行数不是 1 时抛异常并回滚全部项目写入。

- [x] **Step 1: Write RED transaction regression**

真实 MySQL 测试创建两个不同项目的全空案例，在第二条写入前制造来源字段漂移；调用一个包含两条 Update 的批次，断言抛异常且第一条来源字段仍全部为空。

- [x] **Step 2: Run RED test**

Run: `cd backend && mvn test -Dtest=CaseProvenanceBackfillAdapterMysqlTest`

Expected: FAIL，当前服务按项目多次调用 `REQUIRES_NEW`，前序项目可能已提交。

- [x] **Step 3: Implement one batch transaction**

AppService 将全部 FILLABLE Update 按确定顺序汇总后只调用一次端口；Adapter 将事务传播改为单次 `REQUIRED` 边界并在循环内任一更新失败时抛异常，由 Spring 回滚整个调用。

- [x] **Step 4: Run tests GREEN**

Run: `cd backend && mvn test -Dtest=CaseProvenanceBackfillApplyGuardTest,CaseProvenanceBackfillAdapterMysqlTest`

Expected: PASS，0 failures / 0 errors。

### Task 8: 修复收口、文档同步与 PR 更新

**Files:**
- Modify: `backend/src/main/java/com/xiyu/bid/bootstrap/README.md`
- Modify: `backend/src/main/java/com/xiyu/bid/casework/README.md`
- Modify: `backend/src/main/java/com/xiyu/maintenance/README.md`
- Modify: `docs/exec-plans/active/case-provenance-safe-backfill-implementation.md`

**Interfaces:**
- Documents: preview/apply 参数、批次绑定、整批事务和“未授权生产 Apply”边界。

- [x] **Step 1: Run focused verification**

Run: `cd backend && mvn test -Dtest=XiyuBidApplicationTest,CaseProvenanceBackfillCommandLineRunnerTest,CaseProvenanceBackfillApplyGuardTest,CaseProvenanceBackfillArtifactWriterTest,CaseProvenanceSafePreviewTest,CaseProvenanceBackfillAdapterMysqlTest`

- [x] **Step 2: Run architecture and governance gates**

Run: `cd backend && mvn test -Dtest=ArchitectureTest,FPJavaArchitectureTest,MaintainabilityArchitectureTest && cd .. && npm run wiki:check && git diff --check`

- [x] **Step 3: Commit and push through normal hooks**

Commit implementation/tests/docs atomically, then `git push origin HEAD` without bypass flags. Confirm PR #2939 head equals local HEAD and remains mergeable. Production Apply, deployment and restart remain explicitly out of scope.

Verification evidence (2026-10-31): focused preview/apply/MySQL suite `29` passed; architecture suite `43` passed; `npm run wiki:check` passed with `153` pages; `git diff --check` passed. All focused Maven commands used `-Djacoco.skip=true` because an earlier interrupted full-suite run left a corrupt generated `target/jacoco.exec`; no source or behavioral assertion was skipped.
