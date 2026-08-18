# Tasks: 根治评分项权重合计缺口

**Input**: Design documents from `/specs/047-fix-score-weight-gap/`

**Prerequisites**: plan.md（required）、spec.md（required）、data-model.md、contracts/score-parse-api.md、quickstart.md

**Tests**: spec SC-002 明确要求纯单测 ≥10 用例；Constitution Principle III (TDD) NON-NEGOTIABLE。故每个
User Story 内均含测试任务（测试先行，先红后绿）。

**Organization**: Tasks grouped by user story — US1(核心归一化 MVP) → US2(确定性/比例保持) → US3(信号透出)，
按 spec 优先级 P1→P2→P3。

---

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、无依赖）
- **[Story]**: US1 / US2 / US3
- 均含精确文件路径

---

## Phase 1: Setup

**Purpose**: 本 feature 复用既有 Spring Boot 后端 / Vue 前端，无独立项目初始化。仅需确保分支基于最新基线。

- [ ] T001 确认分支 `agent/trae/047-fix-score-weight-gap` 已 `./scripts/sync-env.sh .` 同步至最新
      origin/main，目录 `/Users/user/xiyu/worktrees/trae` 下无未提交改动

---

## Phase 2: Foundational

**Purpose**: 本 feature **无跨 story 阻塞的前置基础设施**——US1 归一化为纯内存运算，不依赖 DB/框架；
信号透出的 DB 字段（V1194 + entity + DTO）属 US3 内聚，故 Phase 2 不设独立任务（标注 N/A）。

**Checkpoint**: 直接进入 US1。

---

## Phase 3: User Story 1 - 评分项权重合计必须恒等于 100 (Priority: P1) 🎯 MVP

**Goal**: 新增纯核心 `WeightNormalizationPolicy`，对合计偏离 100（容差 ±0.5 外、且 >0）的候选集执行
"按比例缩放 + 最大余数法"归一，并插入 `ScoreParseAppService.parse` 编排（超载剔除后、落库前），保证
落库 items 权重合计恒=100。

**Independent Test**: 运行 `mvn test -Dtest=WeightNormalizationPolicyTest`（纯核心，无 Spring 上下文）；
对任一缺失/超载权重集输出合计恰=100。无需 DB/服务即可验证。

### Tests for User Story 1 (TDD，先红后绿) ⚠️

> **NOTE: 先写这些测试并确认 FAIL，再实现**

- [ ] T002 [US1] 新建 `WeightNormalizationPolicyTest`（路径
      `backend/src/test/java/com/xiyu/bid/scoreparse/domain/WeightNormalizationPolicyTest.java`），首轮用例：
      缺口（[10,20,60] 合计 90 → 输出合计 100）、超载（[50,150] 合计 200 → 输出合计 100）、
      恰好 100（[10,20,70] → 原样透传，originalTotal=null）当前实现缺失 → 断言 FAIL

### Implementation for User Story 1

- [ ] T003 [US1] 实现纯核心归一化 `WeightNormalizationPolicy`（路径
      `backend/src/main/java/com/xiyu/bid/scoreparse/domain/WeightNormalizationPolicy.java`）：
      - `public Result normalize(List<ScoreCandidate> candidates)`
      - `record Result(List<ScoreCandidate> normalizedCandidates, BigDecimal originalTotal)`
      - 逻辑：计算 total；total≤0 或 |total-100|≤0.5 → 原样返回 + originalTotal=null；
        否则 scale=100/total，逐项 `weight*scale` 保留 2 位小数，用最大余数法确定性分配余数（tie-break
        按 index 升序），返回归一化列表 + originalTotal=total
      - **仅改写 weight，其余字段（code/dim/detail/sourceText…）保持不变**（spec Key Entities）
- [ ] T004 [US1] 编排接入：在 `ScoreParseAppService.parse`（路径
      `backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreParseAppService.java`）的
      `WeightOverloadTrimPolicy` 剔除（约 L191-197）之后、`itemPersistenceService.persistItems`（约 L210）
      之前，调用 `weightNormalizationPolicy.normalize(valid)`，用 `normalizedCandidates` 覆写 `valid`
      （仅当其 `originalTotal != null` 时；null 时原样）
