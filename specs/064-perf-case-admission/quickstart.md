# Quickstart: 业绩评分项案例推荐精准准入 — 验证指南

**Feature**: specs/064-perf-case-admission | **Branch**: `agent/gemini/fix-perf-case-admission`

## 前置条件

- JDK 21、Maven（使用共享 `~/.m2` 缓存，临时 worktree 无需安装前端依赖）
- 工作目录：`backend/`
- 本次为纯后端改动，前端不参与验证（`[skip e2e-scope]` 豁免：蓝图 E2E 规则允许纯后端改动豁免）

## 1. RED 基线（改前复现，实现前执行）

```bash
cd backend
# 1) 混合类别被误判为资质（预期当前失败/断言不成立）
mvn test -Dtest=ScoreItemCaseMatchPolicyTest
# 2) 多条款只取第一条、无结构化门槛（预期当前失败/断言不成立）
mvn test -Dtest=QualificationPerformanceRetrieverTest
# 3) 人员案例在业绩评分项下被放行（新增集成用例，预期当前失败）
mvn test -Dtest=ScoreItemCaseRecommendAppServiceTest
```

RED 证据以“新增用例先失败”为准；若存量用例本身已绿，则以新增断言用例的失败输出作为基线证据。

## 2. GREEN 定向验证（实现后执行）

```bash
cd backend
mvn test -Dtest=PerformanceThresholdExtractorTest,PerformanceCaseAdmissionPolicyTest,QualificationPerformanceRetrieverTest,ScoreItemCaseMatchPolicyTest,ScoreItemCaseRecommendAppServiceTest
```

预期：全部通过；关键断言：
- 双门槛（2000 万/4500 万）均参与判定；
- 人员/团队类案例在业绩评分项下不出现；
- 明确低于门槛的案例被排除，无金额证据但品类命中的案例保留（降级）；
- `资质业绩` → PERFORMANCE，纯资质文本 → QUALIFICATION。

## 3. 门禁与回归

```bash
cd backend
mvn test -Dtest=FPJavaArchitectureTest,ArchitectureTest,ProjectAccessGuardCoverageTest
mvn test -Dtest='com.xiyu.bid.casework.**'
cd ..
node scripts/check-line-budgets.mjs   # 或 npm run check:line-budgets（若本机 node_modules 可用）
```

预期：架构门禁全绿；casework 模块回归全绿；无文件超出 300 行棘轮。

## 4. 端到端人工验证（开发宿主 trae，可选）

前置：开发宿主 `~/xiyu/worktrees/trae` 启动后端 18089 / 前端 1323。
1. 打开项目 `智慧园区MRO年度采购项目`（本地库 id=10）的「AI 智能案例推荐」；
2. 选择评分项「类似业绩」（score_item id=4）；
3. 观察结果列表：工程文案类/人员类案例不再出现；如项目资格项快照缺省，则仅保留业绩信号案例；
4. 若导入含 2000 万/4500 万条款的资格项快照，验证金额达门槛案例优先展示。

## 5. 证据落点

- S5 验证记录：`.omc/verify-<sessionID>.md`（含退出码、RED/GREEN 输出摘要）
- S6 审查记录：`.omc/review-<sessionID>.md`（verdict 必须为 MERGE_READY 方可进入交付）
