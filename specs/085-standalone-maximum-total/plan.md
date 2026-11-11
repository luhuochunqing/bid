# 独立满分汇总行 Implementation Plan

**Goal:** 消除满分汇总行重复100分，并恢复同源总分证据。
**Architecture:** ScoreTableSummaryPolicy提供纯汇总标签规则，OriginalScoringContext复用；DeterministicScoreFactExtractor保留现有调用。纯核心是scoreparse/domain，副作用外壳Controller/Application/Repository不改。
**Tech Stack:** Java21/JUnit5，Vue3/Vitest，现有Markdown金标回放。

## Global Constraints
无Mock/新依赖/迁移；客户原文不入库；delivery=working_tree。复用既有隔离worktree，诊断Wiki保留。Spec Kit optional commit hooks不执行，constitution只读。编号按现存目录最大084生成085（旧branch_numbering按sequential迁移读取）。

## Route and Resume
已有诊断与新修复compatible_drift，无本修复controller；旧feature.json属于scanned-bid-scoring，另建085。medium/bug/data_integrity，proof=RED-first，assurance=mutation，controller=none。按下列步骤顺序执行；不再等待计划审批，用户已明确要求计划后实施。

## Constitution Check
FP-Java纯函数、不改输入、不引入IO；两处规则消重，保留单项语义；无权限/API/schema变更。前后检查均需成立。

## Task 1: RED与最小核心修复
Files: ScoreTableSummaryPolicy.java、OriginalScoringContext.java、NEW StandaloneMaximumSummaryTest.java、ScoreTableSummaryPolicyTest.java。
Interface: isSummaryLabel(String)规范化标签识别；已有isSummaryRow(List<CanonicalCell>)和resolve(DocumentArtifact,String)契约不变。
- [x] 编写合成四列表40+60、表尾满分100及两表同方案回归；断言事实2、合计100、总分100、数值格locator。
- [x] 执行 `cd backend && mvn -Dtest=ScoreTableSummaryPolicyTest,StandaloneMaximumSummaryTest test`；保存RED，失败必须为伪满分事实/缺少总分。
- [x] 在共享标签识别中接受独立“满分”，原文context复用；不做文本全局contains满分排除。
- [x] 同命令GREEN；保留正常单项细则和合计兼容。

## Task 2: 金标与跨层防回归
Files: GoldenCasesReplayTest.java、NEW case-standalone-maximum-total.md/.expected.json、useScoreCandidateAdoption.spec.js。
Interface: fixtures为SYNTHETIC FIXTURE；case注册使用既有回放入口，前端仅验证输出契约。
- [x] 创建100分明细+满分汇总金标，验证原文总分及提取事实；注册cases。
- [x] 前端两条40/60明细、来源总分100，断言候选总权重100；保留既有父子/来源未核验语义。
- [x] `cd backend && mvn -Dtest=GoldenCasesReplayTest,ScoreTableSummaryPolicyTest,StandaloneMaximumSummaryTest test`
- [x] `npx vitest run src/composables/projectDetail/useScoreCandidateAdoption.spec.js`。

## Task 3: 清理、验证、审查和交付
- [x] ai-slop-cleaner Mode B：仅检查本diff，删除重复标签判断，保持纯核心，不引入封装层。
- [x] 重跑任务1/2以及相邻OriginalScoring*、ScoreSubtotal*、DeterministicScoreFactExtractor*测试；`npm run build`，`git diff --check`。
- [x] 在隔离临时文件恢复关键标签缺陷，同一回归重新失败，再还原并重跑GREEN。
- [x] Spec Kit analyze逐项FR→任务→测试一致性检查；知识追加docs/lessons和原Wiki。
- [x] 独立只读google-code-review、xiyu-code-review（包含spec对照与反例）；最多三轮，变化使证据失效。
- [x] 保存.omc/verify和review工件，列出未执行的生产验收，停止于本地working_tree。

