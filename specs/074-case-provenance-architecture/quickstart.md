# 验证

mvn -f backend/pom.xml test -Dtest=BidCaseSliceArchitectureTest,CaseProvenanceBackfillAppServiceTest,CaseProvenanceBackfillCommandLineRunnerTest,CaseProvenanceBackfillAdapterMysqlTest,BidDocumentLoaderTest

真实MySQL使用AbstractMysqlIntegrationTest约定的127.0.0.1:13306测试容器；全量设置SIDECAR_PYTHON为已有可用虚拟环境解释器，串行运行mvn test并生成JaCoCo报告。不得把受影响子集当成全量通过。


## 阻塞闭合前的验证证据（2026-10-09）

- 基线架构复现：20项、1失败、0错误、0跳过，32处跨层依赖；日志 `/tmp/case-provenance-architecture-baseline.log`，退出1。
- 首轮受影响回归：64项、0失败、0错误、0跳过，日志 `/tmp/case-provenance-green.log`，退出0。
- 最终完整运行：`SIDECAR_PYTHON=/Users/user/xiyu/worktrees/trae/document-converter-sidecar/.venv/bin/python mvn -f backend/pom.xml test`，日志 `/tmp/case-provenance-full.log`，34分56秒，10537项、5失败、0错误、17跳过（10515通过），退出1。
- 5个失败分别是FormDefinition匿名403/401、CompetitionIntel创建200/403、CaCommitmentLetter匿名403/401、TenderPermission匿名更新和转移403/401。相关测试修订保留在原测试修复任务，本独立架构任务不纳入。全量未通过。
- 本轮完整运行中，回填服务13、边界3、命令行3、真实MySQL适配器4、标书装载6、案例架构4均通过；通用ArchitectureTest共32项（外层30及嵌套2）通过。
- 独立验证副本 `/tmp/xiyu-case-provenance-mutation-20261009` 故意漏写页码后，4项中1失败（期望3、实际9），退出1；恢复与修复代码逐字节一致后4项全部通过，退出0。日志 `/tmp/case-provenance-mutation.log`、`/tmp/case-provenance-mutation-restored.log`。原工作区代码未参与变异。
- `mvn -f backend/pom.xml jacoco:report` 成功；XML可解析、HTML存在。适配器行覆盖23/24、分支7/8；应用服务行覆盖83/90、分支49/56。原始报告位于 `backend/target/site/jacoco/index.html`；日志 `/tmp/case-provenance-coverage.log`。
- 默认java-quality命令跳过Checkstyle/PMD，不能当作实际检查通过；另以quality.skip=false、明确三生产文件范围运行实际Checkstyle及PMD：退出0、无违规，日志 `/tmp/case-provenance-quality-scoped.log`。Checkstyle XML包含全部三个生产文件，PMD XML无违规。
- 行数、文档治理、关键词同步、文件锁、路由E2E契约检查通过；全仓Wiki健康检查有历史存量问题，本次相关页已回填。


## 阻塞闭合后的验证（主线86772edc9候选）

- 主线合并e506b1e66，知识§420/421/422保留且引用同步；merge-tree退出0。
- fced375dd通过cherry-pick -x dd39315e恢复四个测试修订；恢复时与原提交四文件逐字节一致。其后竞品创建测试另补数据库新增及保存名称断言。
- 补充数据库断言前，受影响集合129项全部通过、0失败0错误0跳过；日志 `/tmp/case-provenance-blockers-affected.log`，退出0，6分33秒，JaCoCo报告正常生成。
- 补充数据库断言后，竞品集成13项、0失败0错误0跳过；日志 `/tmp/case-provenance-blockers-persistence.log`，退出0。
- 完整运行含补充断言：10538项、0失败0错误17跳过（10521通过），35分02秒，退出0；日志 `/tmp/case-provenance-blockers-full.log`。Surefire退出等待30秒诊断及关闭阶段连接诊断保留，Maven最终BUILD SUCCESS。
- JaCoCo XML可解析且HTML存在：适配器23/24行、7/8分支；应用服务83/90行、49/56分支。
- 前端build:api退出0，lint退出0、0错误231警告；日志 `/tmp/case-provenance-blockers-build.log`、`/tmp/case-provenance-blockers-lint.log`。
- 结束后origin/main前移至067e74cdc（PR2810评分小计修复），产生新的知识文档及Wiki日志冲突；上述完整结果仅对应同步前候选，合并后另行验证。


