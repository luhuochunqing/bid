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

## Test-system E2E after a separately authorized release

For each project, record the deployed commit, parse task ID, source file hash and parse timestamp. Reparse only after the fixed version is deployed, then compare the `/review` API snapshot with the visible review card:

1. Project 1382: confirm no page 98 or later contract-performance fact is a default candidate; verify its audit-only reason/source remains; confirm the selected scheme's valid leaf total is 100.
2. Project 1384: confirm total 100 points to an exact original paragraph/cell; verify cross-table parent IDs and effective leaves total 100; ensure independent schemes require selection and do not show 250.
3. Preserve `/items` and prior formal results before/after. `REVIEW_REQUIRED` must not replace formal scoring items.

This section is an external acceptance procedure, not a claim that a local golden test performed a deployment or real project reparse.
