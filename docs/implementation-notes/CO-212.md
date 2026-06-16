# Implementation Notes: CO-212

## Bug Fix: 项目立项标书制作阶段切换后任务看板残留待项目负责人立项待办

### Root Cause

`useProjectDetailDocumentActions.js:loadProjectWorkflowData` (the active code path for loading project tasks) was missing the `【待立项】` task filter that existed in the older `useProjectDetailInit.js`.

When a tender is submitted, the backend creates a task with title `【待立项】{tenderTitle}` (via `TenderSubmissionService.participateBid` or `TenderEvaluationService`). These tasks are meant to be temporary placeholders that should be filtered out from the task board once the project moves to the drafting stage. The old `useProjectDetailInit.js` had this filter, but when the codebase migrated to `useProjectDetailDocumentActions.js`, the filter was not carried over.

### Decision

**Filter location:** Added in `useProjectDetailDocumentActions.js:loadProjectWorkflowData` — the same pattern as the existing filter in `useProjectDetailInit.js:18`.

**Filter criterion:** `!task.title?.startsWith('【待立项】')` — matches the backend task title prefix used by both `TenderSubmissionService` and `TenderEvaluationService`.

**No backend change needed:** The backend correctly creates these tasks with the `【待立项】` prefix. The filtering is a presentation-layer concern (these tasks serve their purpose during the tender submission flow but should not appear in the project task board).

### Tradeoffs

1. **Title-based filtering vs. task type/category:** Used title prefix matching for consistency with the existing approach. A more robust solution would be adding a `taskCategory` or `taskSource` field to the Task entity, but that's a larger change for a bug fix.

2. **Both code paths now consistent:** Both `useProjectDetailInit.js` and `useProjectDetailDocumentActions.js` now apply the same filter, preventing future drift if either path is used.

### Files Changed

- `src/composables/projectDetail/useProjectDetailDocumentActions.js` — Added `.filter((task) => !task.title?.startsWith('【待立项】'))` to `loadProjectWorkflowData`
- `src/composables/projectDetail/useProjectDetailActions.spec.js` — Added regression test verifying the filter works

### Testing

- ✅ New test: `loadProjectWorkflowData filters out 【待立项】tasks from task board`
- ✅ Existing tests: `useProjectDetailInit.spec.js` (2 tests) — all pass
- ✅ Existing tests: `useProjectDetailBoot.spec.js` (1 test) — all pass
- ✅ Existing tests: `useProjectDetailActions.spec.js` (2 existing + 1 new) — all pass
