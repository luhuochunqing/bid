# Implementation Plan: 评分项证据链修复与跨文件隔离

**Branch**: `agent/codex/fix-1382-post2767` | **Date**: 2026-09-26 | **Spec**: `spec.md`

## Summary
以当前代码基线而非旧发布代码作RED基线。修复仅落在被证明仍失败的责任边界：Sidecar PDF章节事件、逐文档格式身份、PDF原文条目绑定、同范围名称/类别/最大分策略、当前任务候选与正式结果UI。历史诊断中的 Sidecar 章切若在当前基线回归已通过，则只补差分防护，不重写逻辑。

## Technical Context
**Language/Version**: Python 3.13, Java 21, JavaScript ES2022 / Vue 3
**Dependencies**: Sidecar pdfplumber/pytest; Spring Boot 3.2/JUnit; Vitest/Vue Test Utils
**Storage**: 现有数据库，无预期迁移
**Tests**: pytest、scoreparse JUnit、Vitest、Golden/跨格式评测
**Platform**: 西域投标平台后端、文档转换Sidecar及项目评分前端
**Constraints**: PDF专属策略必须按文档边界隔离；歧义继续REVIEW_REQUIRED；不改模型/OCR/历史结果；FP-Java领域策略不依赖框架。

## Constitution Check
- 采用 Spec Kit 073，与活动中的072部分采纳需求分开维护。
- 真实API唯一来源；不得mock验证业务语义。
- 先加失败用例，固定私有真实候选，不把expected反向注入被测输入。
- 混合格式多文件测试检查输入顺序不变量。
- 不迁移、不重解析、不部署，不覆盖已有正式结果。

## Architecture / File Scope
- `document-converter-sidecar/converter.py`: 仅在当前回归证实后调整PDF章级事件。
- `document-converter-sidecar/tests/test_pdf_table_artifact.py`: 子标题、真章、未知章和跨页反例。
- `backend/src/main/java/com/xiyu/bid/scoreparse/application/TextPrimaryEvidenceBinder.java`: 对已知PDF来源的同条目证据绑定。
- `backend/src/main/java/com/xiyu/bid/scoreparse/application/PdfRowEvidenceBinder.java`: PDF单格/跨格的唯一物理行范围和条目名证据策略；不承担DOCX/text策略。
- `backend/src/main/java/com/xiyu/bid/scoreparse/domain/OriginalScoreWeightEvidencePolicy.java` 与 `TextPrimaryScoreFactMapper.java`: 同范围类别/分值语义；拒绝歧义。
- 对应 `backend/src/test/java/com/xiyu/bid/scoreparse/**`：真实引用和语义边界测试。
- `src/composables/projectDetail/useScoreParseDrawer.js`、`src/views/Project/stages/components/ScoreParseDrawer.vue` 与相邻spec：当前任务候选与已生效项并显，最新解析时间不得冒充旧项更新时间；不增加当前DTO没有提供的历史任务ID。
- 多文件文件类型来源构建处及测试：若复现首文件格式泄漏，改为按源文档关联；不要只给artifact顶层加单一类型。
- `specs/073-fix-1382-scoreparse/`: 规格、研究、数据模型、快速验证及任务清单。

## Data / Compatibility
不加持久化字段或外部API字段，除非现有产物缺少不可替代的源文档身份且测试证明无法从已有artifact恢复；此时先定向数据模型和兼容方案。旧产物缺少证据仍走原资格规则，绝不升级资格。

## Verification and Release Boundary
逐根因RED/GREEN；Sidecar→固定候选Java回放；合并跑其他PDF、DOCX、text/OCR、多文件顺序、总分和资格负例差分。跑前端定向测试。任何旧AUTO_APPROVED集合变化逐项人工解释。真实测试服务器当前Flyway 1218失败，不能宣称重解析或浏览器验收完成；本分支交付上限为已审查本地工作树。