## Alternatives and Risks
只改前端过滤会丢合法SOURCE_UNVERIFIED明细且总分仍UNKNOWN；硬编码100不适用其他总分。共享纯规则为最小完整边界。health/codehealth工具未提供，替代证据为固定HEAD文件行数/导入/重复规则比较及编译测试，明确非health认证。生产当前快照不会自动被此本地修复重算。

## 前一阶段验证记录
2026-11-11：后端131、前端19、build通过；反证4失败/恢复4通过；双独立审查第2轮MERGE_READY。本地未提交，未部署/重解析。

## 三技能审查 fix 第1轮

新增无表头独立汇总表双表回归，先确认合计/总计/满分三例总分为空，再收窄OriginalScoringContext的表头排除条件。复用NUMBER/EXPLICIT_TOTAL及ScoreSubtotalColumnPolicy的列选择和单项排除规则。保留满分列表头反例，执行StandaloneMaximumSummaryTest、ScoreTableSummaryPolicyTest、Project1359ScoringContractTest及GoldenCasesReplayTest。当前阶段禁止提交、push、PR、部署、自批凭证和嵌套review-workflow；历史验证结论不作为本轮复审结论。

本轮执行：`cd backend && mvn -o -Dtest=ScoreTableSummaryPolicyTest,StandaloneMaximumSummaryTest,Project1359ScoringContractTest,GoldenCasesReplayTest test`，119例通过、失败/错误/跳过均为0。未启动开发服务；需要监听端口的验证由主执行器verify阶段执行。

## 三技能审查第2轮：清理阶段

完整审阅基线1545e85483b9adb163cf29bfcdc3dcdd6f309b89至工作区的22个文件（含13个未跟踪文件）。修改前先执行上述119例行为保护测试，全部通过；仅在OriginalScoringContext中将三处重复的表头边界比较复用为局部变量headerRow，不改变独立汇总标签、数值证据或列选择规则，不新增封装或依赖。修改后同一命令再次119例通过，失败/错误/跳过均为0；已跟踪差异和全部未跟踪文件的空白检查通过。

本阶段另改本计划记录，入口记录为宿主要求的忽略运行工件。Vitest的19个断言通过，但命令因WebSocket监听EPERM和共享node_modules缓存写入EPERM退出1，不计为验证通过，不再尝试监听；交由主执行器verify阶段执行。独立审查仍由后续阶段负责；本阶段未写自批凭证，未提交、推送、创建PR或部署。

## 后续交付授权（2026-11-11）
用户明确要求“提交PR”，交付上限由初始working_tree提升为pr：允许本地提交、推送origin任务分支并创建Gitee PR；不合并、不部署、不改生产数据。提交前同步最新origin/main（精确基线以当轮门禁凭证为准），重新执行三技能流程和实际测试。前述未提交/本地限定为前一阶段的历史状态。

## 三技能审查 fix 第2轮：构建行数门禁

基线1545e85483b9adb163cf29bfcdc3dcdd6f309b89的完整差异含22个文件（13个未跟踪文件）。复现`node scripts/check-line-budgets.mjs`退出1：OriginalScoringContext由300行增至304行。将来源DTO汇总行判定原样移至既有ScoreTableSummaryPolicy的包内静态方法isSourceSummaryRow，保留独立标签、NUMBER/EXPLICIT_TOTAL、表头边界和短路顺序；未修改测试或门禁。OriginalScoringContext现300行，ScoreTableSummaryPolicy现193行。

修改前后均执行`mvn -o -f backend/pom.xml -Dtest=ScoreTableSummaryPolicyTest,StandaloneMaximumSummaryTest,GoldenCasesReplayTest,Project1359ScoringContractTest,FPJavaArchitectureTest test`，各127例通过，失败/错误/跳过均为0。修改后行数门禁、`npm run build`及`git diff --check`退出0，13个未跟踪文件无尾随空白；构建日志位于`/tmp/score1123-fix-round2-build.log`。未运行需要监听端口的Vitest，交由主执行器verify阶段执行；本阶段不沿用历史复审结论、不写自批凭证、不执行嵌套审查或收尾，未提交、推送、创建PR、部署或启动开发服务。
