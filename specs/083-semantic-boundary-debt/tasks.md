# Tasks: Semantic boundary 技术债

## Setup
- [x] T001 完成 specs/083-semantic-boundary-debt/spec.md、plan.md、research.md 与基线启动。

## US1 契约与语料分离
- [x] T002 [US1] 按 docs/exec-plans/active/semantic-boundary-debt-inventory.md 拆分 Quality1360UnblockTest，逐输入对账且全量旧断言通过。
- [x] T003 [US1] backend/pom.xml 配置默认excludedGroups，验证三类契约默认执行/语料显式执行。

## US2 纯结构等价
- [x] T004 [US2] SemanticEvidenceBoundary.java 与 AuthorizationFieldPolicy.java 命名片段、120字符及300行治理，完整迁移测试等价。

## US3 能力收口
- [x] T005 [US3] CertificateEvidenceScopeContractTest.java 明确对象/同页裸续句/跨页裸续句/其他对象最小契约RED。
- [x] T006 [US3] CertificateEvidenceContextPolicy.java 撤回跨句指代推断，未知人工且直接缺陷/四维度主路径保持GREEN。

## Verification
- [x] T007 后端compliance域与架构、npm构建、line budget及diff检查，保存 specs/083-semantic-boundary-debt/analysis.md。
- [ ] T008 scripts/review-workflow.py 完整三技能门禁，最多3轮、保留!2994状态。
- [x] T009 specs/083-semantic-boundary-debt/analysis.md 一致性、证据、知识沉淀与.wiki回填；只交付本地实现。

依赖T001→T002→T003→T004→T005→T006→T007→T008→T009。共享同一测试与policy修改，不并行写入。
