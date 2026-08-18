# Implementation Plan: fix-score-parse-ghost

**Branch**: `agent/trae/fix-score-parse-ghost-and-order` | **Date**: 2026-08-17 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/046-fix-score-parse-ghost/spec.md`

## Summary

修复评分解析两类质量缺陷：(1) 表头/汇总幽灵项混入落库导致权重 200 —— merge 后加幽灵过滤 + 超 100 剔除策略；(2) 编号序失真与失败不可见 —— 落库前按编号数值排序、空编号不再补假编号、LLM chunk 失败重试并以 skippedChunks 透出。全程 FP-Java：3 个新 domain 纯函数策略类，应用服务只做编排接线。

## Technical Context

**Language/Version**: Java 21（backend，Spring Boot 3.2）

**Primary Dependencies**: Spring Data JPA、Flyway（MySQL 8.0）、JUnit5 + Mockito

**Storage**: MySQL `score_parse_task` 加列 `skipped_chunks INT NULL`（一条 V 迁移 + U 回滚）

**Testing**: `mvn test -Dtest=<类>`；新增 3 个 domain 测试 + 更新 3 个既有测试类

**Target Platform**: Linux 服务器（现有部署），无新平台

**Project Type**: web-service 后端增强（零前端改动）

**Performance Goals**: 无新增热点路径；过滤/排序均为内存纯函数 O(n log n) 以内

**Constraints**: Constitution IV 单文件 300 行硬上限（见 research R6 盘点，两文件压线需控制）

**Scale/Scope**: scoreparse 模块内 9 个文件改动 + 4 个新文件 + 1 迁移

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 判定 | 说明 |
|---|---|---|
| I. FP-Java | ✅ PASS | 3 个新策略均为 domain 纯函数（无框架依赖、输入→输出、不改入参）；应用服务仅编排调用 |
| II. Real-API Only | ✅ PASS | 零 mock 路径；LLM 调用走现有 OpenAiStructuredOutputService 真实链路 |
| III. TDD | ✅ PASS | 每个 FR 先写失败测试再实现（tasks.md T001-T005 前置） |
| IV. Split-First | ✅ PASS | 新逻辑独立文件而非撑爆现有类；R6 行数盘点全部 ≤300 |
| V. OSS | N/A | 不涉及 OSS 集成 |
| VI. Authorization | N/A | 不新增端点、不改权限 |
| VII. Defensive Collection | ✅ PASS | 无新增 Collectors.toMap；过滤/排序遍历均 null-safe（候选字段允许 null） |
| VIII. Boring Patterns | ✅ PASS | 无新框架；重试为单点私有方法，非通用框架 |

## Project Structure

### Documentation (this feature)

```text
specs/046-fix-score-parse-ghost/
├── plan.md              # 本文件
├── research.md          # Phase 0：R1-R6 决策与证据
├── data-model.md        # Phase 1：score_parse_task 加列 + 候选池流转模型
├── quickstart.md        # Phase 1：验证场景
├── contracts/
│   └── api-delta.md     # Meta DTO 增量契约
├── checklists/
│   └── requirements.md  # specify 质量检查（全过）
└── tasks.md             # speckit-tasks 生成
```

### Source Code (repository root)

```text
backend/src/main/java/com/xiyu/bid/scoreparse/
├── domain/
│   ├── GhostCandidateFilter.java        # 新增：幽灵候选过滤（FR-001）
│   ├── WeightOverloadTrimPolicy.java    # 新增：权重超载剔除（FR-002）
│   └── ScoreItemOrderPolicy.java        # 新增：编号数值排序（FR-003）
├── application/
│   ├── ScoreParseAppService.java        # 修改：接入过滤/剔除/skippedChunks（编排）
│   ├── ScoreItemPersistenceService.java # 修改：排序 + code 空串化（FR-003/004）
│   └── ScoreParseItemsMetaBuilder.java  # 修改：Meta 透出 skippedChunks（FR-005）
├── infrastructure/openai/
│   ├── OpenAiScoreAnalyzer.java         # 修改：chunk 重试 + RecallOutcome（FR-005）
│   └── RecallOutcome.java               # 新增：召回结果 record（避免 analyzer 超行数）
├── entity/ScoreParseTask.java           # 修改：加 skippedChunks 列映射
└── dto/ScoreParseItemsDTO.java          # 修改：Meta +skippedChunks

