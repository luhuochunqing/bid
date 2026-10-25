# Project 22 Supplier Performance Evidence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. This repository is operating in single-agent mode, so do not dispatch subagents. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Establish failing business-level tests and a traceable eight-record positive test dataset before choosing the production-code fix for project 22 supplier-performance staircase scoring.

**Architecture:** Exercise the existing pure-core composition (`ScoreTypeClassificationPolicy` → `ScoreItemConstraintExtractor` → `MatchTierPolicy` → `PartialScorePolicy`) with the exact tender rule, then prepare eight qualified performance records through the real performance-import browser workflow. The evidence phase changes no production Java code; its RED outputs decide whether the later fix is classification-only or must introduce an explicit incremental-count scoring rule.

**Tech Stack:** Java 21, JUnit 5, AssertJ, Spring Boot scoreparse domain policies, Vue 3 performance import UI, Excel `.xlsx`, Ego Browser.

## Global Constraints

- Production data and historical scoring state are read-only; test records receive new test-environment IDs.
- A task reaching HTTP 200 or `COMPLETED` is not acceptance unless browser-visible classification and score are correct.
- The exact business table is `1→0`, `2→1`, `8→7`, `10→7` for qualified records.
- The existing 1117 MRO bid remains a negative control and must not be presented as qualified office-supplies evidence.
- No production Java code changes are allowed until Tasks 1–3 establish RED behavior and the positive dataset is auditable.

---

### Task 1: Add the exact project 22 classification RED test

**Files:**
- Modify: `backend/src/test/java/com/xiyu/bid/scoreparse/domain/ScoreTypeClassificationPolicyTest.java`

**Interfaces:**
- Consumes: `ScoreTypeClassificationPolicy.classify(String detail, String scoreTypeGuess)`.
- Produces: A regression assertion that the exact project 22 rule is objectively computable even when the model guesses `SUBJECTIVE`.

- [x] **Step 1: Add the failing test with the exact rule text**

```java
@Test
void classify_project22IncrementalSupplierPerformance_objective() {
    String detail = "供应商具有近三年内（2023年1月1日起至今，供应商具有1项大型集团型国央企电子商城办公用品服务业绩的不得分，"
            + "每增加1个得1分，最高得7分。";

    assertThat(policy.classify(detail, "SUBJECTIVE")).isEqualTo("OBJECTIVE");
}
```

- [x] **Step 2: Run the focused test and capture RED evidence**

Run:

```bash
cd backend
mvn -Dtest=ScoreTypeClassificationPolicyTest#classify_project22IncrementalSupplierPerformance_objective test
```

Expected: FAIL with actual `SUBJECTIVE` and expected `OBJECTIVE`.

- [x] **Step 3: Run existing subjective counterexamples unchanged**

Run:

```bash
cd backend
mvn -Dtest=ScoreTypeClassificationPolicyTest#classify_descriptive_subjective,ScoreTypeClassificationPolicyTest#classify_horizontalComparison_subjective_evenWithNumbers test
```

Expected: PASS. This proves the defect is the missing computable phrase, not a broken global baseline.

### Task 2: Add the complete staircase-scoring RED test

**Files:**
- Create: `backend/src/test/java/com/xiyu/bid/scoreparse/domain/Project22SupplierPerformanceScoringAcceptanceTest.java`

**Interfaces:**
- Consumes: existing `ScoreTypeClassificationPolicy`, `ScoreItemConstraintExtractor`, `MatchTierPolicy`, and `PartialScorePolicy` public APIs.
- Produces: A behavior-level matrix showing whether the current composition implements the first-item-free staircase.

- [x] **Step 1: Add the parameterized business matrix**

