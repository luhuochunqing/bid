# Quickstart: Scoreparse Scoring Units

## Prerequisites

- Python dependencies for `document-converter-sidecar` and Java 21/Maven for backend tests.
- No production service, database, or external model credential is required for unit tests.

## Focused validation

1. Sidecar PDF continuation/page mapping and image response behavior:
   `cd document-converter-sidecar && python -m pytest tests/test_extract_tables_v2.py tests/test_convert_images.py -q`
2. Backend scoring relation and evidence selection tests:
   `mvn -f backend/pom.xml -Dtest='MultimodalScoreFactValidatorTest,MultimodalStructuredExtractionHandlerTest,ScoreUnitPolicyTest,OpenAiMultimodalScoreFactExtractorTest,SidecarImageExtractorTest' test`
3. Persistence/review contract tests and migration checks:
   `mvn -f backend/pom.xml -Dtest='ScoreParseReviewAppServiceTest,ScoreParseDualWriteTest,FlywayMysqlContainerTest' test`
4. Existing Golden regression:
   `mvn -f backend/pom.xml test -Dtest='GoldenCasesReplayTest'`

## Expected outcomes

- Cross-page source cells carry the physical page, selected image request includes cited pages, and truncated/missing image coverage cannot be declared complete.
- Recheck output replaces initial facts only when shared validation proves it strictly stronger.
- Four known relationship types yield one effective score calculation; UNKNOWN blocks automatic publication.
- Legacy fact rows with null relationship remain readable as SUM. No database migration runs against a real environment in this guide.


## Verification record (2026-09-23)

- Baseline before implementation: Sidecar `test_extract_tables_v2.py` + `test_convert_images.py`: 25 passed. The backend baseline command completed successfully; its exact per-suite counts were not captured, so T001 remains open.
- Final focused Sidecar run: 26 passed, 6 warnings (`python -m pytest tests/test_extract_tables_v2.py tests/test_convert_images.py -q`). Warnings were dependency deprecations and missing ffmpeg notice.
- Final focused backend run: 74 tests, 0 failures/errors/skips; `mvn -f backend/pom.xml -Djacoco.skip=true -Dtest='ChildAggregationTest,OriginalSourcePublicationGuardTest,MultimodalScoreFactValidatorTest,MultimodalStructuredExtractionHandlerTest,OpenAiMultimodalScoreFactExtractorTest,SidecarImageExtractorTest,ScoreParseReviewAppServiceTest,ScoreUnitPolicyTest,ScoreTotalEvidencePublicationAcceptanceTest,SchemeScopePublicationTest,ThreeDefectsPublicationFlowTest,ScoreParseDualWriteTest' test`.
- Final Golden and architecture run after the deterministic-guard change: 53 tests, 0 failures/errors/skips (21 GoldenCasesReplay cases and 32 ArchitectureTest cases).
- Full backend `mvn test` was started but interrupted after several minutes because the full integration suite continued creating many Spring contexts. It is incomplete (exit 130), not a pass/fail result. Its interrupted JaCoCo execution data caused one later JaCoCo report error; rerunning the focused set with `-Djacoco.skip=true` passed. Coverage report is unverified.
- Migration checks: the repository scripts skipped because they inspect staged migrations only. Direct static assertions passed: V1217 follows V1216, `child_aggregation` is nullable, rollback drops that column, and no historical backfill is included. No database migration was run.
- Spec Kit consistency analysis: no CRITICAL/MAJOR artifact mismatch. Final `git diff --check` passed. No provider-backed, live-document, production, or real MySQL migration acceptance was performed.
