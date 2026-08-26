# Implementation Plan: AI 评分标准解析两阶段归并 + 表格行保真

**Branch**: `agent/claude/score-parse-global-merge` | **Date**: 2026-08-26 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/055-score-parse-global-merge/spec.md`

## Summary

在评分解析管线中新增两个治本机制：(1) **全局归并裁决**——启发式合并去噪后，将候选池与评分章节原文一次交给 LLM 做身份裁决（同项归并/异项保留/dim 归一），失败降级回启发式结果；(2) **表格行保真**——新增表格感知切片守卫（边界不切断管道表格行）与"召回五：表格行直抽"通道（一行 = 一候选）。修复真实缺陷：S9/A2 重复、A1 残缺成项、S28 吞并 4 分独立项。

## Technical Context

**Language/Version**: Java 21（backend）+ Spring Boot 3.2；sidecar Python 仅复用现有 markdown 输出，本次不改 Python

**Primary Dependencies**: OpenAiStructuredOutputService（LLM 结构化输出，既有）、StructuralDocumentChunker/TextChunker（切片，既有只读）、MarkdownScoreSectionLocator（评分区域定位，既有只读）、PriceScoringKeywords（价格词元单一真相源）

**Storage**: 无新表；解析产物仍落 `score_item`（既有 JPA 实体，字段不变）

**Testing**: JUnit 5 + AssertJ 纯函数单测（domain 层零框架依赖）；编排层沿用 ScoreParseAppServiceTest 既有模式

**Target Platform**: 服务端异步任务（scoreParseExecutor 线程池）

**Performance Goals**: 解析总耗时增幅 ≤30%（新增 1 次 LLM 调用 + 轻量文本处理）

**Constraints**: 单文件硬上限 300 行（OpenAiScoreAnalyzer 已 293 行 → 新职责必须落新类）；FP-Java 纯核心可单测；LLM 失败 fail-open 不阻断任务

**Scale/Scope**: 后端 scoreparse 模块内改动；约 6 个新类 + 2 个既有类小改 + 5 个新测试类

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 状态 | 说明 |
|------|------|------|
| I. FP-Java | ✅ | 新增表格定位/行解析/幻觉防御均为 domain 纯函数（record/final，无框架依赖）；LLM 调用留在 infrastructure |
| II. Real-API Only | ✅ | LLM 走既有 OpenAiStructuredOutputService 真实调用；无任何 Mock 路径 |
| III. TDD | ✅ | 每个新类先写失败测试；ArchUnit 保持全绿（新类按包分层放置） |
| IV. Split-First ≤300 行 | ✅ | 新职责拆分至 ScoreGlobalMergeExecutor / ScoreTableRecallService 等，均 <200 行；不动已 293 行的 OpenAiScoreAnalyzer 主干（仅加一行委托） |
| VII. Defensive & Graceful Degradation | ✅ | 归并 LLM 两连败降级启发式结果；候选 <2 跳过调用；降级记日志 |

## Project Structure

### Documentation (this feature)

```text
specs/055-score-parse-global-merge/
├── plan.md              # 本文件
├── research.md          # Phase 0 决策记录
├── data-model.md        # Phase 1 实体与契约字段
├── quickstart.md        # Phase 1 验证指南
├── contracts/
│   └── llm-global-merge-schema.md  # 归并裁决 LLM 输出契约（演进自 spec 041 schema）
└── tasks.md             # Phase 2 输出（/speckit-tasks）
```

### Source Code (repository root)

```text
backend/src/main/java/com/xiyu/bid/scoreparse/
├── domain/
│   ├── ScoreTableBlockLocator.java     # [新] 定位含评分语义的管道表格块 → 行文本列表
│   ├── ScoreTableRowParser.java        # [新] 表格行 → ScoreCandidate（列对齐/合并单元格降级）
│   ├── ScoreTableAwareChunker.java     # [新] 包装结构化切片器：超长块按表格行边界重切
│   └── ScoreMergeResultGuard.java      # [新] 幻觉防御：detail⊆原文才采纳 + 高置信漏项回补
├── infrastructure/openai/
│   ├── ScoreGlobalMergeOutput.java     # [新] 归并裁决 LLM 结构化输出契约
│   ├── ScoreGlobalMergeExecutor.java   # [新] 召回五 + 全局归并调用与重试（fail-open 语义）
│   └── ScoreParsePrompts.java          # [改] +buildGlobalMergePrompt（静态方法追加）
└── application/
    └── ScoreParseAppService.java       # [改] 编排插入：召回五并入候选池 / 归并阶段插入门禁前

backend/src/test/java/com/xiyu/bid/scoreparse/
├── domain/{ScoreTableBlockLocator,ScoreTableRowParser,ScoreTableAwareChunker,ScoreMergeResultGuard}Test.java
├── infrastructure/openai/ScoreGlobalMergePromptTest.java
└── application/ScoreParseAppServiceTest.java  # [改] 补归并编排用例
```

**Structure Decision**: 全部落在既有 scoreparse 模块分层内（domain 纯核心 / infrastructure LLM / application 编排），不新建模块；docinsight 的 StructuralDocumentChunker 零侵入（包装而非修改），避免跨模块回归。

## Complexity Tracking

> 无宪法违规需要豁免。
