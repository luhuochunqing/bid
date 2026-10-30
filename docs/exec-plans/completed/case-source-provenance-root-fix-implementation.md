# 案例来源位置保真实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不减少案例生成数量的前提下，让案例直接继承 AI 调用前已知的原文段落位置和章节，不再依赖模型改写后的文本反查来源。

**Architecture:** `ScoreDocExcerptExtractor` 返回带原文偏移的结构化段落，`CaseAiMatcher` 为段落标注稳定 ID 并让 AI 在返回 `matchedSnippet` 时选择 `sourceSegmentId`。程序验证 ID 必须属于本次摘录，然后使用段落原文偏移确定 `MarkdownSourceSectionIndex.Provenance`；入库优先使用该来源，无法取得时保留旧反查和案例生成行为。

**Tech Stack:** Java 21, Spring Boot 3.2, JUnit 5, AssertJ, Mockito, Maven.

## Global Constraints

- 不减少案例生成数量，来源失败不得丢弃案例。
- 不把评分项标题、正文首句或模型生成的标题当作来源证据。
- 不新增数据库表或 Flyway 迁移，沿用现有来源字段。
- 新的位置计算保持为无框架 I/O 的纯函数；AI 调用和 JPA 持久化留在命令式外壳。
- 不回填、不部署、不修改生产数据。

---

### Task 1: 保留摘录段落的原文偏移

**Files:**
- Modify: `backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreDocExcerptExtractor.java`
- Test: `backend/src/test/java/com/xiyu/bid/scoreparse/application/ScoreDocExcerptExtractorTest.java`

**Interfaces:**
- Produces: `Excerpt extractRelevantExcerptWithSegments(String fullText, String dim, String detail, int maxChars)`
- Produces: `record Excerpt(String text, List<Segment> segments)`
- Produces: `record Segment(String id, String text, int sourceStartOffset)`
- Preserves: existing `extractRelevantExcerpt(...)` overloads delegate to `Excerpt.text()` without behavior change.

- [x] **Step 1: Write the failing structured-excerpt test**

Add a test that builds three double-newline-separated paragraphs, makes the middle paragraph win keyword scoring, and asserts that `extractRelevantExcerptWithSegments(...)` returns the same rendered excerpt as the legacy method plus segment `sourceStartOffset` values pointing to each selected paragraph in the original string. Assert `fullText.substring(offset, offset + segment.text().length())` equals `segment.text()`.

- [x] **Step 2: Run the RED test**

Run: `cd backend && mvn test -Dtest=ScoreDocExcerptExtractorTest`

Expected: compilation failure because `Excerpt`, `Segment`, and `extractRelevantExcerptWithSegments` do not exist.

- [x] **Step 3: Implement structured extraction minimally**

Replace the lossy `fullText.split("\\n{2,}")` representation with an internal paragraph scanner that records trimmed text and its original start offset. Keep scoring, TOC exclusion, neighbor selection, ordering, separators, and `maxChars` behavior unchanged. Build segment IDs deterministically in rendered order as `segment-0`, `segment-1`, and so on. Make both existing string overloads return `.text()` from the structured method.

- [x] **Step 4: Run extractor tests GREEN**

Run: `cd backend && mvn test -Dtest=ScoreDocExcerptExtractorTest`

Expected: all extractor tests pass with zero failures and zero errors.

- [x] **Step 5: Commit Task 1**

```bash
git add backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreDocExcerptExtractor.java \
  backend/src/test/java/com/xiyu/bid/scoreparse/application/ScoreDocExcerptExtractorTest.java
git commit -m "refactor(scoreparse): preserve excerpt source offsets"
```

### Task 2: 按原文偏移取得权威章节

**Files:**
- Modify: `backend/src/main/java/com/xiyu/bid/casework/application/MarkdownSourceSectionIndex.java`
- Test: `backend/src/test/java/com/xiyu/bid/casework/application/MarkdownSourceSectionIndexTest.java`

**Interfaces:**
- Consumes: original Markdown character offset from Task 1.
- Produces: `Provenance locateByOriginalOffset(int originalOffset)` returning the containing valid section or `null`.

- [x] **Step 1: Write the failing offset-location tests**

Add tests for a Markdown document with two headings and two bodies. Assert offsets inside each body return that body’s exact title/path. Add boundary assertions: negative offset, offset outside the document, text before the first section, and a section with an invalid pure-number title return `null`.

