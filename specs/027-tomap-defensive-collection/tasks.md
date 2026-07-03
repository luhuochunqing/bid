# Tasks: 防御性 Collection 与优雅降级治理

**Feature**: 027-tomap-defensive-collection
**Branch**: `agent/qoder/tomap-robustness-defensive-collection`
**Date**: 2026-07-03

## Implementation Strategy

**MVP**: User Story 1（Batch 1 高风险 toMap 修复 + enrichment 降级）—— 解决本次崩溃事件的核心痛点，确保边界数据不再让模块崩溃。

**增量交付**:
- US1: 修复高风险 toMap（5 处外键 key）+ enrichment 降级 → 核心痛点解决
- US2: ArchUnit 守卫 + pre-push gate → 防新增
- US3: handler 诊断对齐 → 可观测性
- Polish: 修复剩余 toMap（Batch 2/3/4，26 处）+ 清理豁免清单

## Phase 1: Setup

- [ ] T001 确认当前分支 `agent/qoder/tomap-robustness-defensive-collection` 基于最新 main，工作区干净

## Phase 2: Foundational

- [ ] T002 [P] 创建豁免清单文件 `scripts/tomap-exemptions.json`，包含当前 31 处 toMap 2 参数版本的文件:行号 + reason（作为 ArchUnit 守卫和 pre-push gate 的初始白名单）

## Phase 3: User Story 1 - 边界数据不再让模块崩溃 (P1)

**Story Goal**: 修复高风险 toMap（Batch 1，5 处外键 key）+ enrichment 降级，确保边界数据时模块正常返回
**Independent Test**: 构造 tenderId 关联多 Project 数据，访问标讯列表页，验证正常加载

- [ ] T003 [P] [US1] 修复 `backend/src/main/java/com/xiyu/bid/project/service/ProjectQueryService.java` 4 处 toMap：line 109 (Tender::getId), 117 (ProjectInitiationDetails::getProjectId), 135 (TenderEvaluation::getTenderId), 141 (ProjectResult::getProjectId)，添加 `(a, b) -> a` merge function
- [ ] T004 [P] [US1] 修复 `backend/src/main/java/com/xiyu/bid/documenteditor/service/DocumentSectionTreeService.java` 2 处 toMap：line 34 (DocumentSectionAssignment::getSectionId), 37 (DocumentSectionLock::getSectionId)，添加 `(a, b) -> a` merge function
- [ ] T005 [P] [US1] 修复 `backend/src/main/java/com/xiyu/bid/workflowform/infrastructure/persistence/JpaWorkflowFormAdminStore.java` 2 处 toMap：line 50 (WorkflowFormTemplateVersionMaxRow::getTemplateCode), 55 (OaProcessBindingRecord::templateCode)，添加 `(a, b) -> a` merge function
- [ ] T006 [US1] 为 `TenderQueryService.enrichAssignmentInfoBatch` 添加 try-catch 降级（`backend/src/main/java/com/xiyu/bid/tender/service/TenderQueryService.java`），失败时 log.warn 并返回原 dtos
- [ ] T007 [US1] 为 `ProjectQueryService` 中的 enrichment 方法添加 try-catch 降级（识别 fetchXxxNames/fetchXxxMap/enrich 命名方法），失败时 log.warn 并返回原数据
- [ ] T008 [US1] 为 `DocumentSectionTreeService` 中的 enrichment 方法添加 try-catch 降级
- [ ] T009 [US1] 补充 enrichment 降级单元测试：`backend/src/test/java/com/xiyu/bid/tender/service/TenderQueryServiceTest.java` 新增测试模拟 enrichment 阶段抛异常，验证降级返回原数据
- [ ] T010 [US1] 运行 `cd backend && mvn test -Dtest='TenderQueryServiceTest,ProjectQueryServiceTest,DocumentSectionTreeServiceTest'` 验证 Batch 1 修复 + 降级无回归

