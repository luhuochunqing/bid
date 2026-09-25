# Implementation Plan: 评分候选部分采纳与中文校验说明

**Branch**: `agent/zcode/scoreparse-partial-adoption-ux-1384` | **Date**: 2026-09-25 | **Spec**: `specs/072-scoreparse-partial-adoption-ux/spec.md`

## Summary

解除已验证声明总分与候选合计必须完全相等的错误拦截：明确人工采纳时允许实际候选叶子总分不超过声明总分，持久化实际合计并保留差额审计。前端同步允许低于总分，继续阻止超额、总分冲突/未知、未选方案。校验差异使用前端展示层中文映射，原始事实与机器诊断仍保留在 API 和审计中。

## Technical Context

**Language/Version**: Java 21, JavaScript ES2022, Vue 3
**Primary Dependencies**: Spring Boot 3.2, JUnit 5/Mockito/AssertJ, Vitest/Vue Test Utils
**Storage**: 现有 MySQL/JPA，无 schema 变化
**Testing**: `mvn -Dtest=ScoreParseReviewAppServiceTest test`; Vitest `ScoreCandidateReviewCard.spec.js`
**Target Platform**: 西域数智化投标管理平台 Web + API
**Project Type**: Web application (frontend + backend)
**Performance Goals**: 无额外查询或复杂度增长；评分事实线性映射
**Constraints**: 保持 FP-Java：核心判断置纯策略或简单明确的服务边界；不改变资格证据、方案状态、父子聚合契约；禁止隐藏原始审计数据
**Scale/Scope**: 两个既有模块及对应测试，无数据库迁移

## Constitution Check

- 复用现有人工采纳服务和候选卡，不新建框架或接口。
- 对后端上限判断增加领域纯策略，确保可单测且在写入前调用；已有事务编排保持不变。
- 前端错误解释放在候选卡所在展示边界，不污染原始 API 数据。
- RED/GREEN 验证后自审，并覆盖超额和冲突反例。

## Project Structure

```text
specs/072-scoreparse-partial-adoption-ux/ (feature docs)
backend/src/main/java/com/xiyu/bid/scoreparse/domain/ (adoption upper-bound policy if needed)
backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreParseReviewAppService.java
backend/src/test/java/com/xiyu/bid/scoreparse/application/ScoreParseReviewAppServiceTest.java
src/views/Project/stages/components/ScoreCandidateReviewCard.vue
src/views/Project/stages/components/ScoreCandidateReviewCard.spec.js
```

## Design Decisions

1. Keep `task.originalTotalWeight` as the actual adopted candidate sum (the current field is already assigned from the adopted result); do not overwrite it with the declared 100. Preserve declared total through the existing verified scheme evidence.
2. Change equality to an upper-bound rule after checking total evidence. Explicitly reject `candidateTotal > declaredTotal`; lower totals are accepted only through `ADOPT_CANDIDATES` human review. Reject each negative original/adjusted item weight in the pure adoption policy so a negative cannot offset another item.
3. Map known validator identifiers and weight difference to Chinese at render time. Do not display raw validator status/message, serialized JSON, source cell IDs or internal IDs in user-facing copy. Preserve source quotations; provide a generic Chinese fallback for unknown diagnostics.
4. Do not change `candidateEligible` or source-qualification validation. Whether the submitted 91-point items remain eligible is independently enforced by existing qualification evidence.

## Verification

- RED: backend app-service test for 91/100 adoption and 101/100 rejection; frontend component test for 91 enabled/9-point notice, 101 disabled, and localized diagnostics.
- GREEN: run the focused backend test and focused Vitest component test.
- Regression: retain existing tests for exact-total, conflicting/unknown total, multiple schemes, source eligibility, and child aggregation.
- Run `scripts/preflight-self-review.sh`, `scripts/speckit-analyze.sh` if present (otherwise analyzer skill path), and targeted frontend build if applicable.
- Production-site DOCX reparse and click-through remain post-deployment acceptance, outside this PR.