```java
package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class Project22SupplierPerformanceScoringAcceptanceTest {

    private static final BigDecimal WEIGHT = new BigDecimal("7");
    private static final String DETAIL = "供应商具有近三年内（2023年1月1日起至今，供应商具有1项大型集团型国央企电子商城办公用品服务业绩的不得分，"
            + "每增加1个得1分，最高得7分。";

    @ParameterizedTest(name = "qualified={0} -> score={1}")
    @CsvSource({"1,0", "2,1", "8,7", "10,7"})
    void exactProject22Rule_scoresOnlyAdditionalQualifiedPerformances(int qualifiedCount,
                                                                      String expectedScore) {
        String scoreType = new ScoreTypeClassificationPolicy().classify(DETAIL, "SUBJECTIVE");
        Integer requiredCount = ScoreItemConstraintExtractor.extractProjectRequiredCount(DETAIL, WEIGHT);
        MatchTierPolicy.Outcome match = MatchTierPolicy.evaluate(
                qualifiedCount, requiredCount, false, false);
        BigDecimal actual = new PartialScorePolicy().compute(
                WEIGHT, match.tier(), match.matchRatio(), scoreType);

        assertThat(actual).isEqualByComparingTo(expectedScore);
    }
}
```

- [x] **Step 2: Run the matrix and capture every failing row**

Run:

```bash
cd backend
mvn -Dtest=Project22SupplierPerformanceScoringAcceptanceTest test
```

Expected before production changes: RED. Record whether failure is `null` from `SUBJECTIVE`, an early positive score for one record, or premature full score. These observations determine the implementation boundary.

- [x] **Step 3: Run adjacent count and partial-score baselines**

Run:

```bash
cd backend
mvn -Dtest=KnowledgeCategoryPolicyTest,MatchTierPolicyTest,PartialScorePolicyTest test
```

Expected: existing tests PASS. Any failure here is baseline drift and must be diagnosed before modifying production code.

### Task 3: Prepare an auditable eight-record positive E2E dataset

**Files:**
- Create outside Git: `/tmp/xiyu-pr2855-positive-e2e-20261025/performance-template.xlsx`
- Create outside Git: `/tmp/xiyu-pr2855-positive-e2e-20261025/data-manifest.json`
- Create outside Git: `/tmp/xiyu-pr2855-positive-e2e-20261025/evidence/PR2855-E2E-01-CONTRACT.pdf` through `PR2855-E2E-08-CONTRACT.pdf`
- Create outside Git: `/tmp/xiyu-pr2855-positive-e2e-20261025/evidence/PR2855-E2E-01-RELATIONSHIP.pdf` through `PR2855-E2E-08-RELATIONSHIP.pdf`
- Create outside Git: eight row-specific `PR2855-E2E-0N-SOE-DIRECTORY.pdf` files

**Interfaces:**
- Consumes: Browser-downloaded `GET /api/knowledge/performance/template` workbook and the real UI `批量导入业绩` flow.
- Produces: Eight test-environment performance IDs with a common run marker and an exact cleanup manifest.

- [x] **Step 1: Download the current import template from the test-environment browser**

Open the knowledge performance page, choose `批量导入`, click `下载导入模板`, and save the browser-downloaded file as `/tmp/xiyu-pr2855-positive-e2e-20261025/performance-template.xlsx`.

- [x] **Step 2: Populate exactly eight distinct rows while preserving template headers and validations**

Use run marker `PR2855-E2E-20261025`. Create contract names `PR2855-E2E-20261025-01` through `PR2855-E2E-20261025-08`; use distinct customer names. Populate every required template column with these exact value rules:

```text
合同名称*: PR2855-E2E-20261025-0N
签约单位*: PR2855-E2E-央企客户-0N
集团公司名称*: PR2855-E2E-央企集团-0N
客户类型*: 央企
所属行业: 办公用品电子商城服务
项目类型*: 办公
对接方式*: API
客户级别: 集团
合同是否含西域: 是
签约日期*: 2023-01-0N
截止日期*: 2027-12-31
客户联系人*: 测试联系人0N
客户联系方式*: 1390000000N
属地*: 北京市
客户地址*: 北京市测试环境验收地址0N号
合同中西域项目负责人*: E2E验收
合同协议附件文件名*: PR2855-E2E-0N-CONTRACT.pdf
国资委央企名录截图附件文件名: PR2855-E2E-0N-SOE-DIRECTORY.pdf
品类页附件文件名: PR2855-E2E-0N-CATEGORY.pdf
签约抬头与央企集团关系证明附件文件名: PR2855-E2E-0N-RELATIONSHIP.pdf
是否有中标通知书: 否
备注: PR2855-E2E-20261025 办公用品电子商城服务，仅用于测试环境验收
```

