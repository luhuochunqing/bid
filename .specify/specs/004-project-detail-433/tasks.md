# Tasks: 项目详情 §4.3.3 — 6 项差距补齐

**Plan**: [plan.md](plan.md)
**Spec**: [spec.md](spec.md)

---

## Phase 1: 低风险快速修复

### WP-1: 评标子阶段选项补齐

- [ ] T001 [P] [US3] Add `RESULT_OUT` option to EvaluationStatusPanel.vue dropdown at `src/views/Project/stages/components/EvaluationStatusPanel.vue`
- [ ] T002 [US3] Verify backend EvaluationSubStage enum accepts RESULT_OUT and transitions work

### WP-2: 复盘流程亮点字段

**Backend:**
- [ ] T003 [US4] Add `process_highlights` TEXT column to `ProjectRetrospective` entity at `backend/src/main/java/com/xiyu/bid/project/entity/ProjectRetrospective.java`
- [ ] T004 Create Flyway migration `V1003__add_retrospective_process_highlights.sql` at `backend/src/main/resources/db/migration-mysql/`
- [ ] T005 Create rollback `U1003__add_retrospective_process_highlights.sql` at `backend/src/main/resources/db/rollback/migration-mysql/`

**Frontend:**
- [ ] T006 [P] [US4] Add "流程亮点" input field in `RetrospectiveStage.vue` (visible when resultType === 'WON')
- [ ] T007 [US4] Wire processHighlights field to save on submit

**Tests:**
- [ ] T008 [P] Test ProjectRetrospective entity has processHighlights field
- [ ] T009 [P] Test RetrospectiveStage.vue shows/hides field based on result type

---

## Phase 2: 中等风险

### WP-3: 结果确认竞争对手情况

**Backend:**
- [ ] T010 [US2] Add `competitors_json` TEXT column to `ProjectResult` entity at `backend/src/main/java/com/xiyu/bid/project/entity/ProjectResult.java`
- [ ] T011 Create Flyway migration `V1004__add_result_competitors_json.sql`
- [ ] T012 Create rollback `U1004__add_result_competitors_json.sql`
- [ ] T013 Update `ResultRegistrationService` to save/load competitors data

**Frontend:**
- [ ] T014 [P] [US2] Add competitors table (3 default rows + add/delete) to `ResultConfirmStage.vue`
- [ ] T015 [US2] Wire competitors data to form submit/load

**Tests:**
- [ ] T016 [P] Test competitors data save/load roundtrip
- [ ] T017 [P] Test empty competitors allowed on submit

---

## Phase 3: 高风险

### WP-4: 评标时间线

**Backend:**
- [ ] T018 [US1] Create `EvaluationTimelineEvent` entity at `backend/src/main/java/com/xiyu/bid/project/entity/`
- [ ] T019 Create `EvaluationTimelineRepository`
- [ ] T020 Create `EvaluationTimelineService` with insert/list methods
- [ ] T021 Create Flyway migration `V1005__create_evaluation_timeline_table.sql`
- [ ] T022 Create rollback `U1005__create_evaluation_timeline_table.sql`
- [ ] T023 Integrate timeline recording into `EvaluationService.transitionSubStage()` and `attachEvidence()`

**Frontend:**
- [ ] T024 [P] [US1] Create `EvaluationTimeline.vue` component with timeline UI
- [ ] T025 [US1] Integrate timeline into `EvaluationStage.vue`

**Tests:**
- [ ] T026 [P] Test timeline events recorded on status change
- [ ] T027 [P] Test timeline events recorded on file upload
- [ ] T028 [P] Test empty timeline shows placeholder

---

## Phase 4: 后端 AI 集成

### WP-5: AI 生成复盘案例

- [ ] T029 [US5] Create endpoint/service for AI-generated case draft from retrospective data
- [ ] T030 [P] [US5] Add "AI 生成复盘案例" button to `ClosureStage.vue`
- [ ] T031 [US5] Show generated case preview with "保存到案例库" action

### WP-6: 项目文档导出

- [ ] T032 [US6] Add single-project export method to `ProjectExportService`
- [ ] T033 [P] [US6] Add "导出项目总结" button to `ClosureStage.vue`

---

## Final Phase: 验证

- [ ] T034 Run `mvn test -Dtest=ArchitectureTest` — verify architecture
- [ ] T035 Run `pnpm build` — verify frontend build
- [ ] T036 Run `pnpm test:unit -- --run` — verify all unit tests pass
- [ ] T037 Run `npm run check:line-budgets` — verify file sizes

---

## Dependencies

```
T001 → T002
T003 → T004 → T005 → T006 → T007 → T008 → T009
T010 → T011 → T012 → T013 → T014 → T015 → T016 → T017
T018 → T019 → T020 → T021 → T022 → T023 → T024 → T025 → T026 → T027 → T028
T029 → T030 → T031
T032 → T033
T034 → T035 → T036 → T037
```

## Parallel Opportunities

- Wave 1: T001 (WP-1) + T003-T009 (WP-2) — independent
- Wave 2: T010-T017 (WP-3) — after Wave 1
- Wave 3: T018-T028 (WP-4) — after Wave 2
- Wave 4: T029-T033 (WP-5 + WP-6) — after Wave 3
- T034-T037: run all after implementation

## Summary

| Phase | WP | Tasks | Priority | Risk |
|-------|-----|-------|----------|------|
| 1 | WP-1 + WP-2 | T001-T009 | P2/P3 | Low |
| 2 | WP-3 | T010-T017 | P2 | Medium |
| 3 | WP-4 | T018-T028 | P1 | High |
| 4 | WP-5 + WP-6 | T029-T033 | P3 | Medium |
| Final | Verify | T034-T037 | — | — |

Total: **37 tasks** across **6 work packages** + verification