## Phase 4: User Story 2 - 新增 toMap 无 merge function 被 CI 拦截 (P2)

**Story Goal**: ArchUnit 守卫规则 + pre-push gate 脚本，拦截新增 2 参数 toMap
**Independent Test**: 在测试代码中写一个 2 参数 toMap，运行 ArchitectureTest 验证被拦截

- [ ] T011 [P] [US2] 在 `backend/src/test/java/com/xiyu/bid/architecture/ArchitectureTest.java` 新增 ArchUnit 守卫规则：扫描 `Collectors.toMap` 2 参数版本调用，命中即失败，豁免清单从 `scripts/tomap-exemptions.json` 读取（需用 ArchUnit 的 JavaMethodCall API 区分重载）
- [ ] T012 [P] [US2] 创建 pre-push gate 脚本 `scripts/check-tomap-no-merge-function.mjs`：扫描 `backend/src/main/java/` 下 .java 文件的 `Collectors.toMap(` 调用，AST 级别解析参数数量，2 参数版本命中即拒绝（豁免清单从 `scripts/tomap-exemptions.json` 读取）
- [ ] T013 [US2] 将 `scripts/check-tomap-no-merge-function.mjs` 接入 `scripts/pre-push-gate.sh`，在现有检查后追加 toMap 检查步骤
- [ ] T014 [US2] 补充 ArchUnit 守卫规则测试：在 `ArchitectureTest.java` 新增测试验证 2 参数 toMap 被拦截、3 参数通过（用测试 fixture 类模拟）
- [ ] T015 [US2] 运行 `cd backend && mvn test -Dtest=ArchitectureTest` 验证守卫规则生效 + 无误报
- [ ] T016 [US2] 运行 `node scripts/check-tomap-no-merge-function.mjs` 验证 pre-push gate 脚本扫描结果正确（31 处豁免内，无新增）

## Phase 5: User Story 3 - 异常 handler 提供完整诊断信息 (P3)

**Story Goal**: 5xx 异常 handler 对齐 SOP §23（堆栈 + Payload + Sentry）
**Independent Test**: 触发 5xx 异常，验证日志有堆栈+Payload、Sentry 收到事件

- [ ] T017 [P] [US3] 审计 `backend/src/main/java/com/xiyu/bid/exception/GlobalExceptionHandler.java` 所有 5xx handler 方法（返回 500/409），列出需修复的 handler 清单
- [ ] T018 [US3] 修复 GlobalExceptionHandler 中其他 5xx handler（除 PR #1640 已修复的 handleIllegalStateException 外），确保每个 5xx handler 包含：`log.error` 打印堆栈 + `getRequestPayload` 打印 Payload + `Sentry.captureException` 上报
- [ ] T019 [US3] 补充 handler 诊断单元测试：`backend/src/test/java/com/xiyu/bid/exception/GlobalExceptionHandlerTest.java` 新增测试验证 5xx handler 调用 log.error（非 log.warn）+ Sentry.captureException
- [ ] T020 [US3] 运行 `cd backend && mvn test -Dtest=GlobalExceptionHandlerTest` 验证 handler 诊断对齐

## Phase 6: Polish - 修复剩余 toMap 隐患 (Batch 2/3/4)

**Story Goal**: 修复剩余 26 处 toMap（Batch 2 主键 key 10 处 + Batch 3 主键单表 13 处 + Batch 4 内存结构 3 处），豁免清单清空

