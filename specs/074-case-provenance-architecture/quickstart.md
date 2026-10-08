# 验证

mvn -f backend/pom.xml test -Dtest=BidCaseSliceArchitectureTest,CaseProvenanceBackfillAppServiceTest,CaseProvenanceBackfillCommandLineRunnerTest,CaseProvenanceBackfillAdapterMysqlTest,BidDocumentLoaderTest

真实MySQL使用AbstractMysqlIntegrationTest约定的127.0.0.1:13306测试容器；全量设置SIDECAR_PYTHON为已有可用虚拟环境解释器，串行运行mvn test并生成JaCoCo报告。不得把受影响子集当成全量通过。


## 最终验证证据（2026-10-09）

- 基线架构复现：20项、1失败、0错误、0跳过，32处跨层依赖；日志 `/tmp/case-provenance-architecture-baseline.log`，退出1。
- 首轮受影响回归：64项、0失败、0错误、0跳过，日志 `/tmp/case-provenance-green.log`，退出0。
- 最终完整运行：`SIDECAR_PYTHON=/Users/user/xiyu/worktrees/trae/document-converter-sidecar/.venv/bin/python mvn -f backend/pom.xml test`，日志 `/tmp/case-provenance-full.log`，34分56秒，10537项、5失败、0错误、17跳过（10515通过），退出1。
- 5个失败分别是FormDefinition匿名403/401、CompetitionIntel创建200/403、CaCommitmentLetter匿名403/401、TenderPermission匿名更新和转移403/401。相关测试修订保留在原测试修复任务，本独立架构任务不纳入。全量未通过。
- 本轮完整运行中，回填服务13、边界3、命令行3、真实MySQL适配器4、标书装载6、案例架构4均通过；通用ArchitectureTest共32项（外层30及嵌套2）通过。
- 独立验证副本 `/tmp/xiyu-case-provenance-mutation-20261009` 故意漏写页码后，4项中1失败（期望3、实际9），退出1；恢复与修复代码逐字节一致后4项全部通过，退出0。日志 `/tmp/case-provenance-mutation.log`、`/tmp/case-provenance-mutation-restored.log`。原工作区代码未参与变异。
- `mvn -f backend/pom.xml jacoco:report` 成功；XML可解析、HTML存在。适配器行覆盖23/24、分支7/8；应用服务行覆盖83/90、分支49/56。原始报告位于 `backend/target/site/jacoco/index.html`；日志 `/tmp/case-provenance-coverage.log`。
- 默认java-quality命令跳过Checkstyle/PMD，不能当作实际检查通过；另以quality.skip=false、明确三生产文件范围运行实际Checkstyle及PMD：退出0、无违规，日志 `/tmp/case-provenance-quality-scoped.log`。Checkstyle XML包含全部三个生产文件，PMD XML无违规。
- 行数、文档治理、关键词同步、文件锁、路由E2E契约检查通过；全仓Wiki健康检查有历史存量问题，本次相关页已回填。