## 最新主线合并候选59ff963e8验证

- origin/main为067e74cdc，合并树退出0、无冲突；主线知识§421保留，任务架构§422及测试契约§423引用同步，双方Wiki日志保留。
- 完整运行：10553项、7失败、0错误、17跳过，28分33秒，退出1；日志 `/tmp/case-provenance-blockers-final-full.log`。
- 原5个权限契约失败均关闭：表单21、竞品13、CA上传5、标讯权限25项均通过；案例适配器真实MySQL4项及案例架构4项通过，通用架构32项通过。
- 新7失败来自主线PR2810：PdfEvidencePipelineTest 2、StructuredTableExtractionMarkCompletedTest 2、ScoreParseAppServiceTest 1、ScorePartialOverwritePersistenceTest 2。独立审查确认ScoreSubtotalColumnPolicy拒绝mapper已识别的内嵌分值列，合法合计满分失去验证，进而产生CONFLICT/REVIEW_REQUIRED及发布未执行。不是旧测试期望应直接放宽。
- 完整失败后另跑jacoco:report：退出0、3147类，XML可解析、HTML存在；适配器23/24行及7/8分支，服务83/90行及49/56分支。日志 `/tmp/case-provenance-blockers-final-coverage.log`。报告正常不代表全量通过。
- 原两项阻塞已修复，新主线生产回归阻断交付。用户待决定另开评分解析修复任务或纳入本PR；未创建PR、未绕过审查门禁、未修改这七项测试期望。


## 评分小计回归修复（待全量验证）

- RED：`mvn -f backend/pom.xml test -Dtest=Project1359ScoringContractTest#uniqueExplicitMaximumUnderUnknownHeaderIdentifiesSubtotalColumn` 退出1；唯一满分100列产生`TABLE_SUBTOTAL_COLUMN_UNRESOLVED`。日志 `/tmp/score-subtotal-unique-maximum-red.log`。
- GREEN：同一行为用例改前置策略后，Project1359ScoringContractTest、PdfEvidencePipelineTest、StructuredTableExtractionMarkCompletedTest、ScoreParseAppServiceTest、ScorePartialOverwritePersistenceTest共68项通过，0失败、0错误、0跳过；JaCoCo正常。日志 `/tmp/score-subtotal-focused-green.log`。
- 实现仅在没有可识别最高/得分表头时，用唯一满分来源列作回退；若显式总分列集合不等于唯一满分列仍保留未决。全部7个此前整仓失败已在68项定向测试通过；另有完整全量结果如下。


## 评分小计回归修复后的最终候选验证（待PR门禁）

- 最新完整运行：`SIDECAR_PYTHON=/Users/user/xiyu/worktrees/trae/document-converter-sidecar/.venv/bin/python mvn -f backend/pom.xml test`，10554项、0失败、0错误、17跳过（10537通过），36分27秒，Maven `BUILD SUCCESS`。JaCoCo自动报告生成，XML可解析，3147类；日志 `/tmp/case-provenance-fixed-main-full.log`。Surefire在测试已全部结束后有30秒退出等待诊断，保留该日志；最终进程退出0、构建成功。
- 四类原权限失败全通过：表单21、竞品13、CA5、标讯权限25。回填适配器MySQL4、案例架构4、通用架构32全部通过。
- 定向评分回归：68项、0失败0错误0跳过；新增唯一合计满分 RED 及 GREEN 证据分别见 `/tmp/score-subtotal-unique-maximum-red.log`、`/tmp/score-subtotal-focused-green.log`。
- Checkstyle与PMD按四个变更生产文件启用真实检查（`quality.skip=false`）：退出0、BUILD SUCCESS，日志 `/tmp/case-provenance-score-subtotal-quality.log`。
- 前端文档治理115目录通过。JaCoCo关键行/分支：ScoreSubtotalColumnPolicy 27/31与37/52；OriginalScoringContext 190/190与217/262；CaseProvenanceBackfillAdapter 23/24与7/8；CaseProvenanceBackfillAppService 83/90与49/56。
- 源码和测试独立复审无Critical/Major/Minor。PR推送门禁待提交后运行。
