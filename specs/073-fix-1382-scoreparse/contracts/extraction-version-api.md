# Extraction / activation API contract v1
Status: approved implementation contract; actual DTO names may differ, semantics binding.
Base: /api/projects/{projectId}/score-parse. Existing ApiResponse and project ACL apply.

## Reads
GET /extraction/latest and GET /extraction/{taskId} (taskId UUID): latest task status even failed/running, full candidates grouped by scheme, independent diagnostics/computability, active version identity/fingerprint/time, selection/adoption state. History reads do not mutate. Missing payload is legacy version0, never upgraded to verified.
Totals: extracted known subtotal/completeness, computable subtotal/count, active subtotal/count, declared total/status. Unknown total is null, not100. Parent/subitem duplicates never double count.

## Activation
POST /extraction/{taskId}/activate:
```json
{"schemeId":"source-scheme","expectedActiveFingerprint":"current-value","reason":"人工核对后采用","selectedItemIds":["item-id"]}
```
Expected fingerprint required; empty project has explicit sentinel. Missing selection means all computable units of selected scheme; explicit empty list rejected. Explicit noncomputable selection is rejected with400; those items remain visible in full snapshot with null maximum/reasons. Active projection contains computable items only. Each request describes the entire replacement selection. User-facing selection must remain reviewable. Manual overrides, if supported, remain distinct from original source and change fingerprint. Invalid/unknown weights remain snapshot diagnostics and cannot become numeric0. Future explicit manual historical version selection may activate a completed frozen snapshot under CAS; automatic stale task always rejected. No automatic fallback to server-current fingerprint.

## Status
400 invalid scheme/selection/input;403 project permission;404 unknown/cross-project task;409 fingerprint conflict, unavailable feature, unfinished/unfrozen task. GET disabled behavior must let frontend keep legacy display without hiding actual failures.

## Transactions and compatibility
Project row lock -> current version/latest task -> items/results. Compare and replace under same transaction. Freeze all candidates before activation. Existing review partial adoption remains available under legacy policy; new extraction UI uses explicit activation with CAS. Neither adoption status nor MANUAL_APPROVED rewrites original validation.

## First parse and reparse
Feature off is legacy behavior for legacy projects; existing versioned project must not be overwritten unsafely if flag later disabled.
First single scoring scheme with calculable candidates may activate immediately, even validation incomplete. Failed/empty/multiple unresolved schemes retain no active set.
Existing version is replaced automatically only when full/known/no conflicts/no lost items/no manual edits and base fingerprint still matches. Otherwise retain and show candidate differences.

## Scoring
Scoring captures active version and full rule fingerprint. Success, failure fallback and estimated score persistence all recheck under same project lock. Changing rule under samecode invalidates cached results. A->B->A must not admit a stale A task.

## Implemented Task 2 interfaces

- `ScoreExtractionAppService.getLatest(projectId)` / `getByTaskId(projectId, taskId)` return `ScoreExtractionAppService.View`, wrapped in `ApiResponse` by `ScoreExtractionController`.
- `View`: `projectId, taskId, parseTaskId, taskStatus, validationStatus, extractedAt, payloadVersion, snapshotStatus, schemes, items, sourceDiagnostics, runDiagnostics, adoption, active`.
- `snapshotStatus`: `FROZEN`, `NOT_FROZEN`, `LEGACY_UNKNOWN`. Legacy read is payload version 0 and never rewrites stored facts. Feature-off GET returns 409 for legacy projects; existing versioned projects remain readable even if the switch is subsequently disabled.
- `schemes[]`: `schemeId,title,extractedCount,extractedKnownTotal,extractedTotalComplete,computableCount,computableTotal,activeCount,activeTotal,activeTotalComplete,declaredTotal,declaredTotalStatus`. Extracted count and totals use effective scoring units; raw candidates, parent items and excluded candidates remain in `items[]`. Multiple source documents with the same scheme title receive separate scope identifiers.
- `items[]`: `ExtractedScoreItem` contains candidate identity, original fact identity, scheme/file, category/name, original rule or explicitly marked model rule fallback, raw/effective maximum, nullable scheme ratio, unit, score type, parent/aggregation, field sources, diagnostics, evidence validation and independent `computable` boolean. `fact.sourceMetadata.modelRuleText` and `modelQuotedText` remain separate from `fact.sourceText` and original `sourceSpans[].rawText`.
- `materialRequirements` is currently an empty structured list: existing extraction input has no independent materials field. Materials paragraphs remain in the original full source spans/rule text; consumers must not claim that an empty list means the source requires no materials.
- `adoption`: `status,adoptedItemIds,remainingItemIds`. Structural parent summaries and known out-of-scheme candidates do not keep a selected scheme permanently partial; unresolved effective candidates remain visible and unadopted. Selection always replaces the complete active set.
- `active`: `versionId,parseTaskId,taskId,fingerprint,activatedAt,hasManualEdits`. Both version identity and actual active rule projection participate in the fingerprint; estimation fields are excluded. JSON values are canonicalized across MySQL's JSON storage normalization.
- Activation command is `ActivateScoreExtractionCommand(schemeId, expectedActiveFingerprint, reason, selectedItemIds)`. Manual overrides are not implemented; the original source is never replaced by an activation reason. Empty/duplicate/unknown/noncomputable selections return 400. Manual selection sets automatic replacement protection. Completed frozen historical tasks can be selected explicitly under CAS. Old asynchronous tasks cannot auto-publish.
- New errors: 400 `ACTIVATION_INPUT_REQUIRED`, `SCHEME_NOT_FOUND`, `INVALID_OR_NONCOMPUTABLE_SELECTION`; 404 `PROJECT_NOT_FOUND`, `EXTRACTION_TASK_NOT_FOUND`; 409 `EXTRACTION_FEATURE_DISABLED`, `ACTIVE_FINGERPRINT_CONFLICT`, `EXTRACTION_NOT_FROZEN`, `EXTRACTION_NOT_COMPLETED`, `EXTRACTION_ALREADY_FROZEN`, `VERSIONED_PROJECT_REQUIRES_ACTIVATION`. Project ACL rejection remains 403.

