# Implementation Plan: 评分解析与打分的花费守卫

**Branch**: `044-score-llm-spend-guard` | **Date**: 2026-08-16 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/044-score-llm-spend-guard/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

在 043 抽屉门闩之上：招标保存事件走同一套「从未解析才自动新建」；30 分钟内自动失败 2 次熔断自动路径；投标文件内容相对上次成功打分未变则跳过智能评估（手点也跳过）；仅部分章节变化则只重评相关项；用户可选手动重打范围。不做额度/扣费。权限、计分公式、四路召回不改。

## Technical Context

**Language/Version**: Java 21 + Spring Boot 3.2；前端 Vue 3 + Vitest

**Primary Dependencies**: 现有 `scoreparse` 模块、`TenderDocumentStoredListener`、`ScoreScoringAppService`、`ScoreDocExcerptExtractor`

**Storage**: Flyway 给 `score_parse_task` / `score_result` 加可空列（触发来源、投标内容指纹、章节指纹、结果沿用标记）。无新业务表。

**Testing**: JUnit5 + Mockito（门闩/熔断/跳过/脏章纯策略）；Vitest（打分范围与跳过提示）

**Target Platform**: 与现网一致的 Linux 单体 + 浏览器控制台

**Project Type**: 现有 Web 单体上的增量

**Performance Goals**: 文件未变时打分路径不发起 LLM；增量时 LLM 次数不超过相关项数

**Constraints**: `ScoreParseAppService` / `ScoreScoringAppService` / `useScoreParseDrawer.js` 硬上限 300 行；策略进同包新类。`scoreParseExecutor` 不扩容。不做 Token 账本。

**Scale/Scope**: 监听器 + 打分触发 + 抽屉范围选择；约 8–12 个文件

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 检查 | 结论 |
|---|---|---|
| I. FP-Java | 自动门闩、熔断、哈希跳过、脏章判定做成无框架纯策略；AppService 只编排 | ✅ PASS |
| II. Real-API | 不引入 mock 打分路径 | ✅ PASS |
| III. TDD | 先写策略/抽屉失败用例再改实现 | ✅ PASS（计划内） |
| IV. Split-First | 不向已近 300 行的 AppService 塞策略；新类默认 <100 行 | ✅ PASS |
| V. OSS | 不涉及 | ✅ N/A |
| VI. Authorization | 不改 `@PreAuthorize`；仍 `assertCurrentUserCanAccessProject` | ✅ PASS |
| VII. Defensive Collection | 不新增无 merge 的 toMap | ✅ PASS |
| VIII. Boring | 复用任务表加列，不新建计费表 | ✅ PASS |
| File Upload 20MB | 本特性不改上传上限；读投标指纹走已有文件 | ✅ N/A |

**Phase 1 复检**：契约只给打分触发增加可选范围与结果形态（SKIPPED/INCREMENTAL/FULL）；旧客户端不传范围则视为 ALL。无 Complexity Tracking 违规。

## Project Structure

### Documentation (this feature)

```text
specs/044-score-llm-spend-guard/
├── plan.md
├── spec.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── score-spend-guard.md
└── tasks.md             # /speckit-tasks 生成（本命令不创建）
```

### Source Code (repository root)

```text
backend/src/main/java/com/xiyu/bid/scoreparse/
├── domain/AutoParseGate.java              # 从未解析且无项才允许自动新建
├── domain/AutoFailCircuit.java            # 30min / 2 次自动失败
├── domain/BidScoreSkipPolicy.java         # 指纹相同且清单未变 → 跳过
├── domain/BidChapterDirtySet.java         # 章节切分与脏章
├── domain/ScoreItemChapterMatch.java      # 项是否与脏章相关
├── application/TenderDocumentStoredListener.java
├── application/ScoreParseAppService.java  # 事件路径走门闩+熔断
├── application/ScoreScoringAppService.java # 跳过 / 增量 / 范围
├── entity/ScoreParseTask.java
├── entity/ScoreResult.java
src/composables/projectDetail/useScoreParseDrawer.js
src/views/Project/stages/components/ScoreParseDrawer.vue
```

**Structure Decision**: 不新建业务包。门闩/熔断/跳过/脏章放 `scoreparse.domain` 纯核心；监听器与打分编排只调用策略。Flyway 用 `scripts/new-migration.sh` 预约版本。

## Complexity Tracking

> 无 Constitution 违规需登记。
