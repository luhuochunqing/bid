# Feature Specification: 评分解析守卫语料完整性 + 运行遥测持久化 + 金标回放基线 (057)

**Feature Branch**: `agent/gemini/score-eval-guards`

**Created**: 2026-08-27

**Status**: Draft

**Input**: 工程评审（plan-eng-review 2026-08-27）四项拍板决策的落地首批——T3 裁决语料治理 / T2 run-summary 持久化 / T1 金标评测 v0。

## 业务背景与问题定义

工程评审证实两类结构性缺陷正在污染"AI 评分标准解析"质量数据：

1. **裁决语料失真（P1）**：`ScoreGlobalMergeOrchestrator.java:90` 定位失败时降级为全文前缀 12000 字符做 LLM 归并裁决；`ScoreMergeResultGuard` 规则 1 要求输出 detail ⊆ 该语料，否则计入 hallucinationDropped。后果：(a) 真实评分项因超出语料被当幻觉静默丢弃；(b) 幻觉计数系统性失真——后续任何看板/告警建立在脏指标上。
2. **运行信号不可追溯（P1）**：对齐率（aligned=N/M）、各 lane 候选数、归并守卫计数（pool/accepted/hallucinationDropped/restored/poolBacked）全部散落 `log.info`，任务结束后无法回答"这次解析质量如何、坏在哪一层"。§7 的 qwen3.7-max 空返回事故排查耗时一天的根因就是无聚合遥测。
3. **回归无数值护栏（P1）**：仓库内无任何真实形态招标文本的金标用例，每次修复是否收敛只能靠测试环境人工引爆。

## 目标 / 非目标

**目标**：定位失败不再错误裁决；截断场景真实项不被误杀且计数诚实；单次解析产出可持久化的结构化 run-summary；建立纯确定性管线的金标夹具回放（CI 默认跑）+ nightly 全链路模板。

**非目标**：大屏看板 UI；扫描件 OCR；存量项目批量重解析；lane 主干切换决策（等金标产出贡献度数据后另行 spec）；pre-push hook 结构改动（见 Waiver）。

## 用户故事

### US1: 裁决只信对的语料 (P1)
**作为** 解析管线维护者，
**我希望** 评分章节定位失败时跳过 LLM 归并（保持启发式结果），截断时被池背书的真实项存活，
**以便于** hallucinationDropped 只统计真幻觉。

FR：
- **FR-01**: 定位失败 MUST NOT 以全文前缀作为裁决语料——直接 fail-open 跳过归并并记录原因。
- **FR-02**: 守卫对"与候选池身份匹配（归一化互相包含）"的归并输出项 MUST 放行并计 poolBackedKept。该旁路**常态启用**，语料截断（定位成功但超 SECTION_TEXT_LIMIT）是它最需要生效的场景；非截断时能命中者必源自真实候选池的措辞漂移复述（池由同段语料召回），放行不引入新幻觉面。口径收敛依据：docs/reviews/057-design-detour-review.md P4。
- **FR-03**: Outcome 必须携带 poolBackedKept 计数；编排日志同步透出 truncated 标记。

### US2: 单次解析一份运行档案 (P1)
**作为** 平台运维，
**我希望** score_parse_task 持久化 runSummary（provider/model/laneCandidates/aligned 比率/guard 各计数）并在 items meta 透出，
**以便于** 出问题时按 task 追溯与分层定位。

FR：
- **FR-04**: 新增列 `score_parse_task.run_summary`（TEXT NULL），由解析完成路径写入；Meta 透出同名可选字段（前端零改动兼容）。
- **FR-05**: 召回四向 Outcome 上报 lane 贡献数与对齐命中数；召回五行数由接入点上报。
- **FR-06**: 对齐率 <50%（有尝试）或 guardDrop 比例异常时输出 WARN（阈值常量集中在一处可测）。
- **FR-07**: `ScoreParseAppService` 保持 ≤300 行——按 implementation-notes D5 预案把后处理块抽为 `ScoreCandidatePostProcessor`。

### US3: 金标夹具回放基线 (P1)
**作为** 修复作者，
**我希望** 每个 PR 的后端默认测试套件自动对合成金标夹具输出 P/R/F1 与 lane 贡献报告，
**以便于** 回归立即可见、为主干收敛攒数据。

FR：
- **FR-08**: 夹具 = 彻底脱敏合成 markdown + expected items JSON（含 focus/laneExpectation），入 git（存 synthetic 头注释声明来源）；真实客户文件禁止入库（GitHub 镜像面）。
- **FR-09**: `GoldenCasesReplayTest` 仅驱动确定性层（Locator/Extractor/TableLane/Merge/Ghost/Normalize），LLM 层不在本测试范围；报告写 `target/scoreparse-golden-report.json`。
- **FR-10**: scripts/eval-scoreparse/ 提供标注规范 README（匹配谓词定义）+ 报告渲染脚本 + nightly 全链路模板脚本（缺 XIYU_EVAL_CORPUS_DIR 明确 SKIP）。

## 验收标准

- **SC-1**: 定位失败文档：归并不触发 LLM、结果 = 启发式清单、日志含跳过原因（单测证明，mock executor 断言未调用）。
- **SC-2**: 截断文档 + 池背书项：accepted 含该项、poolBackedKept≥1、hallucinationDropped 不含它。
- **SC-3**: COMPLETED 任务落库行含合法 runSummary JSON，items meta 透出同值。
- **SC-4**: GoldenCasesReplayTest 在默认 `mvn test -Dtest='GoldenCasesReplayTest'` 下全绿并产出报告文件。
- **SC-5**: `mvn -pl backend test`（相关包定向）+ ArchitectureTest 全绿；AppService 行数 ≤300。

## Waiver（显式豁免）

speckit-specify/plan/tasks 技能以本目录三工件直产替代（自主会话交互豁免）；pre-push hook 未新增步骤——夹具回放已并入默认后端测试套件等效满足第一层门禁。