Do not invent production provenance. Each evidence PDF must be visibly marked `测试环境 E2E 数据，不代表真实合同`, repeat its contract name/customer/date/service scope, and exist only to exercise the test workflow. Create eight matching category PDFs and eight row-specific SOE-directory PDFs. The observed importer consumes one uploaded filename per row and cannot bind one shared file repeatedly, so upload eight contract, eight relationship, eight category, and eight SOE-directory files, for 32 unique files total.

- [x] **Step 3: Write the cleanup and qualification manifest**

`data-manifest.json` must list, for each of the eight records: run marker, contract name, customer name, customer type, signing date, service scope, all four referenced evidence filenames, imported test ID (initially null), and cleanup status (initially `pending`).

- [x] **Step 4: Import through the browser and verify the UI receipt**

In the same browser TaskSpace, upload the workbook plus all 25 evidence PDFs and click `开始导入`. Acceptance requires the visible receipt to report eight successful records, 32 attachment associations (four per row; the shared SOE-directory file may be associated repeatedly), and zero failed rows/unmatched attachments. If the importer reports a different association count while all declared files are readable on every record, record the actual count and verify each record's four attachment types individually before accepting the dataset.

- [x] **Step 5: Read back test IDs through the authenticated browser session**

Use the performance list/search UI with keyword `PR2855-E2E-20261025`, then supporting `GET /api/knowledge/performance?keyword=PR2855-E2E-20261025` readback. Update the manifest with exactly eight new test IDs. More or fewer than eight is a data-preparation failure.

- [x] **Step 6: Prove the dataset matches the project 22 constraints before code changes**

Call `POST /api/knowledge/project/match` in the authenticated browser session with this exact body and record tier, ratio, matched IDs, and detail:

```json
{
  "projectTypeKeywords": ["中央企业", "央企", "国企", "办公用品", "电商", "电子商城"],
  "signedAfter": "2023-01-01",
  "minContractAmount": null,
  "requiredCount": 8
}
```

The matched ID set must include all eight manifest IDs. If unrelated pre-existing rows also match, restrict the proof to the eight manifest IDs and record the extra IDs separately; do not delete unrelated data. This is supporting evidence only; final acceptance remains browser-visible after deployment.

### Task 4: Make the implementation-boundary decision

**Files:**
- Modify: `docs/superpowers/specs/2026-10-25-project22-scoretype-increment-design.md`
- Create outside Git: `/tmp/xiyu-pr2855-positive-e2e-20261025/red-results.txt`

**Interfaces:**
- Consumes: Task 1 classification RED, Task 2 staircase matrix RED, and Task 3 eight-record match receipt.
- Produces: One explicit decision selecting the later production-code plan.

- [x] **Step 1: Record the three evidence results verbatim**

Include focused Maven command, exit code, expected/actual value for every failed row, browser import receipt, and eight matched test IDs in `red-results.txt`.

- [x] **Step 2: Apply the decision rule**

```text
If classification is the only failure and all four staircase rows already pass:
  choose a ScoreTypeClassificationPolicy-only fix.

If any of 1→0, 2→1, 8→7, 10→7 fails after forcing OBJECTIVE in a diagnostic assertion:
  reject the classification-only fix and plan an explicit incremental-count rule shared by classification and estimated scoring.

If the eight imported records do not match exactly:
  stop; repair data semantics or matching constraints before changing score calculation.
```

- [x] **Step 3: Update the design with the observed decision and stop at the implementation gate**

Add a dated `Evidence decision` section containing the observed failures and selected production boundary. Do not modify production Java in this evidence plan.

- [ ] **Step 4: Commit the evidence-only test and design changes**

```bash
git add backend/src/test/java/com/xiyu/bid/scoreparse/domain/ScoreTypeClassificationPolicyTest.java \
  backend/src/test/java/com/xiyu/bid/scoreparse/domain/Project22SupplierPerformanceScoringAcceptanceTest.java \
  docs/superpowers/specs/2026-10-25-project22-scoretype-increment-design.md \
  docs/superpowers/plans/2026-10-25-project22-scoretype-and-staircase-evidence.md
git commit -m "test(scoreparse): 固定项目22供应商业绩阶梯语义"
```

Expected: the commit hook may reject an intentionally RED test. If so, retain the uncommitted RED evidence and do not bypass hooks; the later production fix commit must include GREEN tests.