- [x] **Step 2: Run the RED test**

Run: `cd backend && mvn test -Dtest=MarkdownSourceSectionIndexTest`

Expected: compilation failure because `locateByOriginalOffset(int)` does not exist.

- [x] **Step 3: Implement offset lookup**

Reuse `findSectionForOffset`; reject offsets outside `originalMarkdown`; apply the existing `MarkdownTitleRules.isValidSectionTitle` contract; compute the page number with `findPageNumberBefore`; return the existing `Provenance` record. Do not add fuzzy text rules.

- [x] **Step 4: Run section-index tests GREEN**

Run: `cd backend && mvn test -Dtest=MarkdownSourceSectionIndexTest`

Expected: all tests pass with zero failures and zero errors.

- [x] **Step 5: Commit Task 2**

```bash
git add backend/src/main/java/com/xiyu/bid/casework/application/MarkdownSourceSectionIndex.java \
  backend/src/test/java/com/xiyu/bid/casework/application/MarkdownSourceSectionIndexTest.java
git commit -m "feat(casework): locate provenance by source offset"
```

### Task 3: 让 AI 选择可验证的来源段落

**Files:**
- Create: `backend/src/main/java/com/xiyu/bid/casework/application/CaseSourceExcerpt.java`
- Modify: `backend/src/main/java/com/xiyu/bid/casework/application/CaseAiMatcher.java`
- Test: `backend/src/test/java/com/xiyu/bid/casework/application/CaseAiMatcherTest.java`

**Interfaces:**
- Consumes: `ScoreDocExcerptExtractor.Excerpt` and `Segment` from Task 1.
- Consumes: `MarkdownSourceSectionIndex.locateByOriginalOffset(int)` from Task 2.
- Produces: `CaseSourceExcerpt.prepare(...)` as a pure adapter from structured excerpt segments to labelled prompt text and validated provenance lookup.
- Produces on `AiMatchedSlice`: `MarkdownSourceSectionIndex.Provenance provenance`.

- [x] **Step 1: Write the failing model-rewrite provenance test**

Use a Markdown fixture with two valid headings. Configure the AI response as `{"matchedSnippet":"模型改写后的证据","sourceSegmentId":"segment-1","confidence":0.9}` where the returned snippet is not a substring of the Markdown. Assert the returned slice still contains that rewritten snippet and carries the title/path of `segment-1` from the original Markdown. Capture the request and assert the prompt contains both stable segment labels and requests `sourceSegmentId`.

- [x] **Step 2: Run the RED test**

Run: `cd backend && mvn test -Dtest=CaseAiMatcherTest`

Expected: test compilation or assertion failure because the prompt and `AiMatchedSlice.provenance` do not exist.

- [x] **Step 3: Implement validated segment selection**

Implement the segment-label rendering and ID-to-provenance map in the focused `CaseSourceExcerpt` pure class. Call `extractRelevantExcerptWithSegments`; render the prompt with each segment prefixed by its ID; request JSON fields `matchedSnippet`, `sourceSegmentId`, and `confidence`. Parse `sourceSegmentId`, accept it only if it matches one of the actual excerpt segments, and calculate provenance from that segment’s `sourceStartOffset`. Unknown/missing IDs leave provenance `null` but do not discard a nonblank matched snippet. Keep OpenAI-wrapper parsing, base64 stripping, confidence behavior, and empty-snippet skip unchanged.

- [x] **Step 4: Add invalid-ID compatibility test**

Add a test returning a valid nonblank snippet with `sourceSegmentId:"invented"`; assert the slice is still returned, its provenance is `null`, and case count behavior is therefore unchanged.

- [x] **Step 5: Run matcher tests GREEN**

Run: `cd backend && mvn test -Dtest=CaseAiMatcherTest`

Expected: all tests pass with zero failures and zero errors.

- [x] **Step 6: Commit Task 3**

```bash
git add backend/src/main/java/com/xiyu/bid/casework/application/CaseSourceExcerpt.java \
  backend/src/main/java/com/xiyu/bid/casework/application/CaseAiMatcher.java \
  backend/src/test/java/com/xiyu/bid/casework/application/CaseAiMatcherTest.java
git commit -m "fix(casework): preserve provenance across AI matching"
```

### Task 4: 入库优先使用上游来源且不影响案例数量

