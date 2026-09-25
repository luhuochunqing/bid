# Quickstart: 评分方案资格与总分回归

## Local regression and build

Run from the isolated task branch. Use the same commands before and after the code change; the first run records baseline counts.

## Baseline before implementation (2026-09-24)

| Command | Exit | Result |
|---|---:|---|
| Focused Maven command in this file (10 scoreparse test classes) | 0 | 119 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS. Maven emitted existing dependency/plugin/deprecation warnings. |
| `npm run test:unit -- src/composables/projectDetail/useScoreCandidateAdoption.spec.js src/views/Project/stages/components/ScoreCandidateReviewCard.spec.js` | 0 | Vitest executed 2 files / 20 tests, all passed. The test queue then reported `flock: not found`; record the queue guard as unavailable and rerun after the patch through an available safe runner before calling the wrapper gate passed. |
| `npm run agent:health-check` | 0 | Host worktree services are alive; the isolated main checkout has no service configuration. Git wrapper safety check reports `Wrapper NOT active`; this is the baseline and remains a separate failed health item. |
| `XIYU_EVAL_CORPUS_DIR` availability check | 0 | Directory unavailable; no local 1382/1384 real corpus. Project-shaped fixtures must be reported as redacted synthetic replay, not real-project reparse. |

```bash
mvn -f backend/pom.xml -Djacoco.skip=true -Dtest=OriginalScoringContextThreeDefectsTest,OriginalScoreEvidencePolicyTest,MultimodalScoreFactValidatorTest,MultimodalFactParentCodeNormalizerTest,ScoreUnitPolicyTest,ScoreFactChunkMergerTest,OpenAiMultimodalScoreFactExtractorTest,MultimodalStructuredExtractionHandlerTest,ScoreParseReviewAppServiceTest,GoldenCasesReplayTest test
npm run test:unit -- src/composables/projectDetail/useScoreCandidateAdoption.spec.js src/views/Project/stages/components/ScoreCandidateReviewCard.spec.js
```

Expected behavior after the fix:

- Project 1382 fixture: later contract-performance facts stay in the audit-only group; the selected scoring scheme has only source-valid effective leaves and totals 100.
- Project 1384 fixture: the 100-point declaration includes an original text/cell locator; same-scheme physical tables resolve one parent hierarchy and the effective leaves total 100.
- Unknown total, conflicting explicit totals and a verified multi-table scheme produce distinct fields/states. Independent schemes are not added together before selection.
- 5/4/3/2 alternatives yield a maximum of 5; cumulative 5+5+10 leaves remain 20 under their parent.
- Default UI adoption sends eligible fact IDs from only the selected scheme; the backend rejects ineligible or cross-scheme IDs.

Then run the repository completion gates:

```bash
npm run build
mvn -f backend/pom.xml test
npm run agent:health-check
```

Record exit codes, test counts, failures and skips in `.omc/verify-<session>.md`; do not summarize a skipped/environment-dependent command as passed.

## Authorized test-system E2E for project 1384

For project 1384, record the test deployed commit/release ID, SHA-256 of the existing real DOCX, new parse task ID and parse timestamp. The user authorized ENV=test only. Reparse after the reviewed build is deployed, then compare the `/review` API snapshot with `/items` and the visible review card:

1. Project 1384: confirm total 100 points to an exact original paragraph/cell; verify cross-table parent IDs and effective leaves total 100; ensure independent schemes require selection and do not show 250.
2. Confirm unknown or out-of-scheme source facts remain auditable and do not enter candidate sums or default adoption.
3. Preserve `/items` and prior formal results before/after. `REVIEW_REQUIRED` must not replace formal scoring items. Project 1382 is validated through its local redacted Golden replay only.

This section is the authorized test-site acceptance procedure, not a claim that a local golden test performed a deployment or real project reparse.

### 2026-09-25 test-site deployment attempt

- Task branch: `agent/zcode/scoreparse-1384-docx-reparse`; source checkout HEAD `e91248ee14c90f28578c13368a3f544cf336be7f` with the reviewed changes present as a dirty worktree. Release: `1384-docx-contract3-api8080` (not a commit-pinned artifact).
- Existing real DOCX SHA-256: `1ad688f01c1f53eac9fa4922b5d64213b4d817997e57c03e3077bb6062e7a4e9`.
- Package/build, container/Flyway test gate, archive validation, upload, and sidecar release smoke passed. Deployment activated the test artifact, but backend startup failed while applying `V1218__backfill_table_column_comments.sql`; `/actuator/health` failed all 240 consecutive probes. The database was not manually repaired.
- Recovery: restored backend JAR and frontend assets from test release `8245c4da8`, preserved the failed release and a backend rollback copy, and updated `deployed-release.json` to `8245c4da8`. The restored backend process is active and its DB/Redis/Sidecar components report UP, but aggregate readiness remains `OUT_OF_SERVICE` (HTTP 503), so do not treat the test site as healthy.
- No parse task was started. Parse task ID, `/review`, `/items`, and rendered UI after reparse are unavailable. Browser baseline remains the earlier pre-deployment snapshot (0 candidates / 0 points, 13 audit-only facts); it is not post-change acceptance evidence.
- Acceptance status: BLOCKED by the test database's failed V1218 migration. Resume only after the database owner resolves the failed migration and test readiness is healthy; then reparse the existing DOCX and record task ID plus `/review`, `/items`, and UI comparison.
