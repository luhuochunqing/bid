# Spec Kit 一致性分析

Date: 2026-11-11; baseline:57164cb98; scope:spec.md/plan.md/tasks.md和当前diff。

| 需求 | 任务 | 执行断言 |
| --- | --- | --- |
| FR-001 汇总不是事实 | T002/T003 | StandaloneMaximumSummaryTest.standaloneMaximumDoesNotBecomeAnAdditionalHundredPointFact |
| FR-002 总分同源定位/合并格 | T002/T003/T005 | sourceTotalSurvivesASecondPhysicalQualificationTable、mergedMaximumLabelAndValueRemainSourceTotalOnly、golden fixture |
| FR-003 正常单项/表头兼容 | T004/T007/T010 | ScoreTableSummaryPolicyTest.standaloneMaximumLabelDoesNotMatchItemMaximumLanguage、maximumWeightColumnHeaderIsNotASummaryRow、headerlessSummaryRemainsSourceTotalBesideDetailTable、相邻OriginalScoring/ScoreSubtotal测试 |
| FR-004 明细只计一次 | T005/T006 | GoldenCasesReplayTest的新增case、前端useScoreCandidateAdoption.spec.js |
| FR-005 交付边界 | T009 | git diff只有纯核心、测试、规格/知识文件；无生产写入、无迁移 |

- 无CRITICAL/HIGH规格矛盾、无未映射需求、无未经许可schema/API/权限变化。
- 纯核心ScoreTableSummaryPolicy与OriginalScoringContext；外壳不改；constitution未改。
- 非生产本地working_tree交付；上线/生产重解析及旧108分未纳入成功判据。
- adapter兼容：本仓check-prerequisites.sh不支持新skill文档的--require-spec，使用原生--require-tasks --include-tasks并独立核对spec存在；SPECIFY_FEATURE=085-standalone-maximum-total适配项目agent分支命名，输出FEATURE_DIR正确。未修改治理脚本。
- health/codehealth工具未提供；entry gate记录替代静态审查而非认证。acceptance-evidence-gate要求clean committed HEAD，本任务授权仅working_tree，因此不伪造receipt；以当前diff、命令日志、独立审查证明本地行为。
- 第1轮独立审查发现满分权重表头污染context.problems；header-red-test.log证实；第2轮实现排除mapping.headerRows，重新执行受影响验证。
- 后续三技能审查发现整个mapping.headerRows排除范围会吞掉无表头独立汇总表。fix第1轮新增三组双表回归，修复前均因总分为空失败；有独立标签和数值证据的行改按无表头规则解析，无数值满分列表头仍排除。当前基线1545e85483b9adb163cf29bfcdc3dcdd6f309b89；本轮仅写入修复，复审待主执行器调度，不沿用历史通过结论。

## 后续交付授权（2026-11-11）
用户明确要求“提交PR”，交付上限由初始working_tree提升为pr：允许本地提交、推送origin任务分支并创建Gitee PR；不合并、不部署、不改生产数据。提交前同步最新origin/main（精确基线以当轮门禁凭证为准），重新执行三技能流程和实际测试。前述未提交/本地限定为前一阶段的历史状态。