- [ ] T005 [US1] 回归既有解析/评分单测确保无破坏（`mvn test -Dtest=ScoreParseAppServiceTest,ScoreScoringAppServiceTest`，
      路径 `backend/src/test/java/com/xiyu/bid/scoreparse/`）

**Checkpoint**: US1 独立可用 — 缺/超载权重集经归一后合计恒 100，单项相对比例保持在容差内。

---

## Phase 4: User Story 2 - 归一化不扭曲相对权重结构 (Priority: P2)

**Goal**: 确认归一化保持各项相对权重比例、输出确定性可复现（最大余数法 + index 升序 tie-break），
同一输入多轮运行结果逐位一致。

**Independent Test**: 追加的确定性/比例单测在 `WeightNormalizationPolicyTest` 中，运行
`mvn test -Dtest=WeightNormalizationPolicyTest` 即验证。

### Tests for User Story 2 (TDD，先红后绿) ⚠️

- [ ] T006 [US2] 在 `WeightNormalizationPolicyTest` 补**相对比例**用例：给定候选权重与归一化后权重，
      逐项比值在原比对应倍数内一致（如 [25,50,25] 归一后仍约 1:2:1）
- [ ] T007 [US2] 在 `WeightNormalizationPolicyTest` 补**确定性/可复现**用例：同一输入连续两次
      `normalize`，输出权重集合逐位一致（Map/List 比较）；不同调用顺序（候选列表乱序）不影响整体
      剩余额度分配的正确性

**Checkpoint**: US1 + US2 独立可用 — 归一化既收敛到 100 又保持结构、可复现。

---

## Phase 5: User Story 3 - 缺口来源对用户可见 (Priority: P3)

**Goal**: 归一化实际发生时，持久化原始合计到 `ScoreParseTask.originalTotalWeight`，并在
`ScoreParseItemsDTO.Summary.originalTotalWeight` 透出（null=未归一化），前端可择机呈现"原合计 X 已补齐"
（FR-007 不阻塞核心，本 feature 不改前端展示）。

**Independent Test**: 触发真实解析后，`GET /api/projects/{id}/score-parse/items` 响应
`summary.originalTotalWeight` 语义正确；SQL 复核 `score_parse_task.original_total_weight`。

### Tests for User Story 3 (TDD，先红后绿) ⚠️

- [ ] T008 [US3] 契约/信号单测：`ScoreParseItemsDTO.Summary` 新增字段在（序列化后）存在且可空；
      `ScoreParseTask` 新增字段映射与 `getItems` 透出逻辑——用新增 `ScoreParseTaskTest`/DTO 测试
      （路径 `backend/src/test/java/com/xiyu/bid/scoreparse/`）先 FAIL

### Implementation for User Story 3

- [ ] T009 [P] [US3] Flyway 迁移：运行 `bash scripts/new-migration.sh add_original_total_weight`
      （自动取下一版本号 V1194）生成 `backend/src/main/resources/db/migration-mysql/V1194__add_original_total_weight.sql`：
      `ALTER TABLE score_parse_task ADD COLUMN original_total_weight DECIMAL(10,2) DEFAULT NULL;`
      并在 `backend/src/main/resources/db/rollback/migration-mysql/` 生成对应回滚（DROP COLUMN）
- [ ] T010 [US3] 实体字段：`ScoreParseTask`（路径
      `backend/src/main/java/com/xiyu/bid/scoreparse/entity/ScoreParseTask.java`）新增
      `private BigDecimal originalTotalWeight;` 及 `@Column` 映射（可空）
- [ ] T011 [US3] DTO 字段：`ScoreParseItemsDTO.Summary`（路径
      `backend/src/main/java/com/xiyu/bid/scoreparse/dto/ScoreParseItemsDTO.java`）新增
      `BigDecimal originalTotalWeight`（可空，位于 weightWarning 之后，保持字段序向后兼容）
