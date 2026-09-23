# Implementation Plan: 评分解析页面证据与计分单元修复

**Branch**: `agent/codex/scoreparse-rule-extraction` | **Date**: 2026-09-23 | **Spec**: [spec.md](./spec.md)

**Input**: `specs/070-scoreparse-scoring-units/spec.md`

## Summary

分两层实施。先在 Sidecar artifact 中携带 PDF cell 的物理页号，由后端按引用页选图并保留 image extraction 截断状态；让初次提取和复核共用多页视觉证据、来源校验与可比较的校验结果，避免弱复核覆盖强初次结果。再引入父子关系枚举和唯一 `ScoreUnitPolicy`，统一候选、原文总分证明、总分校验及发布保护。将关系类型持久化到评分事实及复核 DTO，增加可空兼容字段迁移；未知关系只复核，不写正式分值。

## Technical Context

**Language/Version**: Python 3.11 (Sidecar), Java 21 (backend)
**Primary Dependencies**: FastAPI / pydantic; Spring Boot 3.2 / Jackson / JPA
**Storage**: MySQL 8.0 Flyway; immutable task facts and review evidence
**Testing**: pytest; Maven targeted JUnit; existing scoreparse Golden replay
**Target Platform**: XiYu document converter and backend
**Project Type**: monorepo service change across Sidecar and Java backend
**Performance Goals**: Preserve existing page and image limits; no whole-document image explosion
**Constraints**: PDF page is 1-based; keep DOCX gridSpan/vMerge; no new provider, OCR engine, or UI; exact source quotes and current AUTO_APPROVED write gate remain authoritative
**Scale/Scope**: Scoreparse source attribution, evidence validation, candidate generation, and review snapshot only

## Constitution Check

- FP-Java: scoring unit selection/aggregation is a pure domain policy; Sidecar/API/JPA stay in the imperative shell. **Pass**.
- TDD: tests precede implementation for page attribution, image selection/truncation, result selection, relationship math, and compatibility. **Pass planned**.
- Split-first: one small immutable relation enum/policy instead of duplicating rules across validators/services. **Pass**.
- Data integrity: additive nullable migration and inverse rollback; no historical score updates; REVIEW_REQUIRED keeps formal state. **Pass**.
- Real API: tests use existing real contract types; no production calls or mocks used as acceptance evidence. **Pass**.

## Design Decisions

1. **Evidence page source**: Sidecar emits optional `sourcePage` per PDF cell based on the actual source row/page, with table start page as legacy fallback. Image page selection unions pages from referenced cells and relevant score tables, bounded by current configured limits. If a cited page was not returned or extraction reports truncation, evidence coverage cannot become fully verified.
2. **DOCX merges**: Keep current structural Word grid conversion (`gridSpan`/`vMerge`). Do not convert DOCX to PDF or depend on LibreOffice; existing DOCX renderer path does not provide rendered pages.
3. **Recheck selection**: Initial extraction and image-capable recheck pass the same artifact, image set, validator, and source-proof check. If the provider rejects image requests, a text recheck may run but must pass the same cell/source/total proof; it is never treated as visual coverage. Compare explicit outcomes: valid provenance + structurally valid rule facts + closed declared total + no unknown relation. Keep first on equal strength; recheck may replace only when strictly stronger. Never use non-empty as a proxy.
4. **Relationship model**: `ChildAggregation` values `SUM`, `SHARED_CAP`, `BEST_OF`, `MUTUALLY_EXCLUSIVE`, `UNKNOWN` describe a fact's direct children. Legacy/null resolves as SUM. A model omission means UNKNOWN. Non-SUM relations must match cues in the cited raw source cells; generated `ruleText` alone is not proof.
5. **Effective score units**: SUM expands into direct children recursively; terminal SUM facts count themselves. Other known relation types count the parent once and do not count descendant condition facts as separate score items, while retaining every fact for review. UNKNOWN blocks automatic publication. One pure policy supplies effective facts, sums, and selected formal candidates.
6. **Source integrity**: Multiple cited cells are allowed only when all IDs resolve inside the same artifact scoring scope and the exact quote can be established from those source cells. Preserve all cell IDs/evidence in persisted facts. Do not weaken duplicate/conflict, code, ordering, or page identity checks.
7. **Persistence**: Add nullable `child_aggregation` to `score_parse_fact`, entity, insert/persist mapping, and review fact DTO. API treats field as optional; old rows resolve to SUM at read/domain boundary. No history backfill. Include rollback SQL.

