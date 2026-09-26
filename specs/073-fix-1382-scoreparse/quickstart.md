# Quickstart / Focused Verification

从实现worktree `/Users/user/xiyu/worktrees/xiyu-codex-fix-1382-post2767` 执行。

1. Sidecar RED/GREEN: `python3.13 -m pytest document-converter-sidecar/tests/test_pdf_table_artifact.py document-converter-sidecar/tests/test_converter_pdf.py document-converter-sidecar/tests/test_extract_tables_v2.py`
2. Backend evidence regression: `mvn -f backend/pom.xml test -Dtest='TextPrimaryEvidenceBinderTest,GoldenCasesReplayTest,ScoreParseEvidencePipelineGoldenReplayTest,ScoreReviewAdoptionPolicyTest,SchemeScopePublicationTest'`
3. Frontend drawer: `npx vitest run src/views/Project/stages/components/ScoreParseDrawer.spec.js src/composables/projectDetail/useScoreParseDrawer.spec.js`。
4. Golden/cross-format: `npm run eval:scoreparse:report`，逐项检查新AUTO_APPROVED、拒绝变化和输入顺序差异。
5. 后端健康/部署不能从当前环境推定；Flyway 1218阻塞期间不执行服务器重解析。

通过标准：所有定向测试为0失败；差分变化均已证实符合规格；无样本/环境缺失被计作通过。