- [ ] T012 [US3] 信号持久化 + 透出：`ScoreParseAppService`（路径
      `backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreParseAppService.java`）
      - parse 归一化发生时 `task.setOriginalTotalWeight(originalTotal); taskRepository.save(task);`
      - `getItems` 组装 Summary 时，从 latest PARSE task 读 `originalTotalWeight` 写入
        `new ScoreParseItemsDTO.Summary(..., summary.weightWarning(), originalTotalWeight)`
- [ ] T013 [US3] 契约文档同步确认：`specs/047-fix-score-weight-gap/contracts/score-parse-api.md`
      与实现字段名/可空一致（无需改动即为对齐）

**Checkpoint**: US1 + US2 + US3 全部独立可用 — 落库合计恒 100，缺口来源信号可透出。

---

## Phase N: Polish & Cross-Cutting Concerns

**Purpose**: 门禁、回归、文档收口。

- [ ] T014 [P] 运行 quickstart.md 验证：`cd backend && mvn test -Dtest=WeightNormalizationPolicyTest`；
      `curl .../score-parse/items` 复核 `summary.totalWeight==100`、`originalTotalWeight` 语义
- [ ] T015 [P] 门禁完整性：`mvn test -Dtest=ArchitectureTest,FPJavaArchitectureTest`（FP-Java 分层合规）、
      `npm run check:line-budgets`（单文件行数）、`cd backend && mvn -Pjava-quality checkstyle:check`
- [ ] T016 [P] Wiki 回填（复合查询回填触发）：确认 `.wiki/pages/score-parse-service.md`
      是否需要追加"权重归一化（spec 047）"流程说明；如需则回填后 commit
- [ ] T017 全量回归：`cd backend && mvn test` + `npm run build`，`git status` 确认仅改动授权范围

---

## Phase N: Convergence

**Purpose**: `/speckit-converge` 核对 spec/plan/tasks 与代码库现状，追补缺口任务。核对结论：
US1/US2（归一化算法、确定性、比例保持）、US3 的信号持久化 + getItems 透出 + V1195 迁移 + DTO/entity
字段均已实现与文档一致；实测 12 条纯单测覆盖缺口/超载/恰好100/容差/空集/比例/确定性/字段保留/null 权重
等分支（≥ SC-002 的 10 条）。

**核对发现的唯一缺口**：T008（US3 契约/信号单测）未实现——`backend/src/test/java/com/xiyu/bid/scoreparse/`
下没有任何针对 `ScoreParseItemsDTO.Summary.originalTotalWeight` 序列化/可空、`ScoreParseTask.originalTotalWeight`
映射、`getItems` 透出信号的单测（grep `originalTotalWeight` 0 命中）。代码已接线但缺测试锚定，无法证明 US3
信号链路正确性。故追加下述任务补齐。

- [ ] C001 [US3] 补 US3 信号/契约单测（题干即 T008 缺口，路径
      `backend/src/test/java/com/xiyu/bid/scoreparse/application/OriginalTotalWeightSignalTest.java`）：
      - `ScoreParseItemsDTO.Summary(...)` 构造 + Jackson `ObjectMapper.writeValueAsString` 断言
        `originalTotalWeight` 字段在序列化 JSON 中存在、值正确、可空（`null` 时键为 `"originalTotalWeight":null`）
      - `ScoreParseTask` 反射/Getter 断言 `originalTotalWeight` 字段及 `@Column(name="original_total_weight")`
        映射存在（缺失时断言 FAIL）
      - `getItems` 透出逻辑：latest PARSE task 的 `originalTotalWeight` 非 null 时写入
        `Summary.originalTotalWeight`；null 时透传 null——若 `getItems` 依赖 DB/Spring 上下文，
        可将透出映射抽为纯函数 `SummaryAggregator`/DTO 装配测试，或对 load 'em DTO 构造函数做最小验证
      - 先红后绿：新测试当前缺失 → 断言 FAIL，再补最小实现（若实现已存在则直接 GREEN）