## Project Structure

```text
document-converter-sidecar/
├── converter.py
├── image_extractor.py
└── tests/{test_extract_tables_v2.py,test_convert_images.py}
backend/src/main/java/com/xiyu/bid/scoreparse/
├── domain/ (DocumentArtifact, MultimodalScoreFact, validators, ScoreUnitPolicy)
├── application/ (sidecar image extraction, multimodal handler, persistence and review mapping)
├── infrastructure/openai/ (multimodal request/response contract)
└── entity/ScoreParseFact.java
backend/src/main/resources/db/{migration-mysql,rollback/migration-mysql}/
backend/src/test/java/com/xiyu/bid/scoreparse/
specs/070-scoreparse-scoring-units/
```

**Structure Decision**: Change existing Sidecar conversion and existing scoreparse backend modules. Add only one pure domain policy, the Flyway pair, its focused tests, and specification artifacts. No new service or UI module.

## Risks and Mitigations

- **False semantic certainty**: Model can confuse shared cap with additive children. Mitigation: non-SUM requires explicit relation evidence; uncertain relation is UNKNOWN and review-only.
- **Historical compatibility**: Existing facts have no relation value. Mitigation: null means SUM at one centralized boundary; no migration data rewrite.
- **Source-page drift**: Sidecar artifacts may originate from older versions. Mitigation: optional cell page; fall back to table/page metadata and mark unresolved cited pages for review.
- **Overly strict total proof**: Parent caps and conditional children can appear in the same row. Mitigation: all total calculations share effective-unit policy and Golden cases include each rule class.
- **Output truncation**: More context can increase bytes. Mitigation: preserve existing page/image limits and propagate truncation as incomplete evidence.

## Validation Plan

1. Baseline relevant Sidecar and backend tests before edits; save exact pass/fail/skip output.
2. RED then GREEN: Sidecar continuation row/cell physical-page metadata and page-selection fixtures; image response truncation propagation.
3. RED then GREEN: result-selection matrix for first/recheck evidence quality and shared image context.
4. RED then GREEN: pure relationship policy tests; validator, candidate, publication proof and persisted review contract tests.
5. Verify migration and rollback statically and with existing migration validation; no production DB execution.
6. Run focused Sidecar and backend tests, scoreparse Golden replay/eval, architecture and required local gates that are feasible; label missing services/provider-dependent results explicitly.
7. Independent review after implementation; inspect final diff and confirm anchor worktree cleanliness.

## Complexity Tracking

No constitution violations. A nullable migration is necessary because a recheck snapshot must retain the relationship reviewers are asked to judge; computing it transiently would leave the review contract semantically incomplete.


## Adversarial review notes

- Traced both `ScoreParseAppService` branches: multimodal when its extractor bean is present and deterministic structured extraction when absent. The deterministic publication guard now rejects a parent relationship that contradicts cited original cells, so the legacy SUM constructor default cannot auto-publish explicit shared-cap, best-of, exclusive, or unknown child relations.
- Confirmed invalid multimodal relation/page evidence cannot be rescued by deterministic fallback; recheck results pass the same source and total validation before replacement.
- Confirmed `AUTO_APPROVED` publication still requires an original-source proof and selected effective units; review-required facts remain in the immutable parse snapshot and are not written as formal candidates.
- Independent reviewer was unavailable in this run; T036 remains open for a separate review pass.