- [ ] T021 [P] 修复 Batch 2 toMap（10 处，key 是主键但来自批量查询）：`TaskBoardService.java` (2处), `ExpenseLedgerApplicationService.java` (2处), `CustodianEmployeeNumberResolver.java`, `CaBorrowApplicationNameEnricher.java` (2处), `AccountBorrowApplicationMapper.java` (2处), `AlertHistoryQueryService.java`
- [ ] T022 [P] 修复 Batch 3 toMap（13 处，key 是主键且来自单表查询）：`ProjectArchiveExportService.java` (2处), `TenderFavoriteService.java`, `ProjectExportService.java`, `TaskStatusDictAdminService.java`, `TaskExtendedFieldAdminService.java`, `WarehouseExportAppService.java`, `WarehouseLedgerExportAppService.java`, `TenderQueryService.java` (2处 User::getId), 其他
- [ ] T023 [P] 修复 Batch 4 toMap（3 处，key 来自内存结构）：`AdminUserService.java`, `DepartmentGraphPolicy.java`, `WarehouseAttachmentExportPolicy.java`
- [ ] T024 每修复一处，从 `scripts/tomap-exemptions.json` 豁免清单删除对应条目
- [ ] T025 运行 `cd backend && mvn test` 全量测试验证 31 处修复无回归
- [ ] T026 运行 `cd backend && mvn test -Dtest=ArchitectureTest` 验证豁免清单清空后守卫规则仍通过（或仅剩主键 key 豁免）

## Phase 7: Final Polish & Cross-Cutting

- [ ] T027 [P] 更新 `docs/lessons/lessons-learned.md` 新增 §CO-tomap-defensive-collection：记录本次事件根因、三层失效、治理方案、ArchUnit 守卫使用方法
- [ ] T028 [P] 更新 `AGENTS.md` 或 `RELIABILITY.md` 在门禁体系中追加 toMap 守卫说明（如适用）
- [ ] T029 运行 `npm run build` + `cd backend && mvn test` + `git status` 完成门禁验证
- [ ] T030 推送分支 `git push -u origin agent/qoder/tomap-robustness-defensive-collection` 并创建 PR

## Dependencies

```
T001 (Setup) → T002 (Foundational)
T002 → Phase 3 (US1): T003-T005 可并行，T006-T008 依赖 T003-T005，T009 依赖 T006-T008，T010 依赖 T009
T002 → Phase 4 (US2): T011-T012 可并行，T013 依赖 T012，T014 依赖 T011，T015-T016 依赖 T013-T014
T002 → Phase 5 (US3): T017 独立，T018 依赖 T017，T019 依赖 T018，T020 依赖 T019
Phase 3-5 可并行（不同文件，无依赖）
Phase 6 (Polish): T021-T023 可并行，T024 依赖 T021-T023，T025-T026 依赖 T024
Phase 7: T027-T028 可并行，T029 依赖 Phase 6 + T027-T028，T030 依赖 T029
```

## Parallel Execution Examples

**US1 内部并行**:
- T003 (ProjectQueryService) + T004 (DocumentSectionTreeService) + T005 (JpaWorkflowFormAdminStore) 可并行（不同文件）
- T006 (TenderQueryService 降级) + T007 (ProjectQueryService 降级) + T008 (DocumentSectionTreeService 降级) 可并行

**US2 内部并行**:
- T011 (ArchUnit 规则) + T012 (pre-push gate 脚本) 可并行（不同技术栈）

**跨 Story 并行**:
- Phase 3 (US1) + Phase 4 (US2) + Phase 5 (US3) 可并行（不同文件范围）

**Polish 内部并行**:
- T021 (Batch 2) + T022 (Batch 3) + T023 (Batch 4) 可并行（不同文件）

## Summary

| Phase | Story | Task Count | Parallelizable |
|---|---|---|---|
| 1. Setup | - | 1 | No |
| 2. Foundational | - | 1 | Yes |
| 3. US1 (P1) | 边界数据不崩溃 | 8 | T003-T005, T006-T008 |
| 4. US2 (P2) | CI 拦截新增 | 6 | T011-T012 |
| 5. US3 (P3) | handler 诊断 | 4 | T017 |
| 6. Polish | 修复剩余 toMap | 6 | T021-T023 |
| 7. Final | 收尾 | 4 | T027-T028 |
| **Total** | | **30** | |