- [x] C002 [P] 门禁复核（归一化后 parse 日志语义）：`ScoreParseAppService.doExecuteParse` 完成日志
      `log.info(... weightResult.totalWeight() ...)`（约 L222-223）在归一化后打印的仍是归一化前的
      `weightResult`（如 90/200），与落库 items 实际合计 100 不一致。为排查口径一致性，改为在归一化后
      重算 `weightResult = weightSumCheck.checkCandidates(valid)` 再打印；若归一并重算后 `weightWarning`
      期望为 false。此为诊断语义修正，不改变落库业务正确性。

**Checkpoint**: C001 绿 + C002 日志口径一致 → Convergence 闭合。

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (T001)**: 无依赖
- **US1 (T002-T005)**: 依赖 T001；无其它阻塞（本 feature 无 Phase 2 Foundational）
- **US2 (T006-T007)**: 依赖 US1 的 T003（同一测试类）；可与 US3 并行（不同文件）
- **US3 (T008-T013)**: 依赖 US1 的 T003/T004（需要归一化已接入才有信号源）；可与 US2 并行
- **Polish (T014-T017)**: 依赖所有 User Story 完成

### User Story Dependencies 顺序建议

推荐串行实施 **US1 → US2 → US3**（US2 复用 US1 测试类、US3 复用 US1 的归一化结果），也可在 US1 完成后
并行办 US2 与 US3。

---

## Parallel Opportunities

- T009（迁移，纯 SQL）与 T010/T011（entity/DTO 字段，Java）可并行，互不阻塞
- US2（T006/T007）与 US3（T008-T013）在 US1 完成后可并行（文件完全独立）
- T014/T015/T016 为 Polish 阶段独立验证，可并行
- **不可并行**：同一文件内的连续任务（T002→T003→T004 同 `ScoreParse*` 链路；T010→T012 同 `ScoreParseAppService`）

---

## Parallel Example

```bash
# US1 完成后，同时派出两个任务：
Task: "TACK US2：在 WeightNormalizationPolicyTest 补确定性/相对比例用例"
Task: "TACK US3：new-migration.sh 建 V1194 + entity/DTO 字段 + AppService 信号透出"
```

---

## Implementation Strategy

### MVP First（仅 User Story 1）

1. T001 基线同步
2. **T002 先写 `WeightNormalizationPolicyTest`，确认 FAIL**
3. T003 实现 `WeightNormalizationPolicy`（纯核心）→ Green
4. T004 接入 `ScoreParseAppService` 编排 → 落库合计=100
5. T005 回归既有解析/评分单测
6. **STOP：运行 quickstart 单测验证 US1 独立可用**

MVP 即交付"落库合计恒=100"——这是用户"必须根治 totalWeight 90≠100"的核心诉求。

### Incremental Delivery

- US1 → 合计恒=100（根治数据正确性）
- US2 → 确定性/比例保持（质量加固）
- US3 → originalTotalWeight 信号透出（可见性/FR-005）
- 每个阶段独立验证、独立价值

### Parallel Team Strategy

单人可串行 US1→US2→US3；多人在 US1 完成后，US2（算法加固）与 US3（信号透出）可分派两人并行。

---

## Notes

- **[P]** = 不同文件无依赖才可并行；标有同文件上下游的任务禁止并行
- 先写测试并确认 FAIL（红），再实现（绿），最后重构
- 每个任务/逻辑组完成即 commit（遵循原子提交纪律）
- 迁移 MUST 走 `scripts/new-migration.sh` 取号（禁止手写版本号），V/U 成对
- 归一化只改写 `ScoreCandidate.weight` 与落库 items 权重；`weightWarning` 在合计=100 后不再置真
  （`originalTotalWeight` 承担"已补齐"语义，见 contracts/score-parse-api.md §兼容说明）
- 前端展示文案（FR-007）由产品确认，不阻塞本 feature 交付