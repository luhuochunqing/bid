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

- [ ] **Step 1: Write RED tests for preview safety**

Add tests asserting: a fully empty candidate with a unique match is `FILLABLE`; a candidate with any one of the five fields populated is `SKIP_EXISTING`; an unmatched empty candidate is `UNRESOLVED`; preview never calls `saveAll`; and the sum of decision counts equals scanned cases.

- [ ] **Step 2: Run RED tests**

Run: `cd backend && mvn test -Dtest=CaseProvenanceBackfillAppServiceTest,CaseProvenanceBackfillBoundaryTest`

Expected: compilation failure because `CaseProvenanceBackfillPlan` and `preview()` do not exist.

- [ ] **Step 3: Implement the immutable plan and preview**

Create focused records/enums in `CaseProvenanceBackfillPlan`. Refactor the service so preview scans all ACTIVE candidates, classifies existing provenance before loading documents, groups only empty-source candidates by project, and returns decisions without persistence. Remove all code paths that create null-valued clearing updates.

- [ ] **Step 4: Run Task 1 tests GREEN**

Run: `cd backend && mvn test -Dtest=CaseProvenanceBackfillAppServiceTest,CaseProvenanceBackfillBoundaryTest`

Expected: zero failures and zero errors.

- [ ] **Step 5: Commit Task 1**

Commit: `refactor(casework): plan provenance backfill without writes`

### Task 2: 生成脱敏审计、清单摘要和守卫式回滚 SQL

**Files:**
- Create: `backend/src/main/java/com/xiyu/bid/casework/application/service/CaseProvenanceBackfillArtifactWriter.java`
- Create: `backend/src/test/java/com/xiyu/bid/casework/application/service/CaseProvenanceBackfillArtifactWriterTest.java`

**Interfaces:**
- Consumes: `CaseProvenanceBackfillPlan`.
- Produces: `ArtifactReceipt(batchId, candidateCount, manifestSha256, jsonlPath, csvPath, rollbackSqlPath)`.
- Produces: UTF-8 JSONL/CSV and SQL files under an explicit output directory.

- [ ] **Step 1: Write RED artifact contract tests**

Use `@TempDir` and a fixed plan. Assert JSONL and CSV contain identical case IDs/decision types, never contain the full response text, include only a bounded evidence excerpt plus SHA-256, and the manifest digest is stable for the same ordered decisions. Assert rollback SQL updates only the five source columns, has `WHERE id = ... AND status = 'ACTIVE'`, and guards every current value written by the batch before restoring NULL.

- [ ] **Step 2: Run RED artifact tests**

Run: `cd backend && mvn test -Dtest=CaseProvenanceBackfillArtifactWriterTest`

Expected: compilation failure because the writer does not exist.

- [ ] **Step 3: Implement deterministic artifact generation**

Write decisions sorted by case ID. Use Jackson for JSONL, RFC-4180 escaping for CSV, `MessageDigest` SHA-256 over the canonical manifest, and SQL string literal escaping. Create files with `CREATE_NEW`; reject a nonempty existing batch directory.

- [ ] **Step 4: Run Task 2 tests GREEN**

Run: `cd backend && mvn test -Dtest=CaseProvenanceBackfillArtifactWriterTest`

Expected: zero failures and zero errors.

- [ ] **Step 5: Commit Task 2**

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

- [ ] **Step 1: Write RED apply and database-guard tests**

Assert apply rejects mismatched batch ID/count/digest before any write; valid receipt writes only `FILLABLE`; repository update contains all five `IS NULL` predicates plus existing ACTIVE and snapshot predicates; update count other than one aborts the per-project transaction.

- [ ] **Step 2: Run RED apply tests**

Run: `cd backend && mvn test -Dtest=CaseProvenanceBackfillBoundaryTest,CaseProvenanceBackfillAdapterTest`

Expected: compilation/assertion failure because receipt validation and all-null guard do not exist.

- [ ] **Step 3: Implement apply validation and guarded persistence**

Compute the canonical digest from the freshly generated plan and compare it using constant-time byte comparison. Reject empty batch IDs and nonmatching counts. Change the repository update query to require all five source fields NULL. Keep project-level `REQUIRES_NEW` transaction and throw on any update count other than one.

- [ ] **Step 4: Run Task 3 tests GREEN**

Run: `cd backend && mvn test -Dtest=CaseProvenanceBackfillBoundaryTest,CaseProvenanceBackfillAdapterTest`

Expected: zero failures and zero errors.

- [ ] **Step 5: Commit Task 3**

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

- [ ] **Step 1: Write RED CLI tests**

Assert no flag performs no work; preview calls only `preview()` and artifact writer; legacy flag throws a message naming the safe preview flag; apply without every receipt field throws before preview or persistence.

- [ ] **Step 2: Run RED CLI tests**

Run: `cd backend && mvn test -Dtest=CaseProvenanceBackfillCommandLineRunnerTest`

Expected: assertion/compilation failure because the new CLI contract does not exist.

- [ ] **Step 3: Implement CLI parsing and fail-closed validation**

Keep parsing local and deterministic. Default artifact root to an explicit argument requirement for production; preview requires `--case-provenance-artifact-dir=<absolute-path>`. Do not accept relative paths. Log only counts, batch ID, digest and artifact paths.

- [ ] **Step 4: Run focused and architecture verification**

Run:

```bash
cd backend
mvn test -Dtest=CaseProvenanceBackfillAppServiceTest,CaseProvenanceBackfillBoundaryTest,CaseProvenanceBackfillArtifactWriterTest,CaseProvenanceBackfillAdapterTest,CaseProvenanceBackfillCommandLineRunnerTest
mvn test -Dtest=ArchitectureTest,FPJavaArchitectureTest,MaintainabilityArchitectureTest
```

Expected: both commands pass with zero failures and zero errors.

- [ ] **Step 5: Execute production read-only preview**

Use the deployed production configuration or an approved production execution host. Capture before/after read-only counts of ACTIVE cases and non-null provenance fields. Run preview with an absolute protected artifact directory. Verify no update SQL is emitted, before/after counts are identical, and audit decision counts close to the scanned total. If production credentials or original document access are unavailable, stop and report the exact missing prerequisite; do not substitute test data.

- [ ] **Step 6: Commit Task 4 and preview evidence**

Commit implementation/docs only. Keep production audit artifacts outside Git because they contain business identifiers; record only aggregate counts and SHA-256 in the plan evidence section.
