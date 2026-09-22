# Tasks: 评分解析上游完整性修复

## Phase 1: 规格与基线

- [x] T001 [P] 创建并审查评分解析上游完整性规格 `specs/069-score-parse-upstream-integrity/spec.md`
- [x] T002 [P] 记录 Entry Gate、脏工作区继承项和定向测试基线 `.omc/entry-gate-20260922-scoreparse-upstream.md`

## Phase 2: 父子编码与叶子权重（US1/US2）

- [x] T003 [US1] 新增 parentCode 标准化纯策略及唯一匹配测试 `backend/src/main/java/com/xiyu/bid/scoreparse/domain/MultimodalFactParentCodeNormalizer.java` `backend/src/test/java/com/xiyu/bid/scoreparse/domain/MultimodalFactParentCodeNormalizerTest.java`
- [x] T004 [US1] 在多模态事实校验前应用标准 parentCode，并确保无法唯一解析时保持复核 `backend/src/main/java/com/xiyu/bid/scoreparse/application/MultimodalStructuredExtractionHandler.java`
- [x] T005 [US2] 增加汇总项与叶子项权重回归，移除不安全的模型权重自动覆盖 `backend/src/test/java/com/xiyu/bid/scoreparse/domain/ScoreCompletenessPolicyTest.java` `backend/src/test/java/com/xiyu/bid/scoreparse/application/MultimodalStructuredExtractionHandlerTest.java`

## Phase 3: 多表独立总分（US3）

- [x] T006 [US3] 修改多表无单表声明总分的判定，按表输出 UNKNOWN/REVIEW_REQUIRED，保留单表兼容路径 `backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreTableSplitPolicy.java` `backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreCompletenessPolicy.java`
- [x] T007 [US3] 更新多表回归测试，覆盖每表自洽、部分表自洽、多表无总分和单表兼容 `backend/src/test/java/com/xiyu/bid/scoreparse/domain/ScoreTableSplitPolicyTest.java`

## Phase 4: 收敛验证

- [x] T008 [P] 人工完成 Spec Kit 一致性分析；官方脚本因 Agent 分支命名门禁无法执行 `specs/069-score-parse-upstream-integrity/{spec.md,plan.md,tasks.md}`
- [x] T009 执行 ai-slop-cleaner 清理，删除本次范围内死代码、重复逻辑和不安全自动修正分支
- [x] T010 运行 scoreparse 定向回归、后端完整测试、架构门禁和 diff 检查；记录 `.omc/verify-20260922-scoreparse-upstream.md`
- [x] T011 通过独立 `xiyu-code-review` 审查并记录 S6 `MERGE_READY` 结论；已完成提交前门禁，可进入推送、合并与收尾

## 依赖与策略

- T003 → T004 → T005；T006 与 T003/T004 并行设计但共享完整性契约；T007 依赖 T006；T008 在实现前后均可分析但必须在 T009 前完成；T009 → T010 → T011。
- MVP 为 T003-T007：三个根因均有纯领域或应用回归；T008-T011 是交付门禁，不得跳过。
