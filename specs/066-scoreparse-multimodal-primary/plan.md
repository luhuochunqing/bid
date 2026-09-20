# Implementation Plan: 多模态模型主导评分项解析

**Branch**: `agent/codex/scoreparse-multimodal-primary` | **Date**: 2026-09-20 | **Spec**: `spec.md`

## Summary

将 `DocumentArtifact v2` 的表格网格、原始单元格与评分页图片一次性提交给结构化多模态模型。模型负责召回和结构化；纯 Java 验证器只做可证明的完整性校验。全部强校验通过才自动发布，失败则完整保留候选、差异和证据。主客观分类独立于召回和知识匹配，证书/业绩预计得分输出逐记录逐条件证明。

## Technical Context

- Java 21 / Spring Boot 3.2；Vue 3 / Vite 5 / Element Plus。
- 复用 `OpenAiStructuredOutputService`、`SidecarDocumentArtifactExtractor`、`SidecarImageExtractor`。
- MySQL 8.0；优先复用现有 fact/evidence JSON，不新增表。
- JUnit 5、Vitest、ArchUnit；五个指定项目脱敏金标回放作为发布门禁。
- 模型失败或验证失败必须 fail closed：保存异常候选，不覆盖正式项。

## Constitution Check

| 原则 | 判定 | 说明 |
|---|---|---|
| FP-Java | PASS | 验证器、分类器与证明模型为无 Spring 的纯核心 |
| Real API | PASS | 生产仅调用真实 Sidecar/模型/知识库；测试使用值对象 fixture |
| TDD | PASS | 先写验证、分类、发布门禁和证明红测 |
| Split-first | PASS | 模型适配器、验证器、发布编排分离，不继续膨胀 AppService |
| Security | PASS | 不新增端点/权限；日志只记元数据，不记正文 |
| Defensive degradation | PASS | 模型不可用进入 REVIEW_REQUIRED，禁止正则静默发布 |
| DB migration | N/A | 复用既有 JSON 和事实表 |

## Architecture

```text
LoadedBidFile
  -> DocumentArtifact v2 + scoring-page images
  -> MultimodalScoreFactExtractor (one structured request)
  -> MultimodalScoreFactValidator (pure deterministic checks)
  -> AUTO_APPROVED -> existing item persistence + estimation
     REVIEW_REQUIRED -> fact/evidence snapshot + editable review
```

旧 `ScoreFactShadowPipeline` 仅在模型不可用时产生诊断候选，永不取得自动发布资格。

## Source Scope

- `scoreparse/domain`: 多模态事实契约、验证报告、分类策略。
- `scoreparse/application`: 输入组装、发布状态机和可审计知识证明。
- `scoreparse/infrastructure/openai`: 单次结构化多模态调用。
- `ScoreCandidateReviewCard.vue`: 完整候选、证据、差异和编辑入口。

## Delivery Gates

1. 单元红绿：验证器、分类器、失败不覆盖、知识逐条件证明。
2. API/UI 兼容测试。
3. 项目 35/43/580/1117/1118 脱敏回放。
4. `mvn test`、`npm test/build`、架构门禁、Spec Kit analyze。

