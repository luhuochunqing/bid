# Tasks: 同来源评分身份与 OCR 失败可见性

## Phase 1 Setup
- [x] T001 固定任务182、原件hash、生产SHA与PR因果证据，保存在 `.runtime/score43/`。
- [x] T002 完成 `specs/20261104-score-source-identity/` 的spec/plan/研究与契约，检查宪法。

## Phase 2 Foundational RED
- [x] T003 [US1] 在 `ScoreFactChunkMergerTest.java` 与 `SourceRecheckPlanTest.java` 增加同源别名及父子/子项边界，实际运行RED。
- [x] T004 [US2] 新增 `BaiduOcrFailureDiagnosticsTest.java` 与 `BaiduOcrConfigurationTest.java`，实际运行RED。

## Phase 3 US1 (P1)
- [x] T005 [US1] 实现 `SourceFactIdentity.java` / `SourceFactDeduplication.java`，接入两个入口。
- [x] T006 [US1] `Project43SourceIdentityRegressionTest.java` 通过实际handler验证11项与完整原文，加入现有golden回放登记。

## Phase 4 US2 (P1)
- [x] T007 [US2] 在 `application.yml` 统一默认OCR凭证引用，保留专用环境覆盖。
- [x] T008 [US2] 在 `BaiduTableArtifactEnricher.java` 与 `SourceArtifactAcquisition.java` 保留并透传失败诊断。
- [x] T009 [US2] 用真实原件完成本地OCR与提取回放，保存脱敏结果于 `.runtime/score43/`。

## Phase 5 Verification
- [x] T010 运行影响域测试、反事实验证、架构/文档门禁，写 `.omc/verify-01a1a49b-8314-72e3-b53f-c1b3b5f43bdd.md`。
- [x] T011 执行ai-slop-cleaner ModeB及独立审查，更新 `.wiki/pages/scoreparse-text-paragraph-and-continuation-fix.md` 与 `docs/lessons/lessons-learned.md`。
- [x] T012 更新 `.runtime/score43/evidence.json` 并运行root-cause修复证据检查，记录实际发布边界。

## Dependencies and execution
T001→T002→T003/T004→T005/T007/T008→T006/T009→T010→T011→T012。US1和US2可独立验证；本任务串行编辑，共享文件不并行修改。独立审查在实现完成后委派，用户无需重新批准已授权的修复方案。

原件新模型回放完成：11项100分；br/物理换行差异修复后全部规则非空。T010-T012等待最新测试与最终审查记录，不代表生产发布。
