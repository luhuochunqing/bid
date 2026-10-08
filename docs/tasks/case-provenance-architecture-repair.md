# 案例来源回填架构修复任务

- 状态：设计已批准，实施与完整验证已完成，独立源码审查通过；整仓基线5个断言失败使交付结论为ESCALATE；Spec Kit 规格位于 specs/074-case-provenance-architecture。
- 分支：`agent/gemini/case-provenance-architecture`
- 基线：`501b5e16007165ef583592bec6f6e9aebbbfc7e3`
- 来源：PR #2809 的后端全量验证；用户于 2026-10-08 决定另开任务，保持评分提示 PR 范围。

## 已确认问题

`casework.application.service.CaseProvenanceBackfillAppService` 直接引用 `casework.infrastructure.KnowledgeCase` 与 `KnowledgeCaseRepository`。`BidCaseSliceArchitectureTest.applicationDoesNotDependOnInfrastructure` 报告 32 次依赖违规，整仓全量因此存在实际架构失败。

复现命令：

```bash
mvn -f backend/pom.xml test -Dtest=BidCaseSliceArchitectureTest
```

发现日志：`/tmp/pr2809-repair-full.log`；全量运行完整结束，10531 tests、87 failures、29 errors、17 skipped。其他测试契约及环境问题在 PR #2809 任务区修复后的受影响子集已关闭，本任务仅处理该独立架构违规。

## 范围与约束

- 目标：解除应用层对本模块 infrastructure 的直接依赖，保留案例来源回填行为。
- 涉及：`CaseProvenanceBackfillAppService`、必要的应用端口/持久化适配器及对应测试；启动命令调用契约需保留。
- 在编码前完成仓库规定的 Spec Kit specify → plan → tasks，并核对 `specs/028-ai-case-slice-semantic-search/plan.md` 的原有分层约定。
- 不添加架构白名单，不放宽 ArchUnit 规则，不把回填业务判定迁入持久化层来隐藏违规。
- 保留 ACTIVE 案例范围、按项目分组、可验证章节写回、定位失败清空、标书不可用整批跳过不写库，以及重复执行幂等性。
- 不修改评分解析功能开关、权限策略或数据库迁移。

## 验收要求

1. `BidCaseSliceArchitectureTest` 全绿，原有架构约束保持不变。
2. 案例回填、标书装载与命令行入口回归通过；端口转换逐字段证明来源信息没有丢失，未知/不可用来源不得误清空。
3. 对最终候选串行执行后端全量测试，使用独立测试 MySQL 与可用的 `SIDECAR_PYTHON`；保持 JaCoCo 启用并正常生成报告。
4. 三透镜独立复审通过后再交付；合并与部署另按授权执行。

## 修复设计（2026-10-09 已批准）

推荐新增专用应用端口 `CaseProvenanceBackfillPort` 与 `CaseProvenanceBackfillAdapter`。端口返回不可变候选（案例ID、项目ID、正文、评分项标题和五个来源字段），接收案例ID及完整五字段更新。应用服务继续负责章节过滤、分组、定位、清空与统计；适配器负责实体映射及按项目批量保存，只修改来源章节标题、路径、索引、文档名、页码。项目名称查找与命令行入口契约保留。

候选仍严格使用 `findByStatus("ACTIVE")`。现有 `KnowledgeCaseQueryPort` 把空状态也当作ACTIVE，扩展它会混合不同契约，因此不采用。将服务移入infrastructure会把业务判断迁入持久化层，也不采用。

写入事务覆盖适配器实体读取与保存；案例不存在则失败并回滚，禁止重新创建或静默丢弃更新，写入异常继续传播。标书不可用或解析失败整项目不写入；定位失败只清空原有来源；孤立案例计入未变化。保留成功定位更新计数与重复执行字段一致性，不增加并发版本策略或统计优化。

### 实施与验收

1. 已按批准设计完成Spec Kit规格、计划、任务并实施；完整验证记录见 specs/074-case-provenance-architecture/quickstart.md。
2. 服务测试改用端口候选和更新捕获，覆盖五字段写回、页码回退、清空、无变化、孤立案例、标书不可用、异常及统计。
3. 适配器验证严格ACTIVE范围、五字段映射、其他实体字段保留、批量保存和缺失案例失败；真实数据库验证持久化行为。
4. 架构规则保持不变，回填和命令行测试必须通过。最终串行执行后端全量测试，启用JaCoCo并生成可读取报告，再独立复审。

本次基线复现：`mvn -f backend/pom.xml test -Dtest='BidCaseSliceArchitectureTest,CaseProvenanceBackfillAppServiceTest,CaseProvenanceBackfillCommandLineRunnerTest'`；退出码1，20项、1失败、0错误、0跳过。日志 `/tmp/case-provenance-architecture-baseline.log`。已完成端口与适配器实现。
