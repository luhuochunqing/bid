# Implementation Plan: 评分候选及采纳正分约束

**Branch**: agent/codex/scoring-only-adoption | **Date**: 2026-11-11 | **Spec**: [spec.md](spec.md)

## Summary
已证实项目1120正式项来自task203（49项/38零分/100分），项目44来自task215（56项/41零分/100分）。两者有人工采纳记录。确定性抽取保留非计分行不是应删掉的证据；错误在“有方案归属即可采纳”和显式选择后零分放行。复用现有纯策略阻断正式入库，并让旧候选投影与前端选择一致。

## Technical Context
Java21 / Spring Boot3.2 / JUnit5，Vue3 / Vitest。无新依赖，无表结构或公开接口变更。基准57164cb98，生产诊断部署记录2960e7ea5；关键旧采纳策略文件在两版本间无差异。私有客户原件不入Git，使用合成混表夹具回放。

## Constitution Check
已读.specify/memory/constitution.md及ARCHITECTURE。纯核心：SourceDeductionSelectionPolicy、ScoreReviewAdoptionPolicy（OriginalScoreQualificationPolicy保持不变），无I/O；副作用Shell：现有ReviewAppService持久化及事务不新增；Mapper仅重新投影历史资格，前端共享选择函数不创建第二条提交路径。无Mock业务/远端数据、无main修改、无回退恢复重写。测试沿用现有仓储替身但业务策略及回放真实执行。

## Design and bounded alternatives
1. 保留OriginalScoreQualificationPolicy来源语义，不修改内部candidateEligible，否则行覆盖和版本化零分扣分判定会回归。
2. 复用SourceDeductionSelectionPolicy.verified(fact,qualification)，由ScoreParseFactEvidenceWriter在完整来源且方案内的零分扣分事实保存SOURCE_VERIFIED_ZERO_DEDUCTION标记，quotedText绑定未改写原文。仅公开既有纯策略，不复制关键词实现。
3. Review Mapper对历史非正分重算candidateEligible，唯可信扣分例外保留。标记需绑定factId/原文/方案资格，不能凭历史true或扣分词放行。
4. ScoreReviewAdoptionPolicy检查调整后分值；负分失败，零分/缺分跳过，例外仅服务端传入的可信零分扣分标志。ReviewAppService传递持久化可信证据，修改sourceText后不得沿用标志，不新增客户端信任字段。
5. manualScoreCandidate共享函数默认将非计分行放审计，可信零分扣分例外与后端一致；manualIncluded允许编辑补分但不能绕过后端。完整原始事实保留，正式读取不掩盖旧数据。
拒绝：GET全过滤（掩盖脏数据）；删零分事实（丢证据）；资格词黑名单（误伤业绩计分）；生产批量DELETE（未授权且不能证明全部零分无意义）；新row-kind平台（本次无必要）。

## Execution slices / checkpoints
A. 写AC01/02/03/04/05失败测试与合成金标回放；记录RED必须是业务断言失败。
B. 内部来源语义保持，新增可信零分扣分证据及历史Mapper投影；采纳纯策略排除无证据的非正分并保留可信扣分；前端共享选择和审计提示。
C. 运行定向测试、金标、父子聚合与多方案相邻测试；修复相关回归，保留原文事实数量。
D. ai-slop-cleaner Mode B清理，重新执行验证，独立google/xiyu审查；最多三轮。
E. docs/lessons和Wiki回填；逐条对spec/tasks核对。代码验证与生产恢复严格分开。

## Validation commands
- `mvn -f backend/pom.xml -Dtest=ScoreReviewAdoptionPolicyTest,OriginalScoreEvidencePolicyTest,ScoreParseReviewAppServiceTest,ScoringOnlyAdoptionGoldenReplayTest test`
- `npx vitest run src/composables/projectDetail/manualScoreCandidate.spec.js src/composables/projectDetail/useScoreCandidateAdoption.spec.js src/views/Project/stages/components/ScoreCandidateReviewCard.spec.js`
- `mvn -f backend/pom.xml -Dtest=GoldenCasesReplayTest,FPJavaArchitectureTest,MaintainabilityArchitectureTest,ProjectAccessGuardCoverageTest test`
- scoreparse领域/应用完整受影响测试（不启Spring全量生产连接）。
- `npm run build`，`git diff --check`，`node scripts/check-line-budgets.mjs --base origin/main`（先核实参数）。
- `python3 scripts/review-workflow.py run --verify '["git","diff","--check"]' --verify '["mvn","-f","backend/pom.xml","-Dtest=ScoreReviewAdoptionPolicyTest,OriginalScoreEvidencePolicyTest,ScoreParseReviewAppServiceTest,ScoringOnlyAdoptionGoldenReplayTest","test"]'`

## Rollout / historical recovery
见[quickstart.md](quickstart.md)。本次working_tree，不push/PR/部署。没有health CLI或codehealth连接器，测量不可用作显式豁免；用架构/行预算/回归和独立审查检查结构。原文生产验收未执行不能报现场恢复。

## Review history
独立Spec/Standards首轮NEEDS_FIXES：936领域测试2失败，证明内部source资格与可计分不能合并；存在既有可信零分扣分契约。撤回该内部改动并复用既有策略新增可信持久化证据。旧自动清理流程因方案失效主动取消，不复用旧凭证；所有验证与独立审查重跑。总审查轮数最多3。

## Verified baseline limitation
基准57164cb98干净codex锚点只运行OriginalScoreEvidenceRoleAndHeadingTest，11项中1失败，与任务区相同textFactUnderChildHeadingRemainsWithinParentScoringScheme:116。日志/tmp/scoring-baseline-heading.log；该测试输入将文本商务100事实与结构化销售10+技术90表混用，原文覆盖校验拒绝。当前不扩大范围修改基线行为/断言。完整领域回归保留此失败，受影响其余项须全部通过。