**Files:**
- Modify: `backend/src/main/java/com/xiyu/bid/casework/application/ProjectClosedEventListener.java`
- Test: `backend/src/test/java/com/xiyu/bid/casework/application/ProjectClosedEventListenerTest.java`

**Interfaces:**
- Consumes: `AiMatchedSlice.getProvenance()` from Task 3.
- Preserves: legacy `sectionIndex.locate(matchedSnippet, scoreItem.title())` only when upstream provenance is absent.

- [x] **Step 1: Write the failing end-to-end persistence test**

Create one score item and one AI slice whose `matchedSnippet` is absent from the Markdown but whose provenance is `new Provenance("二、 应急方案", "六、 技术方案 > 二、 应急方案", 1, 12)`. Capture `knowledgeCaseRepository.saveAll` and assert exactly one case is saved, `responseText` remains the rewritten model text, and all provenance fields use the supplied value and bid document name.

- [x] **Step 2: Run the RED test**

Run: `cd backend && mvn test -Dtest=ProjectClosedEventListenerTest`

Expected: compilation failure before Task 3 or assertion failure because the listener ignores upstream provenance.

- [x] **Step 3: Implement provenance preference**

Extract a small private method that returns `slice.getProvenance()` when nonnull; otherwise executes the existing text lookup. Populate the same five entity fields as today. Preserve the current try/catch degradation and unconditional `casesToSave.add(kCase)`.

- [x] **Step 4: Run focused regression tests GREEN**

Run: `cd backend && mvn test -Dtest=ScoreDocExcerptExtractorTest,MarkdownSourceSectionIndexTest,CaseAiMatcherTest,ProjectClosedEventListenerTest,ProjectClosedEventListenerStreamingTest`

Expected: all focused tests pass with zero failures and zero errors.

- [x] **Step 5: Commit Task 4**

```bash
git add backend/src/main/java/com/xiyu/bid/casework/application/ProjectClosedEventListener.java \
  backend/src/test/java/com/xiyu/bid/casework/application/ProjectClosedEventListenerTest.java
git commit -m "fix(casework): persist pre-AI source provenance"
```

### Task 5: 验证、知识回填与完成门禁

**Files:**
- Modify: `docs/lessons/lessons-learned.md`
- Modify when triggered by wiki checkpoint: `.wiki/pages/case-source-actions-and-import-pitfalls.md`
- Move: `docs/exec-plans/active/case-source-provenance-root-fix-design.md` to `docs/exec-plans/completed/`
- Move: `docs/exec-plans/active/case-source-provenance-root-fix-implementation.md` to `docs/exec-plans/completed/`

**Interfaces:**
- Consumes: all implementation and test evidence from Tasks 1–4.
- Produces: repository knowledge and final verification evidence bound to current HEAD.

- [x] **Step 1: Run architecture and maintainability gates**

Run: `cd backend && mvn test -Dtest=ArchitectureTest,FPJavaArchitectureTest,MaintainabilityArchitectureTest`

Expected: zero failures and zero errors.

- [ ] **Step 2: Run the complete backend suite**

Run: `cd backend && mvn test`

Expected: `BUILD SUCCESS`, zero failures and zero errors. If an unrelated baseline failure appears, record the exact test and separately rerun the affected suite; do not label the full suite green.

Observed 2026-10-30: suite ran for about 25 minutes and reached unrelated integration tests, then the shared local MySQL on port 13306 refused connections. `WarehouseAttachmentTypePersistenceIntegrationTest` failed during `AbstractMysqlIntegrationTest` static initialization; the remaining run was stopped after the infrastructure failure was proven. Focused and architecture gates remain green.

- [x] **Step 3: Record the root-cause lesson**

Append one numbered entry to `docs/lessons/lessons-learned.md` stating the single root cause, the pre-AI segment-ID/offset repair, the unchanged case-generation contract, and exact RED/GREEN commands. Run the project wiki checkpoint and update the existing case-source wiki page only if the checkpoint requires it.

- [x] **Step 4: Complete plan documents and verify diff**

Move both active documents to `docs/exec-plans/completed/`, mark completed checklist items, then run `git diff --check`, `git status --short`, and `git diff origin/main...HEAD --stat`.

- [x] **Step 5: Commit closeout documentation**

```bash
git add docs/lessons/lessons-learned.md docs/exec-plans/ .wiki/
git commit -m "docs(casework): record provenance root-cause closure"
```