### Persistence and Task 3 hook

V1219 adds `score_extraction_snapshot` (task mode/base fingerprint/full snapshot/frozen time), append-only `score_active_version`, nullable `score_parse_fact.weight` plus versioned `item_payload`, and `score_item.source_fact_id/active_version_id/rule_fingerprint`. `score_item.weight` remains NOT NULL; only known computable units are projected.

`ScoreVersionActivationService.currentToken(projectId)` returns `VersionToken(Long versionId, String fingerprint, boolean hasManualEdits)`. The caller must hold `ScoreParseTaskRepository.lockProjectForTaskChange(projectId)` when using this token to guard a write. The public read itself is not a write barrier. Task 3 must capture the token at trigger, check again with the same project lock at both successful and fallback result persistence, and avoid merging stale entire ScoreItem entities during estimated-score updates. New-version activation clears old result rows in the same transaction. Task 2 does not implement asynchronous scoring guards.

Task mode is captured when the parse task is created. Default-off uses the prior text-first extraction ordering; enabled mode acquires source anchors before extraction and freezes all candidates before activation. A later flag change cannot change a queued task's chosen parser/persistence mode. Legacy publishing is refused for already-versioned projects.

## Task 3: scoring and estimated-score version binding

The existing scoring routes and DTO shapes remain unchanged. `score_parse_task.item_set_hash` on new scoring tasks is SHA256 of `scoring-version:v1:` plus the canonical active projection fingerprint, and `pipeline_version=SCORING_VERSION_V1` identifies this contract. The projection includes active/parse/source-fact identities, full effective rule, name, weight, type, parent and the extraction rule fingerprint. `score_result.scoring_task_id` binds each result to this durable task fingerprint; no additional migration is required.

Both skip paths require the current fingerprint and a complete set of reusable assessments. Pending placeholders from an ITEMS/UNSATISFIED request are not assessment cache entries. Unselected items without valid results return `status=PENDING`, `actualScore=null` and an explanatory `missedReason`. An explicit empty ITEMS selection does not expand to ALL. Rules/version changes invalidate reuse even when bid chapters are unchanged.

For an unversioned project, a pre-upgrade completed task whose legacy item hash still matches (or whose historical hash is absent) remains visible as history. Its result has `reuseKind=LEGACY_UNVERIFIED`, `status=PENDING`, and `missedReason` beginning `历史评分尚未绑定完整规则版本，请重新评分；`; original `actualScore`, `evidence`, `quote` and suggestion are retained. This value is excluded from `summary.totalActualScore` and from both skip/reuse paths. It is never treated as a current completed assessment. This compatibility read remains available when the extraction-version flag is off. Versioned projects retain the strict guard after the flag is disabled.

Result reads are coherent project-lock transactions. Result replacement and task completion/failure are committed together only when the current projection fingerprint, current task state and latest scoring-task identity match. A rejected old worker records `STALE_SCORE_VERSION_OR_TASK` without replacing results. Estimation calculates on detached inputs and writes only estimate fields after rechecking the captured fingerprint. Automatic and manual activation schedule existing estimation after commit; they never trigger paid actual scoring. Follow-up dispatch failure does not roll back or misreport an already committed activation.

Task 3 review fix: UNSATISFIED selects by the previous result status of the same current item ID, independently of score reuse eligibility. A stale DANGER score can select an item for reassessment under its new rule; its numeric score is never reused. Legacy `/review` adoption also defers estimation until the original caller transaction commits. Every scoring worker exit clears progress cache, including a failed terminal-state write, so the status endpoint falls back to actual database state.
