# Tasks: fix-score-parse-ghost

**Input**: plan.md / spec.md / research.md / data-model.md / contracts/api-delta.md / quickstart.md

**Prerequisites**: spec ✅ plan ✅ research ✅ data-model ✅ contracts ✅ quickstart ✅

**Tests**: Constitution III TDD 强制 —— 先写失败测试再实现（每个 FR 测试任务前置）。

**Organization**: 3 个 User Story（US1 P0 幽灵/超载、US2 P1 编号序、US3 P1 可见性）+ 集成收尾。支持 Wave 并行（见 plan.md Agent Task Assignments）。

## Path Conventions

- 后端主代码：`backend/src/main/java/com/xiyu/bid/scoreparse/`（简写 `scoreparse/`）
- 后端测试：`backend/src/test/java/com/xiyu/bid/scoreparse/`
- 迁移：`backend/src/main/resources/db/migration-mysql/`

---

## Phase 1: Setup

- [x] T001 创建迁移加列 `score_parse_task.skipped_chunks INT NULL` + 回滚脚本（`bash scripts/new-migration.sh add-skipped-chunks` 分配版本号，禁止手猜）+ `scoreparse/entity/ScoreParseTask.java` 加 `Integer skippedChunks` 字段映射
- [x] T002 [P] 新建 `scoreparse/infrastructure/openai/RecallOutcome.java`：record `(List<ScoreCandidate> candidates, int skippedChunks)`，<20 行
- [x] T003 [P] `scoreparse/dto/ScoreParseItemsDTO.java` Meta record 末位加 `Integer skippedChunks`（同步 contracts/api-delta.md）

## Phase 2: US1 — 幽灵过滤与超载剔除（P0）🎯 MVP

**Goal**: 权重 200 → 100；幽灵项（dim=detail≤4字 或 无code无source）不落库；真实项零误删。

**Independent Test**: `mvn test -Dtest=GhostCandidateFilterTest,WeightOverloadTrimPolicyTest`（quickstart 场景 1）

- [x] T004 [P] [US1] 失败测试 `domain/GhostCandidateFilterTest.java`：①dim=detail="得分"被滤 ②dim=detail="评分标准"（>4字）保留 ③无code有sourceText 保留 ④有code无sourceText 保留 ⑤无code无sourceText 被滤 ⑥入参不被修改、序保持
- [x] T005 [P] [US1] 失败测试 `domain/WeightOverloadTrimPolicyTest.java`：①200→剔无code幽灵后=100 剔除停止 ②无code剔尽仍>100 继续剔无sourceText ③剔尽仍>100 保留全部（TrimResult.removed 记录） ④有code有source 真实项永不入 removed ⑤total≤100.5 时 no-op
- [x] T006 [P] [US1] 实现 `domain/GhostCandidateFilter.java`（纯函数 <60 行）：规则见 plan M1；norm=trim+去空白
- [x] T007 [P] [US1] 实现 `domain/WeightOverloadTrimPolicy.java`（纯函数 <90 行）：`trim(candidates, limit=100.5) → TrimResult(remaining, removed)`；两轮剔除（无code→无sourceText），逐个剔+重算，达标即停；TrimResult 为嵌套 record

## Phase 3: US2 — 编号序落库与空编号（P1）

**Goal**: itemIndex = 编号数值序；空 code 落 ""不补假编号。

**Independent Test**: `mvn test -Dtest=ScoreItemOrderPolicyTest,ScoreItemPersistenceServiceTest`（quickstart 场景 2）

- [x] T008 [P] [US2] 失败测试 `domain/ScoreItemOrderPolicyTest.java`：①[6,7,1,2,3,4-1,4-2,5] → [1,2,3,4-1,4-2,5,6,7] ②"A1/B2"类不可解析编码稳定保持相对序排末尾 ③"4-1"<"4-2"（子段数值序） ④null/空白 code 归入不可解析组 ⑤入参不修改
- [x] T009 [P] [US2] 实现 `domain/ScoreItemOrderPolicy.java`（纯函数 <80 行）：解析键 `^(\d+)(?:[-—.](\d+))?$`；major/minor 升序、无 minor 在前；不可解析稳定排序置后
- [x] T010 [P] [US2] `application/ScoreItemPersistenceService.java` 接入：persistItems 落库前 `orderPolicy.sort(candidates)`；code 空白落 `""`（行 65-66 与指纹比对行 112-113 两处同步）；更新 `ScoreItemPersistenceServiceTest` 断言（排序后 itemIndex、空 code 不再是 "1"）

## Phase 4: US3 — LLM chunk 失败可见（P1）

**Goal**: chunk 失败重试 1 次；skippedChunks 落库并透出 Meta。

**Independent Test**: `mvn test -Dtest=OpenAiScoreAnalyzerTest`（quickstart 场景 3）

- [x] T011 [P] [US3] 扩展失败测试 `infrastructure/openai/OpenAiScoreAnalyzerTest.java`：①单 chunk 首败→重试成功→skippedChunks=0 且候选保留 ②连续两败→skippedChunks=1 不阻断后续 chunk ③无失败→skippedChunks=0
- [x] T012 [P] [US3] `infrastructure/openai/OpenAiScoreAnalyzer.java` 改造：抽私有 `requestCandidatesWithRetry`（失败重试 1 次）；`recallCandidates` 返回 `RecallOutcome`；文件 ≤300 行（RecallOutcome 已独立文件）；完成日志带 skippedChunks
- [x] T013 [P] [US3] `application/ScoreParseItemsMetaBuilder.java`：Meta 构造加 `parseTask.getSkippedChunks()`（null 安全透出）；若现有 builder 测试存在则同步断言

## Phase 5: 集成接线与收尾（AppService 热点，串行）

- [x] T014 [US1][US2][US3] `application/ScoreParseAppService.java` 接线（依赖 T006/T007/T012）：①行160后插 ghostFilter（INFO 日志记被滤项） ②weightResult total>100.5 分支调 trimPolicy 并 WARN 记 removed、重算 weightResult ③recallCandidates 适配 RecallOutcome + `task.setSkippedChunks` ④文件 ≤300 行；`ScoreParseAppServiceTest` 加集成断言：幽灵候选被滤、超载被剔、skippedChunks 落库
- [x] T015 全量回归：`mvn test -Dtest='ScoreParse*Test,Score*PolicyTest'` + `mvn test -Dtest=ArchitectureTest,FPJavaArchitectureTest,MaintainabilityArchitectureTest` + `npm run check:line-budgets` 全绿
- [x] T016 收尾：部署报告（测试环境 project 212 重放验证 quickstart 场景）+ wiki 回填（score-parse-service 页）+ PR（Gitee）

---

## Dependencies

```text
T001 ──────────────┐
T002 ──┐           ├── T013
T003 ──┼─ (US3) ───┘
T004→T006 ──┐
T005→T007 ──┼── (US1) ──┐
T008→T009→T010 (US2) ───┤
T011→T012 ──┘ (US3) ───┴── T014 → T015 → T016
```

## Parallel Opportunities

- Wave1 并行：T001-T003（互不相干文件）；T004/T006、T005/T007、T008/T009、T011/T012 全程 [P]（新文件/独立文件）
- Wave2 串行：T014（AppService 唯一热点文件，避免并行编辑覆盖）

## MVP Scope

US1（T004-T007）独立可交付：仅幽灵过滤+超载剔除即可修复 project 212 权重 200 问题。