backend/src/main/resources/db/
├── migration-mysql/V____add_skipped_chunks.sql     # 新增（编号 new-migration.sh 分配）
└── rollback/migration-mysql/U____add_skipped_chunks.sql

backend/src/test/java/com/xiyu/bid/scoreparse/
├── domain/GhostCandidateFilterTest.java           # 新增
├── domain/WeightOverloadTrimPolicyTest.java       # 新增
├── domain/ScoreItemOrderPolicyTest.java           # 新增
├── application/ScoreParseAppServiceTest.java      # 修改（集成断言）
├── application/ScoreItemPersistenceServiceTest.java # 修改（排序/code 断言）
└── infrastructure/openai/OpenAiScoreAnalyzerTest.java # 修改（重试/计数断言）
```

## Modules and Interfaces

### M1 幽灵过滤（FR-001/FR-006）

`GhostCandidateFilter.filter(List<ScoreCandidate>) → List<ScoreCandidate>`
- 规则 1：normalize(dim).equals(normalize(detail)) 且 length ≤4
- 规则 2：code 空 且 sourceText 空白
- 保留原序、不修改入参（返回新 List）；被滤项由调用方记日志

### M2 超载剔除（FR-002/FR-006）

`WeightOverloadTrimPolicy.trim(List<ScoreCandidate>, totalLimit=100) → TrimResult(remaining, removed)`
- 触发条件由调用方判定（weightSumCheck total > 100.5）
- 剔除轮次：①无 code ②无 sourceText；每轮按候选序逐个剔、剔一个重算一次，total ≤100.5 即停
- TrimResult 携带被剔项供调用方 WARN 日志

### M3 编号排序（FR-003/FR-004）

`ScoreItemOrderPolicy.sort(List<ScoreCandidate>) → List<ScoreCandidate>`
- 解析键 `^(\d+)(?:[-—.](\d+))?$` → (major, minor)；major 升序、minor 升序（无 minor 在前）
- 不可解析编码稳定排序保持相对序，置于可解析之后
- persistItems 中 code 空白落空串（`""`），指纹比对同步（FR-004）

### M4 chunk 重试与可见性（FR-005）

`RecallOutcome(List<ScoreCandidate> candidates, int skippedChunks)`（infrastructure/openai 包内 record）
- `recallCandidates` 返回类型改为 RecallOutcome；内部 `requestCandidatesWithRetry` 失败重试 1 次
- ScoreParseAppService 落 `task.setSkippedChunks(outcome.skippedChunks())`
- MetaBuilder：`meta.skippedChunks = parseTask.getSkippedChunks()`

## Complexity Tracking

| 模块 | 行数预算 | 风险 | 缓解 |
|---|---|---|---|
| ScoreParseAppService 277→≤292 | 压线 | 插入 3 处接线 | 集成代码单行化，超限即抽私有方法 |
| OpenAiScoreAnalyzer 286→≤296 | 压线 | 重试逻辑 | RecallOutcome 独立文件 + 抽私有方法 |
| 迁移版本号 | 并发撞号 | new-migration.sh --reserve 预约 | pre-push gate 兜底 auto-fix |

## Manual Verification

- `mvn test -Dtest='GhostCandidateFilterTest,WeightOverloadTrimPolicyTest,ScoreItemOrderPolicyTest'`
- `mvn test -Dtest='ScoreParse*Test'`（模块回归）
- `mvn test -Dtest=ArchitectureTest,FPJavaArchitectureTest,MaintainabilityArchitectureTest`
- quickstart.md 场景（project 212 样本重放）

## Agent Task Assignments（并行编排）

| Wave | Agent | 范围 | 依赖 |
|---|---|---|---|
| 1（并行） | domain-core | M1+M2+M3 三策略类 + 三测试（全新文件） | 无 |
| 1（并行） | infra-visible | M4：RecallOutcome + analyzer 重试 + entity/DTO/迁移 + analyzer 测试 | 无（不碰 AppService） |
| 2（串行） | app-integrator | AppService 接线（M1/M2/M4 落库）+ Persistence（M3/FR-004）+ MetaBuilder + 集成测试 | Wave 1 两者完成 |

Wave 2 串行原因：AppService 是唯一热点文件，避免并行编辑互相覆盖。
